use oh_domain::{
    account::AccountId,
    catalog::{Kind, ProviderKey, ProviderVod, VodType},
    discovery::{Browse, Page, PageRequest},
    metadata::{Candidate, MatchInputs, Presentation},
    pairing::Session,
    personal::{continue_watching, Favorites, OnDemand, Progress},
    sync::{Outcome, RefreshAction, Scope, State, Summary},
};
use std::{
    hint::black_box,
    num::{NonZeroU64, NonZeroUsize},
    sync::Barrier,
    time::Instant,
};

fn row(account: &str, i: usize) -> ProviderVod {
    ProviderVod {
        key: ProviderKey {
            account: AccountId(account.into()),
            id: i.to_string(),
        },
        media: VodType::Movie {
            extension: Some("mp4".into()),
            added: Some(i as i64),
        },
        category_id: Some((i % 20).to_string()),
        name: format!("Provider movie {i:06} (2021)"),
        presentation: Presentation::default(),
        tmdb_hint: None,
        year: Some(2021),
    }
}

#[test]
fn module_graph_must_be_acyclic() {
    let catalog = include_str!("../src/catalog.rs");
    let metadata = include_str!("../src/metadata.rs");
    assert!(
        !(catalog.contains("metadata::") && metadata.contains("crate::catalog")),
        "catalog and metadata have a bidirectional source dependency"
    );
}

#[test]
fn all_current_outcomes_not_just_cached_states() {
    let prior_empty = Scope::default().begin().finish(Outcome::Success(0));
    for a in [Outcome::Success(0), Outcome::Success(1), Outcome::Failed, Outcome::Cancelled] {
        for b in [Outcome::Success(0), Outcome::Success(1), Outcome::Failed, Outcome::Cancelled] {
            for c in [Outcome::Success(0), Outcome::Success(1), Outcome::Failed, Outcome::Cancelled] {
                let outcomes = [a, b, c];
                let mut summary = Summary::default();
                for (kind, outcome) in [Kind::Live, Kind::Movie, Kind::Series].into_iter().zip(outcomes) {
                    *summary.get_mut(kind) = prior_empty.begin().finish(outcome);
                    assert_eq!(
                        outcome.catalog_action() == RefreshAction::Replace,
                        matches!(outcome, Outcome::Success(_))
                    );
                }
                let confirm = outcomes.iter().all(|x| *x == Outcome::Success(0));
                assert_eq!(confirm, a == Outcome::Success(0) && b == a && c == a);
                if confirm {
                    assert!(!summary.usable());
                    assert_eq!(summary.live.state, State::Empty);
                    assert_eq!(summary.movies.state, State::Empty);
                    assert_eq!(summary.shows.state, State::Empty);
                }
            }
        }
    }
    assert_eq!(prior_empty.begin().finish(Outcome::Cancelled), prior_empty);
}

#[test]
fn progress_integer_boundary_properties() {
    let key = OnDemand::Movie(row("a", 1).key);
    for duration in [1, 2, 9, 10, 4999, 5000, 10001, i64::MAX] {
        let threshold = ((i128::from(duration) * 9 + 9) / 10) as i64;
        for position in [-1, 0, threshold - 1, threshold, duration, i64::MAX] {
            let p = Progress::record(key.clone(), position, duration, 1).unwrap();
            assert_eq!(p.completed(), position.max(0) >= threshold);
            assert_eq!(p.position_ms(), position.max(0));
            let expected = if position < 5000 || position >= threshold {
                0
            } else {
                position
            };
            assert_eq!(p.resume_ms(), expected);
        }
    }
    for duration in [i64::MIN, -1, 0] {
        assert!(Progress::record(key.clone(), 1, duration, 1).is_none());
    }
}

#[test]
fn bounded_iterator_does_not_overconsume() {
    let request = PageRequest {
        offset: 999,
        limit: NonZeroUsize::new(7).unwrap(),
    };
    let mut consumed = 0;
    let rows = std::iter::from_fn(|| {
        consumed += 1;
        assert!(consumed <= 7);
        Some(consumed)
    });
    assert_eq!(Page::bounded(request, rows).rows(), &[1, 2, 3, 4, 5, 6, 7]);
    assert_eq!(consumed, 7);
}

#[test]
fn account_isolation_and_large_catalog() {
    let filter = Browse {
        account: AccountId("a".into()),
        kind: Kind::Movie,
        category: None,
    };
    let mut favorites = Favorites::default();
    let mut progress = Vec::new();
    for i in 0..100_000 {
        let account = if i % 2 == 0 { "a" } else { "b" };
        let r = row(account, i);
        assert_eq!(filter.matches(&r, " provider "), account == "a");
        favorites.add(r.identity(), i as i64);
        progress.push(Progress::record(OnDemand::Movie(r.key), 6000, 100000, i as i64).unwrap());
    }
    assert_eq!(favorites.newest(&filter.account).len(), 50_000);
    let result = continue_watching(&progress, &filter.account);
    assert_eq!(result.len(), 50_000);
    assert!(result.windows(2).all(|p| p[0].updated_at > p[1].updated_at));
    assert!(result.iter().all(|p| p.key().account() == &filter.account));
}

#[test]
fn matching_rejects_wrong_year_kind_and_ambiguity() {
    let p = row("a", 7);
    let inputs = MatchInputs::from(&p);
    let candidate = |kind, id, year| Candidate {
        kind,
        id: NonZeroU64::new(id).unwrap(),
        title: "Provider movie 000007".into(),
        original_title: String::new(),
        year,
    };
    assert!(inputs.fallback(1, &[candidate(Kind::Movie, 1, None)]).is_none());
    assert!(inputs.fallback(1, &[candidate(Kind::Series, 1, Some(2021))]).is_none());
    assert!(inputs.fallback(1, &[candidate(Kind::Movie, 1, Some(2021))]).is_some());
    assert!(inputs.fallback(1, &[candidate(Kind::Movie, 1, Some(2021)), candidate(Kind::Movie, 2, Some(2021))]).is_none());
}

#[test]
fn pairing_repeated_contention() {
    for i in 0..32 {
        let session = Session::new(i.to_string(), "X-Y", 10).unwrap();
        let barrier = Barrier::new(8);
        let winners = std::thread::scope(|scope| {
            let jobs: Vec<_> = (0..8)
                .map(|_| scope.spawn(|| {
                    barrier.wait();
                    session.accept("xy", 299_999).is_ok()
                }))
                .collect();
            jobs.into_iter().filter_map(|job| job.join().ok()).filter(|won| *won).count()
        });
        assert_eq!(winners, 1);
        assert!(session.accept("xy", 300_010).is_err());
    }
}

#[test]
#[ignore = "manual release-mode benchmark; timings are environment-specific"]
fn catalog_benchmark() {
    for n in [1_000, 10_000, 50_000, 100_000] {
        let rows: Vec<_> = (0..n).map(|i| row(if i % 2 == 0 { "a" } else { "b" }, i)).collect();
        let filter = Browse { account: AccountId("a".into()), kind: Kind::Movie, category: None };
        let progress: Vec<_> = rows.iter().enumerate().map(|(i, r)| Progress::record(OnDemand::Movie(r.key.clone()), 6000, 100000, i as i64).unwrap()).collect();
        let mut favorites = Favorites::default();
        for (i, r) in rows.iter().enumerate() {
            favorites.add(r.identity(), i as i64);
        }
        for _ in 0..3 {
            black_box(rows.iter().filter(|r| filter.matches(r, "movie")).count());
            black_box(continue_watching(&progress, &filter.account));
            black_box(favorites.newest(&filter.account));
        }
        for _ in 0..10 {
            let start = Instant::now();
            black_box(rows.iter().filter(|r| filter.matches(r, "movie")).count());
            let search = start.elapsed();
            let start = Instant::now();
            black_box(continue_watching(&progress, &filter.account));
            let recent = start.elapsed();
            let start = Instant::now();
            black_box(favorites.newest(&filter.account));
            let favorite = start.elapsed();
            println!("n={n},search_ns={},recent_ns={},favorite_ns={}", search.as_nanos(), recent.as_nanos(), favorite.as_nanos());
        }
    }
}
