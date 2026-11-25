use crate::{
    account::AccountId,
    catalog::{CategoryKey, Channel, Kind, ProviderVod, VodKey},
};
use std::num::NonZeroUsize;
#[derive(Clone)]
pub struct Browse {
    pub account: AccountId,
    pub kind: Kind,
    pub category: Option<String>,
}
impl Browse {
    pub fn category_key(&self) -> Option<CategoryKey> {
        self.category.as_ref().map(|id| CategoryKey {
            account: self.account.clone(),
            kind: self.kind,
            id: id.clone(),
        })
    }
    pub fn includes(&self, row: &ProviderVod) -> bool {
        self.account == row.key.account
            && self.kind == row.identity().kind()
            && (self.category.is_none() || self.category == row.category_id)
    }
    pub fn includes_channel(&self, row: &Channel) -> bool {
        self.kind == Kind::Live
            && self.account == row.key.account
            && (self.category.is_none() || self.category == row.category_id)
    }
    pub fn matches(&self, row: &ProviderVod, query: &str) -> bool {
        let q = query.trim();
        !q.is_empty()
            && self.includes(row)
            && row
                .name
                .to_ascii_lowercase()
                .contains(&q.to_ascii_lowercase())
    }
}
#[derive(Clone, Copy)]
pub struct PageRequest {
    pub offset: usize,
    pub limit: NonZeroUsize,
}
pub struct Page<T>(Vec<T>);
impl<T> Page<T> {
    pub fn bounded(request: PageRequest, rows: impl IntoIterator<Item = T>) -> Self {
        Self(rows.into_iter().take(request.limit.get()).collect())
    }
    pub fn rows(&self) -> &[T] {
        &self.0
    }
}
/// Reads from the current local snapshot. Implementations apply offset before constructing a page.
pub trait CachedCatalog {
    type Item;
    type Error;
    fn browse(&self, filter: &Browse, page: PageRequest) -> Result<Page<Self::Item>, Self::Error>;
}
#[derive(Default)]
pub struct RecentSearches(Vec<String>);
impl RecentSearches {
    pub fn record(&mut self, query: &str, had_results: bool) {
        let q = query.trim();
        if !had_results || q.is_empty() {
            return;
        }
        self.0.retain(|s| s.to_lowercase() != q.to_lowercase());
        self.0.insert(0, q.to_owned());
        self.0.truncate(3)
    }
    pub fn entries(&self) -> &[String] {
        &self.0
    }
}
#[derive(Debug, Eq, PartialEq)]
pub enum Preview {
    Unfocused,
    Metadata,
    Still,
    Trailer,
}
/// Elapsed time is for the currently focused card only. Focus loss clears the interval.
pub fn preview(continuous_focus_ms: Option<u64>, has_trailer: bool) -> Preview {
    match continuous_focus_ms {
        None => Preview::Unfocused,
        Some(0..5000) => Preview::Metadata,
        Some(_) if has_trailer => Preview::Trailer,
        Some(_) => Preview::Still,
    }
}

#[derive(Default)]
pub struct CardFocus(Option<(VodKey, u64)>);
impl CardFocus {
    pub fn update(&mut self, key: Option<&VodKey>, now_ms: u64) {
        if self.0.as_ref().map(|(k, _)| k) != key {
            self.0 = key.map(|k| (k.clone(), now_ms));
        }
    }
    pub fn preview_for(&self, key: &VodKey, now_ms: u64, has_trailer: bool) -> Preview {
        let elapsed = self
            .0
            .as_ref()
            .filter(|(k, _)| k == key)
            .map(|(_, since)| now_ms.saturating_sub(*since));
        preview(elapsed, has_trailer)
    }
}
