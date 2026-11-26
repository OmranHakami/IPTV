use oh_domain::{
    account::AccountId,
    catalog::{Channel, Kind, ProviderKey, ProviderVod, VodType},
    discovery::Browse,
    metadata::{search_terms, Candidate, Details, Presentation, Vod},
};
use std::num::NonZeroU64;
fn key() -> ProviderKey {
    ProviderKey { account: AccountId("a".into()), id: "1".into() }
}
fn provider() -> ProviderVod {
    ProviderVod {
        key: key(), media: VodType::Movie { extension: None, added: None },
        category_id: None, name: "Film".into(),
        presentation: Presentation { date: Some("2024-01-01".into()), ..Default::default() },
        tmdb_hint: Some(42), year: Some(2024),
    }
}
fn candidate() -> Candidate {
    Candidate { kind: Kind::Movie, id: NonZeroU64::new(42).unwrap(), title: "Film".into(), original_title: String::new(), year: Some(2024) }
}
#[test]
fn channel_filter_rejects_wrong_account_kind_category() {
    for row_category in [None, Some("x")] {
        let row = Channel { key: key(), category_id: row_category.map(str::to_owned), number: 1, name: "News".into(), icon: None, epg_id: None, catchup: false };
        for account in ["a","b"] {
            for kind in [Kind::Live,Kind::Movie,Kind::Series] {
                for category in [None,Some("x"),Some("y")] {
                    let browse = Browse { account: AccountId(account.into()), kind, category: category.map(str::to_owned) };
                    assert_eq!(browse.includes_channel(&row), account == "a" && kind == Kind::Live && (category.is_none() || category == row_category));
                }
            }
        }
    }
}
#[test]
fn second_separator_and_year_length_rejection() {
    let mut vod = Vod::new(provider());
    for date in ["2024-01/01","2024/01-01","2024/01/01","2024-02-29"] {
        let token = vod.match_inputs().supplied(&candidate()).unwrap();
        assert!(vod.attach(token, Details { kind: Kind::Movie, id: NonZeroU64::new(42).unwrap(), imdb_id: None, presentation: Presentation { date: Some(date.into()), ..Default::default() } }));
        assert_eq!(vod.display().date.as_deref(), Some(if date == "2024-02-29" {date} else {"2024-01-01"}));
        assert_eq!(vod.enrichment().unwrap().details.presentation.date.as_deref(), Some(date));
    }
    for title in ["Film (02024)","Film (001900)"] {
        assert_eq!(search_terms(title,None),(title,None));
    }
    assert_eq!(search_terms("Film (2024)",None),("Film",Some(2024)));
}
#[test]
fn unknown_year_and_empty_normalized_title() {
    let mut row = provider();
    row.year = None;
    row.presentation.date = None;
    let vod = Vod::new(row.clone());
    let mut c = candidate();
    c.year = Some(2000);
    assert!(vod.match_inputs().supplied(&c).is_some());
    c.year = None;
    assert!(vod.match_inputs().supplied(&c).is_some());
    row.name = "!!!".into();
    c.title = "!!!".into();
    c.original_title = "!!!".into();
    assert!(Vod::new(row).match_inputs().supplied(&c).is_none());
}
