use oh_domain::{
    account::{Account, AccountId, AuthReply, CredentialRef, XtreamProfile},
    catalog::{Kind, ProviderKey, ProviderVod, VodType},
    discovery::{Browse, RecentSearches},
    metadata::{search_terms, Candidate, Details, MatchInputs, Presentation, Vod},
    pairing::Session,
    personal::{continue_watching, OnDemand, Progress},
};
use std::{hint::black_box, num::NonZeroU64, time::Instant};

fn provider(i: usize) -> ProviderVod {
    ProviderVod {
        key: ProviderKey {
            account: AccountId(if i % 2 == 0 { "a" } else { "b" }.into()),
            id: format!("{i:08}"),
        },
        media: VodType::Movie {
            extension: Some("mp4".into()),
            added: Some(i as i64),
        },
        category_id: Some((i % 20).to_string()),
        name: format!("Example movie {i:08} (2021)"),
        presentation: Presentation {
            title: Some(format!("Example movie {i:08}")),
            overview: Some("Provider description ".repeat(20)),
            date: Some("2021-01-01".into()),
            poster: Some("https://example.invalid/poster.jpg".into()),
            rating: Some(7.0),
            genres: vec!["Drama".into(), "Action".into()],
            ..Default::default()
        },
        tmdb_hint: Some(1),
        year: Some(2021),
    }
}

fn candidate(i: usize) -> Candidate {
    Candidate {
        kind: Kind::Movie,
        id: NonZeroU64::new(1).unwrap(),
        title: format!("Example movie {i:08}"),
        original_title: String::new(),
        year: Some(2021),
    }
}

fn details() -> Details {
    Details {
        kind: Kind::Movie,
        id: NonZeroU64::new(1).unwrap(),
        imdb_id: None,
        presentation: Presentation {
            title: Some("Localized title".into()),
            overview: Some("Metadata overview ".repeat(20)),
            poster: Some("https://example.invalid/tmdb.jpg".into()),
            rating: Some(8.0),
            genres: vec!["Drama".into()],
            ..Default::default()
        },
    }
}

#[test]
fn generated_title_and_pairing_inputs() {
    // Deterministic generated-input fuzz-style smoke test, not libFuzzer.
    let chars = ['A', 'z', ' ', '-', '(', ')', '[', ']', '4', 'K', 'é', 'ع', 'ß', '\0', '🙂'];
    let mut state = 0x1234_5678_9abc_def0_u64;
    for i in 0..20_000 {
        let mut text = String::new();
        for _ in 0..(i % 65) {
            state = state.wrapping_mul(6364136223846793005).wrapping_add(1);
            text.push(chars[(state >> 32) as usize % chars.len()]);
        }
        let (title, year) = search_terms(&text, Some(2024));
        assert_eq!(year, Some(2024));
        assert!(text.contains(title));
        assert!(title.len() <= text.len());
        if let Some(session) = Session::new("test".into(), &text, 1) {
            assert!(session.accept(&text, 1).is_ok());
            assert!(session.accept(&text, 1).is_err());
        }
    }
}

#[test]
fn generated_progress_properties() {
    let key = OnDemand::Movie(provider(0).key);
    let mut seed = 42_u64;
    for _ in 0..100_000 {
        seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1);
        let duration = (seed >> 1).max(1) as i64;
        seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1);
        let position = seed as i64;
        let p = Progress::record(key.clone(), position, duration, 0).unwrap();
        let cutoff = duration - duration / 10;
        assert_eq!(p.completed(), position.max(0) >= cutoff);
        assert_eq!(p.duration_ms(), duration);
        assert_eq!(p.position_ms(), position.max(0));
        if p.resume_ms() != 0 {
            assert!(p.resume_ms() >= 5000);
            assert!(p.resume_ms() < cutoff);
        }
    }
}

#[test]
fn accepted_auth_policy_and_account_accessors() {
    let profile = XtreamProfile {
        id: AccountId("a".into()),
        display_name: "A".into(),
        provider_base_url: "https://example.invalid".into(),
        credentials: CredentialRef("opaque-handle".into()),
    };
    let account = Account::accept(
        profile,
        Ok(AuthReply {
            authenticated: true,
            status: "Active".into(),
            expires_epoch_seconds: Some(1),
        }),
        2,
    )
    .unwrap();
    // Documents approved auth+Active rule; expiry-conflict policy is unresolved.
    assert_eq!(account.expiry(), Some(1));
    assert_eq!(account.profile().display_name, "A");
    assert_eq!(account.authenticated_at(), 2);
}

#[test]
fn metadata_scope_and_stale_match_inputs() {
    let original = provider(0);
    let inputs = MatchInputs::from(&original);
    let mut vod = Vod::new(original.clone());
    assert!(vod.attach(inputs.supplied(&candidate(0)).unwrap(), details()));
    let other = provider(1);
    assert!(!vod.refresh(other.clone()));
    assert!(!vod.attach(
        MatchInputs::from(&other).supplied(&candidate(1)).unwrap(),
        details()
    ));
    assert_eq!(vod.provider(), &original);
    let mut changed = original;
    changed.name = "Different title".into();
    assert!(vod.refresh(changed));
    assert!(vod.enrichment().is_none());
    assert!(!vod.attach(inputs.supplied(&candidate(0)).unwrap(), details()));
}

#[test]
fn recent_searches_instance_isolation() {
    let mut a = RecentSearches::default();
    let mut b = RecentSearches::default();
    a.record("private A", true);
    b.record("private B", true);
    assert_eq!(a.entries(), ["private A"]);
    assert_eq!(b.entries(), ["private B"]);
}

#[test]
#[ignore = "manual release benchmark"]
fn metadata_and_search_comparison() {
    for n in [1_000, 10_000, 50_000, 100_000] {
        let rows: Vec<_> = (0..n).map(provider).collect();
        let candidates: Vec<_> = (0..n).map(candidate).collect();
        let filter = Browse {
            account: AccountId("a".into()),
            kind: Kind::Movie,
            category: None,
        };
        let progress: Vec<_> = rows.iter().enumerate().map(|(i, r)| {
            Progress::record(OnDemand::Movie(r.key.clone()), 6000, 100000, i as i64).unwrap()
        }).collect();
        let query = "movie";
        let folded = query.to_ascii_lowercase();
        let alternative = |r: &&ProviderVod| {
            r.key.account == filter.account
                && matches!(r.media, VodType::Movie { .. })
                && r.name.to_ascii_lowercase().contains(&folded)
        };
        for iteration in 0..8 {
            let t = Instant::now();
            let original = rows.iter().filter(|r| filter.matches(r, query)).count();
            let search_ns = t.elapsed().as_nanos();
            let t = Instant::now();
            let alternate = rows.iter().filter(alternative).count();
            let alternate_ns = t.elapsed().as_nanos();
            assert_eq!(original, alternate);
            let t = Instant::now();
            black_box(continue_watching(&progress, &filter.account));
            let recent_ns = t.elapsed().as_nanos();
            let t = Instant::now();
            for (r, c) in rows.iter().zip(&candidates) {
                black_box(MatchInputs::from(r).supplied(c));
            }
            let match_ns = t.elapsed().as_nanos();
            let t = Instant::now();
            for (r, c) in rows.iter().zip(&candidates) {
                let mut vod = Vod::new(r.clone());
                let token = MatchInputs::from(r).supplied(c).unwrap();
                assert!(vod.attach(token, details()));
                assert!(vod.refresh(r.clone()));
                black_box(vod.display());
            }
            let lifecycle_ns = t.elapsed().as_nanos();
            if iteration >= 3 {
                println!("extended n={n},search_ns={search_ns},alternate_ns={alternate_ns},recent_ns={recent_ns},match_ns={match_ns},lifecycle_ns={lifecycle_ns}");
            }
        }
    }
}
