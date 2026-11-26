#![no_main]
use libfuzzer_sys::fuzz_target;
use oh_domain::{account::AccountId,catalog::{Kind,ProviderKey,ProviderVod,VodType},metadata::{search_terms,Candidate,Details,Presentation,Vod},pairing::Session,discovery::{Browse,RecentSearches},personal::{OnDemand,Progress}};
use std::num::NonZeroU64;
fuzz_target!(|data: &[u8]| {
    let s = String::from_utf8_lossy(data);
    let (title, year) = search_terms(&s, Some(2024));
    assert_eq!(year, Some(2024));
    assert!(s.contains(title));
    if let Some(session) = Session::new("id".into(), &s, 0) {
        assert!(session.accept(&s, 0).is_ok());
        assert!(session.accept(&s, 0).is_err());
    }
    let a=AccountId("a".into());
    let mut h=RecentSearches::default();
    h.record(&a,&s,true);
    assert_eq!(h.entries(&a).len(),usize::from(!s.trim().is_empty()));
    assert!(h.entries(&AccountId("b".into())).is_empty());
    let p=ProviderVod{key:ProviderKey{account:a.clone(),id:"001".into()},media:VodType::Movie{extension:None,added:None},category_id:None,name:"Film".into(),presentation:Presentation{date:Some("2024-01-01".into()),..Default::default()},tmdb_hint:Some(1),year:Some(2024)};
    let browse=Browse{account:a,kind:Kind::Movie,category:None};
    assert_eq!(browse.matches(&p,&s),!s.trim().is_empty() && "film".contains(&s.trim().to_ascii_lowercase()));
    let mut v=Vod::new(p);
    let candidate=Candidate{kind:Kind::Movie,id:NonZeroU64::new(1).unwrap(),title:"Film".into(),original_title:String::new(),year:Some(2024)};
    let d=Details{kind:Kind::Movie,id:candidate.id,imdb_id:None,presentation:Presentation{date:Some(s.to_string()),..Default::default()}};
    assert!(v.attach(v.match_inputs().supplied(&candidate).unwrap(),d));
    assert_eq!(v.enrichment().unwrap().details.presentation.date.as_deref(),Some(s.as_ref()));
    if let Some(out)=v.display().date {
        assert!(out=="2024-01-01" || out==s);
    }
    if data.len()>=16 {
        let pos=i64::from_le_bytes(data[..8].try_into().unwrap());
        let dur=i64::from_le_bytes(data[8..16].try_into().unwrap());
        let p=Progress::record(OnDemand::Movie(v.provider().key.clone()),pos,dur,0);
        assert_eq!(p.is_some(),dur>0);
        if let Some(p)=p {assert_eq!(p.completed(),pos.max(0)>=dur-dur/10);}
    }
});
