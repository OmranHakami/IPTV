use crate::{
    account::AccountId,
    catalog::{EpisodeKey, ProviderKey, VodKey},
};
use std::collections::HashMap;
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub enum OnDemand {
    Movie(ProviderKey),
    Episode(EpisodeKey),
}
impl OnDemand {
    pub fn account(&self) -> &AccountId {
        match self {
            Self::Movie(k) => &k.account,
            Self::Episode(k) => &k.series.account,
        }
    }
}
pub enum Playback {
    Live(ProviderKey),
    OnDemand(OnDemand),
}
#[derive(Debug)]
pub struct Progress {
    key: OnDemand,
    position: i64,
    duration: i64,
    completed: bool,
    pub updated_at: i64,
}
impl Progress {
    pub fn record(key: OnDemand, position: i64, duration: i64, updated_at: i64) -> Option<Self> {
        if duration <= 0 {
            return None;
        }
        let position = position.max(0);
        let completed = i128::from(position) * 10 >= i128::from(duration) * 9;
        Some(Self {
            key,
            position,
            duration,
            updated_at,
            completed,
        })
    }
    pub fn key(&self) -> &OnDemand {
        &self.key
    }
    pub fn completed(&self) -> bool {
        self.completed
    }
    pub fn position_ms(&self) -> i64 {
        self.position
    }
    pub fn duration_ms(&self) -> i64 {
        self.duration
    }
    pub fn resume_ms(&self) -> i64 {
        resume(self.position, self.duration, self.completed)
    }
}
pub fn resume(position: i64, duration: i64, completed: bool) -> i64 {
    if completed || position < 5000 || position >= duration {
        0
    } else {
        position
    }
}
pub fn continue_watching<'a>(records: &'a [Progress], account: &AccountId) -> Vec<&'a Progress> {
    let mut rows: Vec<_> = records
        .iter()
        .filter(|r| r.key.account() == account && !r.completed)
        .collect();
    rows.sort_by_key(|r| std::cmp::Reverse(r.updated_at));
    rows
}
#[derive(Default)]
pub struct Favorites(HashMap<VodKey, i64>);
impl Favorites {
    pub fn add(&mut self, key: VodKey, created_at: i64) {
        self.0.entry(key).or_insert(created_at);
    }
    pub fn remove(&mut self, key: &VodKey) {
        self.0.remove(key);
    }
    pub fn newest(&self, account: &AccountId) -> Vec<&VodKey> {
        let mut rows: Vec<_> = self
            .0
            .iter()
            .filter(|(key, _)| &key.provider().account == account)
            .collect();
        rows.sort_by_key(|(_, time)| std::cmp::Reverse(**time));
        rows.into_iter().map(|(key, _)| key).collect()
    }
}
#[derive(Clone, Copy, Eq, PartialEq)]
pub enum TrackKind {
    Audio,
    Subtitle,
}
pub struct Track {
    pub kind: TrackKind,
    pub id: String,
}
pub fn selectable(tracks: &[Track], kind: TrackKind, id: &str) -> bool {
    tracks.iter().any(|t| t.kind == kind && t.id == id)
}
pub fn subtitle_lookup_enabled(configured: bool, enabled: bool) -> bool {
    configured && enabled
}
