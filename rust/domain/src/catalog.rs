use crate::{account::AccountId, metadata::Presentation};
#[derive(Clone, Copy, Debug, Eq, PartialEq, Hash)]
pub enum Kind {
    Live,
    Movie,
    Series,
}
#[derive(Clone, Debug, Eq, PartialEq, Hash)]
pub struct ProviderKey {
    pub account: AccountId,
    pub id: String,
}
#[derive(Clone, Debug, Eq, PartialEq, Hash)]
pub enum VodKey {
    Movie(ProviderKey),
    Series(ProviderKey),
}
impl VodKey {
    pub fn provider(&self) -> &ProviderKey {
        match self {
            Self::Movie(k) | Self::Series(k) => k,
        }
    }
    pub fn kind(&self) -> Kind {
        match self {
            Self::Movie(_) => Kind::Movie,
            Self::Series(_) => Kind::Series,
        }
    }
}
#[derive(Clone, Debug, Eq, PartialEq, Hash)]
pub struct EpisodeKey {
    pub series: ProviderKey,
    pub episode_id: String,
}
#[derive(Clone, Debug, Eq, PartialEq, Hash)]
pub struct CategoryKey {
    pub account: AccountId,
    pub kind: Kind,
    pub id: String,
}
pub struct Category {
    pub key: CategoryKey,
    pub name: String,
    pub sort_order: i64,
}
pub struct Channel {
    pub key: ProviderKey,
    pub category_id: Option<String>,
    pub number: i64,
    pub name: String,
    pub icon: Option<String>,
    pub epg_id: Option<String>,
    pub catchup: bool,
}
pub struct Episode {
    pub key: EpisodeKey,
    pub season: i32,
    pub number: i32,
    pub title: String,
    pub extension: Option<String>,
    pub overview: Option<String>,
    pub duration_ms: Option<u64>,
    pub artwork: Option<String>,
}
#[derive(Clone, Debug, PartialEq)]
pub enum VodType {
    Movie {
        extension: Option<String>,
        added: Option<i64>,
    },
    Series {
        modified: Option<i64>,
        details_available: bool,
    },
}
#[derive(Clone, Debug, PartialEq)]
pub struct ProviderVod {
    pub key: ProviderKey,
    pub media: VodType,
    pub category_id: Option<String>,
    pub name: String,
    pub presentation: Presentation,
    pub tmdb_hint: Option<u64>,
    pub year: Option<i32>,
}
impl ProviderVod {
    pub fn identity(&self) -> VodKey {
        match self.media {
            VodType::Movie { .. } => VodKey::Movie(self.key.clone()),
            VodType::Series { .. } => VodKey::Series(self.key.clone()),
        }
    }
    pub fn timestamp(&self) -> i64 {
        match self.media {
            VodType::Movie { added, .. } => added,
            VodType::Series { modified, .. } => modified,
        }
        .unwrap_or(0)
    }
}
pub fn vod_order(a: &ProviderVod, b: &ProviderVod) -> std::cmp::Ordering {
    b.timestamp()
        .cmp(&a.timestamp())
        .then_with(|| a.name.cmp(&b.name))
}
pub fn channel_order(a: &Channel, b: &Channel) -> std::cmp::Ordering {
    (a.number, &a.name).cmp(&(b.number, &b.name))
}
pub fn episode_order(a: &Episode, b: &Episode) -> std::cmp::Ordering {
    (a.season, a.number).cmp(&(b.season, b.number))
}
