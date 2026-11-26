use oh_domain::{
    account::{Account, AccountId, AuthError, AuthReply, CredentialRef, XtreamProfile},
    catalog::{Kind, ProviderKey, ProviderVod, VodType},
    discovery::RecentSearches,
    metadata::{Candidate, Details, MatchInputs, Presentation, Vod},
    sync::{Outcome, RefreshAction, Scope, State, Summary},
};
use std::num::NonZeroU64;

fn id(s: &str) -> AccountId { AccountId(s.into()) }
fn provider(kind: Kind) -> ProviderVod {
    ProviderVod {
        key: ProviderKey { account: id("a"), id: "001".into() },
        media: match kind {
            Kind::Movie => VodType::Movie { extension: Some("mp4".into()), added: Some(1) },
            Kind::Series => VodType::Series { modified: Some(1), details_available: true },
            Kind::Live => unreachable!(),
        },
        category_id: Some("provider-category".into()),
        name: "Film (2024)".into(),
        presentation: Presentation {
            title: Some("provider title".into()),
            overview: Some("provider overview".into()),
            date: Some("2024-01-01".into()),
            poster: Some("provider-poster".into()),
            backdrop: Some("provider-backdrop".into()),
            trailer: Some("provider-trailer".into()),
            runtime_minutes: Some(110),
            rating: Some(7.0),
            vote_count: Some(3),
            genres: vec!["provider genre".into()],
        },
        tmdb_hint: Some(42),
        year: Some(2024),
    }
}
fn candidate(kind: Kind) -> Candidate {
    Candidate { kind, id: NonZeroU64::new(42).unwrap(), title: "Film".into(), original_title: String::new(), year: Some(2024) }
}
fn details(kind: Kind) -> Details {
    Details { kind, id: NonZeroU64::new(42).unwrap(), imdb_id: None, presentation: Presentation::default() }
}
fn account(a: &AccountId, auth: bool, status: &str, expiry: Option<i64>) -> Result<Account, AuthError> {
    Account::accept(
        XtreamProfile { id: a.clone(), display_name: "account".into(), provider_base_url: "https://provider.invalid".into(), credentials: CredentialRef("opaque".into()) },
        Ok(AuthReply { authenticated: auth, status: status.into(), expires_epoch_seconds: expiry }), 100
    )
}

#[test]
fn calendar_exhaustive_boundary_and_independent_fields() {
    for kind in [Kind::Movie, Kind::Series] {
        let p = provider(kind);
        let mut v = Vod::new(p.clone());
        let c = candidate(kind);
        // Independent oracle: Gregorian year length and month-length table.
        for y in 1600..=2400 {
            let feb = if y % 400 == 0 || (y % 100 != 0 && y % 4 == 0) { 29 } else { 28 };
            for (m, days) in [31, feb, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31].into_iter().enumerate() {
                for d in [0, 1, days, days+1] {
                    let raw = format!("{y:04}-{:02}-{d:02}", m+1);
                    let mut data = details(kind);
                    data.presentation.date = Some(raw.clone());
                    data.presentation.title = Some("preferred".into());
                    data.presentation.runtime_minutes = Some(0);
                    data.presentation.rating = Some(f32::INFINITY);
                    data.presentation.genres = vec![" ".into(), "new".into()];
                    assert!(v.attach(v.match_inputs().supplied(&c).unwrap(), data));
                    let out = v.display();
                    assert_eq!(out.date.as_deref(), Some(if (1..=days).contains(&d) { raw.as_str() } else { "2024-01-01" }));
                    assert_eq!(out.title.as_deref(), Some("preferred"));
                    assert_eq!(out.runtime_minutes, Some(110));
                    assert_eq!(out.rating, Some(7.0));
                    assert_eq!(out.genres, ["new"]);
                    assert_eq!(v.enrichment().unwrap().details.presentation.date.as_deref(), Some(raw.as_str()));
                    assert_eq!(v.provider(), &p);
                }
            }
        }
        for (raw, valid) in [("0000-01-01", false), ("0001-01-01", true), ("9999-12-31", true), ("2024-00-01", false), ("2024-13-01", false), ("2024-1-01", false), ("2024/01/01", false), ("２０２４-01-01", false), ("2024-0x-01", false), ("2024", false), (" 2024-02-29 ", true), ("2024-02-29T00:00:00Z", false), ("2024-01-0\0", false)] {
            let mut data = details(kind);
            data.presentation.date = Some(raw.into());
            assert!(v.attach(v.match_inputs().supplied(&c).unwrap(), data));
            assert_eq!(v.display().date.as_deref(), Some(if valid { raw } else { "2024-01-01" }));
        }
        let mut data = details(kind);
        data.presentation = Presentation { title: Some(" ".into()), overview: Some("new overview".into()), date: None, poster: Some("relative.jpg".into()), backdrop: Some(" ".into()), trailer: Some("trailer-id".into()), runtime_minutes: Some(1), rating: Some(0.0), vote_count: Some(0), genres: vec![" ".into()] };
        assert!(v.attach(v.match_inputs().supplied(&c).unwrap(), data));
        let out = v.display();
        assert_eq!(out.title, p.presentation.title);
        assert_eq!(out.overview.as_deref(), Some("new overview"));
        assert_eq!(out.poster.as_deref(), Some("relative.jpg"));
        assert_eq!(out.backdrop, p.presentation.backdrop);
        assert_eq!(out.trailer.as_deref(), Some("trailer-id"));
        assert_eq!(out.runtime_minutes, Some(1));
        assert_eq!(out.rating, Some(0.0));
        assert_eq!(out.vote_count, Some(0));
        assert_eq!(out.genres, p.presentation.genres);
        assert_eq!(v.provider(), &p);
    }
}

#[test]
fn freshness_state_machine_and_object_recreation() {
    for kind in [Kind::Movie, Kind::Series] {
        let original = provider(kind);
        let c = candidate(kind);
        let mut v = Vod::new(original.clone());
        let mut old: Vec<MatchInputs> = Vec::new();
        let mut seed = 7_u64;
        for step in 0..2048 {
            let current = v.match_inputs();
            assert!(v.attach(current.supplied(&c).unwrap(), details(kind)));
            let mut irrelevant = original.clone();
            irrelevant.category_id = Some(step.to_string());
            irrelevant.presentation.overview = Some("irrelevant".into());
            assert!(v.refresh(irrelevant));
            assert!(v.enrichment().is_some());
            assert!(v.attach(current.supplied(&c).unwrap(), details(kind)));
            let mut changed = original.clone();
            seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1);
            match seed % 4 {
                0 => changed.name = "Other".into(),
                1 => changed.year = Some(2020),
                2 => changed.presentation.date = Some("2023-01-01".into()),
                _ => changed.tmdb_hint = Some(43),
            }
            old.push(current);
            assert!(v.refresh(changed));
            assert!(v.enrichment().is_none());
            assert!(v.refresh(original.clone()));
            for snapshot in old.iter().rev().take(8).chain(old.first()) {
                assert!(!v.attach(snapshot.supplied(&c).unwrap(), details(kind)));
            }
            assert!(v.enrichment().is_none());
            let fresh = v.match_inputs();
            assert!(v.attach(fresh.supplied(&c).unwrap(), details(kind)));
            let mut foreign = original.clone();
            foreign.key.account = id("b");
            assert!(!v.refresh(foreign.clone()));
            assert!(v.enrichment().is_some());
            let mut other = Vod::new(foreign);
            assert!(!other.attach(fresh.supplied(&c).unwrap(), details(kind)));
            let mut other = Vod::new(original.clone());
            assert!(!other.attach(fresh.supplied(&c).unwrap(), details(kind)));
            assert!(!other.attach(MatchInputs::from(&original).supplied(&c).unwrap(), details(kind)));
        }
        let token = v.match_inputs().supplied(&c).unwrap();
        drop(v);
        let mut recreated = Vod::new(original);
        assert!(!recreated.attach(token, details(kind)));
        assert!(recreated.attach(recreated.match_inputs().supplied(&c).unwrap(), details(kind)));
    }
}

#[test]
fn auth_authority_and_history_model() {
    for expiry in [None, Some(i64::MIN), Some(99), Some(100), Some(101), Some(i64::MAX)] {
        let a = id("a");
        assert_eq!(account(&a, true, "aCtIvE", expiry).unwrap().expiry(), expiry);
        assert!(matches!(account(&a, true, "Expired", expiry), Err(AuthError::Expired)));
        assert!(matches!(account(&a, false, "Active", expiry), Err(AuthError::InvalidCredentials)));
    }
    let ids: Vec<_> = (0..64).map(|i| id(&format!("account-{i}"))).collect();
    let mut h = RecentSearches::default();
    let mut model: Vec<Vec<String>> = vec![vec![]; ids.len()];
    let queries = ["one", "TWO", "three", " four ", "ONE", " ", "É", "é", "six"];
    let mut seed = 33_u64;
    for _ in 0..20_000 {
        seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1);
        let i = (seed >> 16) as usize % ids.len();
        let q = queries[(seed >> 32) as usize % queries.len()];
        let successful = seed & 3 != 0;
        if seed.is_multiple_of(17) {
            let owner = account(&ids[i], true, "Active", None).unwrap();
            h.remove_account(&owner.removal());
            h.remove_account(&owner.removal());
            model[i].clear();
        } else {
            h.record(&ids[i], q, successful);
            if successful && !q.trim().is_empty() {
                model[i] = std::iter::once(q.trim().to_owned())
                    .chain(model[i].iter().filter(|s| s.to_lowercase() != q.trim().to_lowercase()).cloned()).take(3).collect();
            }
        }
        for (a, expected) in ids.iter().zip(&model) { assert_eq!(h.entries(a), expected); }
        assert!(h.entries(&id("unknown")).is_empty());
    }
}

#[test]
fn every_attempt_outcome_with_distinct_prior_states() {
    let options = [Outcome::Success(0), Outcome::Success(17), Outcome::Failed, Outcome::Cancelled];
    let previous = [Scope {state: State::Ready, cached: 8, processed: 8}, Scope::default(), Scope {state: State::Empty, cached: 0, processed: 0}];
    let mut confirmations = 0;
    for a in options { for b in options { for c in options {
        let outcomes = [a,b,c];
        let mut s = Summary::default();
        let mut usable = false;
        for ((kind, outcome), prior) in [Kind::Live, Kind::Movie, Kind::Series].into_iter().zip(outcomes).zip(previous) {
            let mut refresh = prior.begin();
            refresh.processed = 3;
            let result = refresh.finish(outcome);
            let expected = match outcome {
                Outcome::Success(0) => (State::Empty,0,0),
                Outcome::Success(n) => (State::Ready,n,n),
                Outcome::Failed if prior.cached > 0 => (State::Stale,prior.cached,3),
                Outcome::Failed => (State::Failed,0,3),
                Outcome::Cancelled => (prior.state,prior.cached,prior.processed),
            };
            assert_eq!((result.state,result.cached,result.processed),expected);
            assert_eq!(outcome.catalog_action(),if matches!(outcome,Outcome::Success(_)) {RefreshAction::Replace} else {RefreshAction::Retain});
            *s.get_mut(kind) = result;
            usable |= matches!(expected.0,State::Ready|State::Stale);
        }
        assert_eq!(s.usable(),usable);
        if outcomes == [Outcome::Success(0);3] { confirmations += 1; assert!(!s.usable()); }
    }}}
    assert_eq!(confirmations,1);
}

#[test]
fn pairing_linearization_orders_and_competing_threads() {
    use oh_domain::pairing::{Rejection, Session};
    use std::sync::Barrier;
    // For these operation types, enumerate each possible ordering at the
    // only state-changing operation (the production compare_exchange).
    let operations = [("ABC", 0), ("ABC", 299999), ("wrong", 10), ("ABC", 300000)];
    for a in 0..4 { for b in 0..4 { for c in 0..4 { for d in 0..4 {
        let order = [a,b,c,d];
        let mut seen = [false;4];
        if !order.iter().all(|&i| {let fresh=!seen[i];seen[i]=true;fresh}) {continue;}
        let s = Session::new("id".into(), "ABC", 0).unwrap();
        let mut consumed=false;
        for i in order {
            let expected = match i {
                2 => Err(Rejection::WrongCode),
                3 => Err(Rejection::Expired),
                _ if consumed => Err(Rejection::Consumed),
                _ => {consumed=true;Ok(())},
            };
            assert_eq!(s.accept(operations[i].0,operations[i].1),expected);
        }
    }}}}
    for _ in 0..128 {
        let s=Session::new("id".into(),"ABC",0).unwrap();
        let barrier=Barrier::new(4);
        std::thread::scope(|scope| {
            let handles:Vec<_>=operations.iter().map(|&(code,now)| {
                let s=&s;let barrier=&barrier;
                scope.spawn(move || {barrier.wait();s.accept(code,now)})
            }).collect();
            let results:Vec<_>=handles.into_iter().map(|h|h.join().unwrap()).collect();
            assert_eq!(results[2],Err(Rejection::WrongCode));
            assert_eq!(results[3],Err(Rejection::Expired));
            assert_eq!(results[..2].iter().filter(|r|r.is_ok()).count(),1);
            assert_eq!(results[..2].iter().filter(|r|**r==Err(Rejection::Consumed)).count(),1);
        });
    }
}
#[test]
fn candidate_boundary_and_failed_attach_retains_enrichment() {
    let mut p=provider(Kind::Movie);
    p.year=None;
    p.presentation.date=Some("2024-01-01".into());
    let mut v=Vod::new(p);
    let mut c=candidate(Kind::Movie);
    let current=v.match_inputs();
    c.title="not a match".into();
    c.original_title="Film".into();
    assert!(current.supplied(&c).is_some());
    c.year=Some(2023);
    assert!(current.supplied(&c).is_none());
    c.year=Some(2024);
    assert!(current.fallback(1,&[]).is_none());
    assert!(current.fallback(0,std::slice::from_ref(&c)).is_none());
    assert!(v.attach(current.supplied(&c).unwrap(),details(Kind::Movie)));
    let mut bad=details(Kind::Series);
    bad.presentation.title=Some("foreign".into());
    assert!(!v.attach(current.supplied(&c).unwrap(),bad));
    assert!(v.enrichment().is_some());
    let mut bad_hint=c.clone();
    bad_hint.id=NonZeroU64::new(43).unwrap();
    assert!(current.supplied(&bad_hint).is_none());
}
