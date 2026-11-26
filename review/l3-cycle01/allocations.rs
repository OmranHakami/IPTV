use oh_domain::{account::AccountId,catalog::{Kind,ProviderKey,ProviderVod,VodType},metadata::{Candidate,Details,MatchInputs,Presentation,Vod},discovery::RecentSearches};
use std::{alloc::{GlobalAlloc,Layout,System},sync::atomic::{AtomicUsize,Ordering::Relaxed},num::NonZeroU64,hint::black_box};
struct Measured;
static LIVE:AtomicUsize=AtomicUsize::new(0);
static PEAK:AtomicUsize=AtomicUsize::new(0);
static ALLOCS:AtomicUsize=AtomicUsize::new(0);
static REALLOCS:AtomicUsize=AtomicUsize::new(0);
fn increase(n:usize){let live=LIVE.fetch_add(n,Relaxed)+n;PEAK.fetch_max(live,Relaxed);}
unsafe impl GlobalAlloc for Measured {
 unsafe fn alloc(&self,l:Layout)->*mut u8 {let p=unsafe{System.alloc(l)};if !p.is_null(){ALLOCS.fetch_add(1,Relaxed);increase(l.size());}p}
 unsafe fn dealloc(&self,p:*mut u8,l:Layout){LIVE.fetch_sub(l.size(),Relaxed);unsafe{System.dealloc(p,l)}}
 unsafe fn realloc(&self,p:*mut u8,l:Layout,n:usize)->*mut u8 {let q=unsafe{System.realloc(p,l,n)};if !q.is_null(){REALLOCS.fetch_add(1,Relaxed);if n>=l.size(){increase(n-l.size());}else{LIVE.fetch_sub(l.size()-n,Relaxed);}}q}
}
#[global_allocator] static A:Measured=Measured;
fn sample(f:impl FnOnce())->(usize,usize,usize,usize){let base=LIVE.load(Relaxed);PEAK.store(base,Relaxed);ALLOCS.store(0,Relaxed);REALLOCS.store(0,Relaxed);f();(ALLOCS.load(Relaxed),REALLOCS.load(Relaxed),PEAK.load(Relaxed).saturating_sub(base),LIVE.load(Relaxed).saturating_sub(base))}
fn row(i:usize)->ProviderVod {ProviderVod{key:ProviderKey{account:AccountId("a".into()),id:i.to_string()},media:VodType::Movie{extension:Some("mp4".into()),added:Some(i as i64)},category_id:Some("1".into()),name:format!("Film {i}"),presentation:Presentation{date:Some("2024-01-01".into()),overview:Some("description ".repeat(20)),..Default::default()},tmdb_hint:Some(1),year:Some(2024)}}
fn candidate(i:usize)->Candidate{Candidate{kind:Kind::Movie,id:NonZeroU64::new(1).unwrap(),title:format!("Film {i}"),original_title:String::new(),year:Some(2024)}}
fn details()->Details{Details{kind:Kind::Movie,id:NonZeroU64::new(1).unwrap(),imdb_id:None,presentation:Presentation{date:Some("2024-02-29".into()),title:Some("Title".into()),overview:Some("description ".repeat(20)),..Default::default()}}}
fn main(){
 for n in [1000,10000,50000,100000]{
  let rows:Vec<_>=(0..n).map(row).collect();let cs:Vec<_>=(0..n).map(candidate).collect();
  for round in 0..3{
   let m=sample(||{for (r,c) in rows.iter().zip(&cs){black_box(MatchInputs::from(r).supplied(c));}});
   let l=sample(||{for (r,c) in rows.iter().zip(&cs){let mut v=Vod::new(r.clone());let t=v.match_inputs().supplied(c).unwrap();assert!(v.attach(t,details()));assert!(v.refresh(r.clone()));black_box(v.display());}});
   println!("ALLOC n={n} round={round} match={m:?} lifecycle={l:?}");
  }
 }
 for n in [1,10,100,1000]{
  let accounts:Vec<_>=(0..n).map(|i|AccountId(format!("account-{i}"))).collect();
  let mut storage=RecentSearches::default();
  let measurement=sample(||{for account in &accounts{for q in ["one","two","three","four"]{storage.record(account,q,true);}}black_box(&storage);});
  println!("HISTORY accounts={n} measurement={measurement:?}");
 }
}
