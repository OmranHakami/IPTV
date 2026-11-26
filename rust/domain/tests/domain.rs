use oh_domain::{
    account::*, catalog::*, discovery::*, metadata::*, pairing::*, personal::*, sync::*,
};
use std::num::{NonZeroU64, NonZeroUsize};
fn account(s: &str) -> AccountId {
    AccountId(s.into())
}
fn key(a: &str, id: &str) -> ProviderKey {
    ProviderKey {
        account: account(a),
        id: id.into(),
    }
}
fn profile(a: &str) -> XtreamProfile {
    XtreamProfile {
        id: account(a),
        display_name: a.into(),
        provider_base_url: "https://provider.invalid".into(),
        credentials: CredentialRef(format!("handle-{a}")),
    }
}
fn reply(authenticated: bool, status: &str) -> Result<AuthReply, AuthError> {
    Ok(AuthReply {
        authenticated,
        status: status.into(),
        expires_epoch_seconds: Some(1000),
    })
}
#[test]
fn auth_and_removal() {
    for (response, error) in [
        (reply(false, "Expired"), AuthError::InvalidCredentials),
        (reply(true, "Expired"), AuthError::Expired),
        (reply(true, "Disabled"), AuthError::Malformed),
        (Err(AuthError::Network), AuthError::Network),
        (Err(AuthError::Http(403)), AuthError::Http(403)),
        (Err(AuthError::Malformed), AuthError::Malformed),
    ] {
        assert_eq!(
            Account::accept(profile("a"), response, 1).err(),
            Some(error)
        )
    }
    let accounts = [
        Account::accept(profile("a"), reply(true, "aCtIvE"), 20).unwrap(),
        Account::accept(profile("b"), reply(true, "Active"), 10).unwrap(),
    ];
    let a = latest(&accounts).unwrap();
    assert_eq!(a.status(), "aCtIvE");
    assert_eq!(a.expiry(), Some(1000));
    assert_eq!(a.authenticated_at(), 20);
    assert_eq!(a.removal().account_data, &account("a"));
    assert!(a.removal().credential == &CredentialRef("handle-a".into()));
    assert!(latest(&[]).is_none());
}
fn provider(series: bool) -> ProviderVod {
    ProviderVod {
        key: key("a", "001"),
        media: if series {
            VodType::Series {
                modified: Some(20),
                details_available: false,
            }
        } else {
            VodType::Movie {
                added: Some(20),
                extension: Some("mp4".into()),
            }
        },
        category_id: Some("1".into()),
        name: "Dune (2021) [4K] - DUAL AUDIO".into(),
        presentation: Presentation {
            overview: Some("provider".into()),
            poster: Some("poster".into()),
            genres: vec!["IPTV".into()],
            rating: Some(7.0),
            ..Default::default()
        },
        tmdb_hint: Some(2),
        year: None,
    }
}
fn candidate(kind: Kind, id: u64, name: &str, year: i32) -> Candidate {
    Candidate {
        kind,
        id: NonZeroU64::new(id).unwrap(),
        title: name.into(),
        original_title: "".into(),
        year: Some(year),
    }
}
#[test]
fn matching() {
    assert_eq!(
        search_terms("Dune (2021) [4K] - DUAL AUDIO", Some(1984)),
        ("Dune", Some(1984))
    );
    assert_eq!(
        search_terms("2001: A Space Odyssey", None),
        ("2001: A Space Odyssey", None)
    );
    assert_eq!(search_terms("My4K", None), ("My4K", None));
    for series in [false, true] {
        let p = provider(series);
        let kind = p.identity().kind();
        let inputs = MatchInputs::from(&p);
        let mut c = candidate(kind, 2, "Else", 2021);
        assert!(inputs.supplied(&c).is_none());
        c.original_title = "DUNE".into();
        assert!(inputs.supplied(&c).is_some());
        assert!(inputs.fallback(1, &[c.clone(), c.clone()]).is_some());
        assert!(inputs
            .fallback(1, &[c.clone(), candidate(kind, 3, "dune", 2021)])
            .is_none());
        assert!(inputs.fallback(2, &[c.clone()]).is_none());
        c.year = Some(1984);
        assert!(inputs.supplied(&c).is_none());
        c.year = Some(2021);
        c.kind = Kind::Live;
        assert!(inputs.supplied(&c).is_none());
    }
}
fn details(kind: Kind, id: u64) -> Details {
    Details {
        kind,
        id: NonZeroU64::new(id).unwrap(),
        imdb_id: Some("tt1".into()),
        presentation: Presentation {
            title: Some("TMDB".into()),
            poster: Some(" ".into()),
            genres: vec!["Sci-Fi".into()],
            rating: Some(f32::NAN),
            ..Default::default()
        },
    }
}
#[test]
fn enrichment_and_refresh() {
    for series in [false, true] {
        let p = provider(series);
        let kind = p.identity().kind();
        let mut v = Vod::new(p.clone());
        assert_eq!(v.display().title.as_deref(), Some(p.name.as_str()));
        let inputs = v.match_inputs();
        let c = candidate(kind, 2, "Dune", 2021);
        let token = || inputs.supplied(&c).unwrap();
        assert!(!v.attach(token(), details(kind, 4)));
        assert!(v.attach(token(), details(kind, 2)));
        assert_eq!(v.enrichment().unwrap().provenance, Provenance::Supplied);
        let display = v.display();
        assert_eq!(display.title.as_deref(), Some("TMDB"));
        assert_eq!(display.overview, p.presentation.overview);
        assert_eq!(display.poster, p.presentation.poster);
        assert_eq!(display.rating, Some(7.0));
        assert_eq!(display.genres, ["Sci-Fi"]);
        assert_eq!(v.provider(), &p);
        let mut changed = p.clone();
        changed.category_id = None;
        assert!(v.refresh(changed.clone()));
        assert!(v.enrichment().is_some());
        changed.tmdb_hint = None;
        assert!(v.refresh(changed));
        assert!(v.enrichment().is_none());
        assert!(!v.attach(token(), details(kind, 2)));
        for changed in [
            {
                let mut q = p.clone();
                q.name = "other".into();
                q
            },
            {
                let mut q = p.clone();
                q.presentation.date = Some("1984".into());
                q
            },
        ] {
            assert!(v.refresh(p.clone()));
            assert!(!v.attach(token(), details(kind, 2)));
            let current = v.match_inputs();
            let token = || current.supplied(&c).unwrap();
            assert!(v.attach(token(), details(kind, 2)));
            assert!(v.refresh(changed));
            assert!(v.enrichment().is_none());
            assert!(!v.attach(token(), details(kind, 2)))
        }
    }
}
fn episode(series: &str) -> EpisodeKey {
    EpisodeKey {
        series: key("a", series),
        episode_id: "001".into(),
    }
}
#[test]
fn playback_and_identity() {
    let m = OnDemand::Movie(key("a", "001"));
    let e = OnDemand::Episode(episode("s1"));
    let e2 = OnDemand::Episode(episode("s2"));
    assert_ne!(e, e2);
    assert_ne!(m, e);
    assert_ne!(m, OnDemand::Movie(key("b", "001")));
    for d in [-1, 0] {
        assert!(Progress::record(m.clone(), 100, d, 0).is_none())
    }
    for (pos, complete, restart) in [
        (4999, false, 0),
        (5000, false, 5000),
        (89999, false, 89999),
        (90000, true, 0),
        (100001, true, 0),
    ] {
        let p = Progress::record(m.clone(), pos, 100000, 0).unwrap();
        assert_eq!(p.completed(), complete);
        assert_eq!(p.resume_ms(), restart)
    }
    assert_eq!(resume(100000, 100000, false), 0);
    assert_eq!(resume(5000, 100000, true), 0);
    assert!(!Progress::record(e.clone(), 1, i64::MAX, 0)
        .unwrap()
        .completed());
    assert!(Progress::record(m.clone(), i64::MAX, i64::MAX, 0)
        .unwrap()
        .completed());
    let records = [
        Progress::record(e.clone(), 5000, 90000, 5).unwrap(),
        Progress::record(e2.clone(), 400, 800, 7).unwrap(),
        Progress::record(m.clone(), 90, 100, 8).unwrap(),
        Progress::record(OnDemand::Movie(key("b", "001")), 5, 90, 10).unwrap(),
    ];
    assert_eq!(
        continue_watching(&records, &account("a"))
            .iter()
            .map(|p| p.key())
            .collect::<Vec<_>>(),
        [&e2, &e]
    );
    let live = Playback::Live(key("a", "001"));
    assert!(matches!(live,Playback::Live(k) if k.id=="001"));
    assert!(matches!(
        Playback::OnDemand(m),
        Playback::OnDemand(OnDemand::Movie(_))
    ));
}
#[test]
fn favorites() {
    let m = provider(false).identity();
    let s = provider(true).identity();
    let b = VodKey::Movie(key("b", "001"));
    let mut f = Favorites::default();
    f.add(m.clone(), 1);
    f.add(m.clone(), 1);
    f.add(s.clone(), 2);
    f.add(b, 3);
    assert_eq!(f.newest(&account("a")), [&s, &m]);
    f.remove(&s);
    assert_eq!(f.newest(&account("a")), [&m])
}
#[test]
fn sync_outcomes() {
    let mut summary = Summary::default();
    for k in [Kind::Live, Kind::Movie, Kind::Series] {
        *summary.get_mut(k) = Scope::default().begin().finish(Outcome::Success(0))
    }
    assert!(!summary.usable());
    assert_eq!(summary.live.state, State::Empty);
    summary.live = summary.live.begin().finish(Outcome::Failed);
    assert_eq!(summary.live.state, State::Failed);
    summary.movies = summary.movies.begin().finish(Outcome::Success(42));
    assert!(summary.usable());
    assert_eq!(summary.movies.cached, 42);
    let mut refresh = summary.movies.begin();
    refresh.processed = 12;
    assert_eq!(refresh.progress().state, State::Running);
    assert_eq!(refresh.progress().processed, 12);
    assert_eq!(refresh.finish(Outcome::Cancelled), summary.movies);
    summary.movies = summary.movies.begin().finish(Outcome::Failed);
    assert_eq!(summary.movies.state, State::Stale);
    assert_eq!(summary.movies.cached, 42);
    assert!(summary.usable());
    assert_eq!(summary.shows.cached, 0);
    for outcome in [Outcome::Failed, Outcome::Cancelled] {
        assert_eq!(outcome.catalog_action(), RefreshAction::Retain)
    }
    assert_eq!(Outcome::Success(0).catalog_action(), RefreshAction::Replace)
}
#[test]
fn pairing() {
    assert!(Session::new("id".into(), "- ", 0).is_none());
    let s = Session::new("id".into(), "A-B C", 10).unwrap();
    assert_eq!(s.id(), "id");
    assert_eq!(s.accept("abc", 300010), Err(Rejection::Expired));
    assert_eq!(s.accept("bad", 11), Err(Rejection::WrongCode));
    assert_eq!(s.accept("abc", 300009), Ok(()));
    assert_eq!(s.accept("abc", 300009), Err(Rejection::Consumed));
    let s = Session::new("id2".into(), "code", 0).unwrap();
    let barrier = std::sync::Barrier::new(16);
    let accepted = std::thread::scope(|scope| {
        let workers: Vec<_> = (0..16)
            .map(|_| {
                scope.spawn(|| {
                    barrier.wait();
                    s.accept("CODE", 5).is_ok()
                })
            })
            .collect();
        workers
            .into_iter()
            .map(|w| usize::from(w.join().unwrap()))
            .sum::<usize>()
    });
    assert_eq!(accepted, 1)
}
#[test]
fn discovery() {
    let m = provider(false);
    let s = provider(true);
    let filter = Browse {
        account: account("a"),
        kind: Kind::Movie,
        category: Some("1".into()),
    };
    assert!(filter.matches(&m, " duNE "));
    assert!(!filter.matches(&m, "  "));
    assert!(!filter.matches(&s, "Dune"));
    assert!(!filter.matches(&m, "tmdb"));
    let shows = Browse {
        kind: Kind::Series,
        ..filter.clone()
    };
    assert!(shows.matches(&s, "dune"));
    assert_ne!(filter.category_key(), shows.category_key());
    assert!(!Browse {
        category: Some("x".into()),
        ..filter.clone()
    }
    .includes(&m));
    assert!(!Browse {
        account: account("b"),
        ..filter.clone()
    }
    .includes(&m));
    let mut r = RecentSearches::default();
    for q in ["one", "two", "three", "four", " THREE "] {
        r.record(q, true)
    }
    r.record("absent", false);
    r.record(" ", true);
    assert_eq!(r.entries(), ["THREE", "four", "two"]);
}
#[test]
fn bounded_snapshot() {
    struct Cache([ProviderVod; 3]);
    impl CachedCatalog for Cache {
        type Item = String;
        type Error = ();
        fn browse(&self, f: &Browse, p: PageRequest) -> Result<Page<String>, ()> {
            Ok(Page::bounded(
                p,
                self.0
                    .iter()
                    .filter(|r| f.includes(r))
                    .skip(p.offset)
                    .map(|r| r.name.clone()),
            ))
        }
    }
    let mut second = provider(false);
    second.name = "second".into();
    let cache = Cache([provider(false), second, provider(false)]);
    let page = cache
        .browse(
            &Browse {
                account: account("a"),
                kind: Kind::Movie,
                category: None,
            },
            PageRequest {
                offset: 1,
                limit: NonZeroUsize::new(1).unwrap(),
            },
        )
        .unwrap();
    assert_eq!(page.rows(), ["second"]);
}
#[test]
fn ordering() {
    for series in [false, true] {
        let first = provider(series);
        let mut other = first.clone();
        other.media = if series {
            VodType::Series {
                modified: None,
                details_available: false,
            }
        } else {
            VodType::Movie {
                extension: None,
                added: None,
            }
        };
        assert!(vod_order(&first, &other).is_lt());
        other.media = first.media.clone();
        other.name = "Z".into();
        assert!(vod_order(&first, &other).is_lt())
    }
    let channel = |number, name: &str| Channel {
        key: key("a", "01"),
        name: name.into(),
        category_id: Some("1".into()),
        number,
        icon: None,
        epg_id: Some("epg".into()),
        catchup: false,
    };
    let a = channel(1, "A");
    assert_eq!(a.epg_id.as_deref(), Some("epg"));
    assert!(channel_order(&a, &channel(2, "A")).is_lt());
    assert!(channel_order(&a, &channel(1, "Z")).is_lt());
    assert!(Browse {
        account: account("a"),
        kind: Kind::Live,
        category: Some("1".into())
    }
    .includes_channel(&a));
    let ep = |season, number| Episode {
        key: episode("s"),
        season,
        number,
        title: "E".into(),
        extension: None,
        overview: None,
        duration_ms: None,
        artwork: None,
    };
    assert!(episode_order(&ep(1, 4), &ep(2, 1)).is_lt());
    assert!(episode_order(&ep(1, 4), &ep(1, 5)).is_lt())
}
#[test]
fn focus_and_tracks() {
    let a = provider(false).identity();
    let b = provider(true).identity();
    let mut f = CardFocus::default();
    f.update(Some(&a), 10);
    f.update(Some(&a), 100);
    assert_eq!(f.preview_for(&a, 5009, true), Preview::Metadata);
    f.update(Some(&b), 5009);
    assert_eq!(f.preview_for(&a, 5010, true), Preview::Unfocused);
    assert_eq!(f.preview_for(&b, 5010, true), Preview::Metadata);
    assert_eq!(f.preview_for(&b, 10009, false), Preview::Still);
    assert_eq!(f.preview_for(&b, 10009, true), Preview::Trailer);
    f.update(None, 10010);
    assert_eq!(f.preview_for(&b, 10011, true), Preview::Unfocused);
    let tracks = [
        Track {
            kind: TrackKind::Audio,
            id: "1".into(),
        },
        Track {
            kind: TrackKind::Subtitle,
            id: "2".into(),
        },
    ];
    assert!(selectable(&tracks, TrackKind::Audio, "1"));
    assert!(selectable(&tracks, TrackKind::Subtitle, "2"));
    assert!(!selectable(&tracks, TrackKind::Subtitle, "1"));
    assert!(subtitle_lookup_enabled(true, true));
    assert!(!subtitle_lookup_enabled(false, true));
    assert!(!subtitle_lookup_enabled(true, false))
}

#[test]
fn unique_accounts_and_opaque_ids() {
    let a = account("A");
    assert!(!available_id(&a, [&a]));
    assert!(available_id(&account("a"), [&a]));
    assert_ne!(key("A", "001"), key("A", "1"));
}
#[test]
fn catalog_replacement_contract() {
    // Test double for the consumer of the L3 decision. Only an authoritative
    // successful response can replace the episode snapshot, including with empty.
    let mut snapshot = vec![episode("s")];
    let personal =
        Progress::record(OnDemand::Episode(snapshot[0].clone()), 6000, 100000, 10).unwrap();
    let mut favorites = Favorites::default();
    let series = VodKey::Series(key("a", "s"));
    favorites.add(series.clone(), 0);
    for outcome in [Outcome::Cancelled, Outcome::Failed, Outcome::Success(0)] {
        if outcome.catalog_action() == RefreshAction::Replace {
            snapshot.clear();
        }
        assert_eq!(snapshot.is_empty(), matches!(outcome, Outcome::Success(0)));
        assert_eq!(favorites.newest(&account("a")), [&series]);
        assert_eq!(personal.resume_ms(), 6000);
    }
}

#[test]
fn display_date_uses_calendar_validity_without_mutating_sources() {
    for series in [false, true] {
        let mut p = provider(series);
        p.presentation.date = Some("2021".into());
        let kind = p.identity().kind();
        let mut v = Vod::new(p.clone());
        for (raw, expected) in [
            (None, "2021"),
            (Some(" "), "2021"),
            (Some("not-a-date"), "2021"),
            (Some("1900-02-29"), "2021"),
            (Some("2000-02-29"), "2000-02-29"),
            (Some("2024-04-31"), "2021"),
            (Some("2024-13-01"), "2021"),
            (Some("2024-01-00"), "2021"),
            (Some("2024-01-31"), "2024-01-31"),
        ] {
            let mut d = details(kind, 2);
            d.presentation.date = raw.map(str::to_owned);
            let token = v
                .match_inputs()
                .supplied(&candidate(kind, 2, "Dune", 2021))
                .unwrap();
            assert!(v.attach(token, d));
            assert_eq!(v.display().date.as_deref(), Some(expected));
            assert_eq!(v.display().title.as_deref(), Some("TMDB"));
            assert_eq!(
                v.enrichment().unwrap().details.presentation.date.as_deref(),
                raw
            );
            assert_eq!(v.provider(), &p);
        }
    }
}

#[test]
fn match_generation_is_owned_by_the_current_vod() {
    for series in [false, true] {
        let p = provider(series);
        let kind = p.identity().kind();
        let c = candidate(kind, 2, "Dune", 2021);
        let mut v = Vod::new(p.clone());
        let issued = v.match_inputs();
        for other in [
            p.clone(),
            {
                let mut q = p.clone();
                q.key.account = account("b");
                q
            },
            {
                let mut q = p.clone();
                q.key.id = "other".into();
                q
            },
            provider(!series),
        ] {
            let mut other = Vod::new(other);
            assert!(!other.attach(issued.supplied(&c).unwrap(), details(kind, 2)));
        }
        let mut changed = p.clone();
        changed.category_id = None;
        assert!(v.refresh(changed));
        assert!(v.attach(issued.supplied(&c).unwrap(), details(kind, 2)));
        let mut changed = p.clone();
        changed.year = Some(2000);
        assert!(v.refresh(changed));
        assert!(v.refresh(p));
        assert!(!v.attach(issued.supplied(&c).unwrap(), details(kind, 2)));
        let current = v.match_inputs();
        assert!(v.attach(current.supplied(&c).unwrap(), details(kind, 2)));
    }
}

#[test]
fn active_authentication_is_authoritative_over_past_expiry() {
    let now = 1_001;
    let active = Account::accept(profile("a"), reply(true, "aCtIvE"), now).unwrap();
    assert_eq!(active.expiry(), Some(1_000));
    assert_eq!(active.authenticated_at(), now);
    for (auth, status, reason) in [
        (true, "Expired", AuthError::Expired),
        (false, "Active", AuthError::InvalidCredentials),
        (true, "Disabled", AuthError::Malformed),
    ] {
        assert_eq!(
            Account::accept(profile("a"), reply(auth, status), now).err(),
            Some(reason)
        );
    }
}
