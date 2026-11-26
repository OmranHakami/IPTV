use crate::catalog::{Kind, ProviderVod, VodKey};
use std::num::NonZeroU64;

pub use crate::presentation::Presentation;
fn nonblank(value: &Option<String>) -> Option<&String> {
    value.as_ref().filter(|s| !s.trim().is_empty())
}
/// TMDB release_date and first_air_date use YYYY-MM-DD.
fn calendar_date(value: &str) -> bool {
    let bytes = value.as_bytes();
    if bytes.len() != 10 || bytes[4] != b'-' || bytes[7] != b'-' {
        return false;
    }
    let decimal = |digits: &[u8]| {
        digits.iter().try_fold(0u32, |n, &d| {
            d.is_ascii_digit().then(|| n * 10 + u32::from(d - b'0'))
        })
    };
    let (Some(year), Some(month), Some(day)) = (
        decimal(&bytes[..4]),
        decimal(&bytes[5..7]),
        decimal(&bytes[8..]),
    ) else {
        return false;
    };
    let leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    let last = match month {
        2 if leap => 29,
        2 => 28,
        4 | 6 | 9 | 11 => 30,
        1 | 3 | 5 | 7 | 8 | 10 | 12 => 31,
        _ => return false,
    };
    year > 0 && (1..=last).contains(&day)
}
fn normalized(s: &str) -> String {
    s.to_lowercase()
        .split(|c: char| !c.is_alphanumeric())
        .filter(|x| !x.is_empty())
        .collect::<Vec<_>>()
        .join(" ")
}
fn strip_quality(s: &str) -> Option<&str> {
    let end = s.trim_end_matches([']', ')']).trim_end();
    let split = |c: char| c.is_whitespace() || ['.', '-', '[', '('].contains(&c);
    let boundary = end.rfind(split)?;
    let (prefix, tail) = end.split_at(boundary);
    let last = tail.trim_start_matches(split);
    let prefix = prefix.trim_end_matches(split);
    if ["4k", "uhd", "1080p", "720p"]
        .iter()
        .any(|v| last.eq_ignore_ascii_case(v))
    {
        return Some(prefix);
    }
    if last.eq_ignore_ascii_case("audio") {
        let i = prefix.rfind(split)?;
        if prefix[i..]
            .trim_start_matches(split)
            .eq_ignore_ascii_case("dual")
        {
            return Some(prefix[..i].trim_end_matches(split));
        }
    }
    None
}
pub fn search_terms(name: &str, explicit: Option<i32>) -> (&str, Option<i32>) {
    let mut title = name.trim();
    while let Some(shorter) = strip_quality(title) {
        title = shorter
    }
    if let Some((prefix, year)) = title.strip_suffix(')').and_then(|s| s.rsplit_once('(')) {
        if year.len() == 4 && year.bytes().all(|c| c.is_ascii_digit()) {
            if let Ok(year @ 1900..=2099) = year.parse() {
                return (prefix.trim_end(), explicit.or(Some(year)));
            }
        }
    }
    (title, explicit)
}
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct MatchInputs {
    key: VodKey,
    name: String,
    year: Option<i32>,
    date: Option<String>,
    hint: Option<u64>,
}
impl From<&ProviderVod> for MatchInputs {
    fn from(p: &ProviderVod) -> Self {
        Self {
            key: p.identity(),
            name: p.name.clone(),
            year: p.year,
            date: p.presentation.date.clone(),
            hint: p.tmdb_hint,
        }
    }
}
#[derive(Clone, Debug)]
pub struct Candidate {
    pub kind: Kind,
    pub id: NonZeroU64,
    pub title: String,
    pub original_title: String,
    pub year: Option<i32>,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Provenance {
    Supplied,
    Search,
}
pub struct Matched {
    inputs: MatchInputs,
    id: NonZeroU64,
    provenance: Provenance,
}
impl MatchInputs {
    fn accepts(&self, c: &Candidate) -> bool {
        let explicit = self
            .year
            .or_else(|| self.date.as_ref()?.get(..4)?.parse().ok());
        let (name, year) = search_terms(&self.name, explicit);
        let target = normalized(name);
        c.kind == self.key.kind()
            && !target.is_empty()
            && (year.is_none() || year == c.year)
            && (target == normalized(&c.title) || target == normalized(&c.original_title))
    }
    fn matched(&self, c: &Candidate, provenance: Provenance) -> Matched {
        Matched {
            inputs: self.clone(),
            id: c.id,
            provenance,
        }
    }
    pub fn supplied(&self, c: &Candidate) -> Option<Matched> {
        (self.hint == Some(c.id.get()) && self.accepts(c))
            .then(|| self.matched(c, Provenance::Supplied))
    }
    pub fn fallback(&self, total_pages: u32, rows: &[Candidate]) -> Option<Matched> {
        if total_pages != 1 {
            return None;
        }
        let mut matches = rows.iter().filter(|c| self.accepts(c));
        let first = matches.next()?;
        matches
            .all(|c| c.id == first.id)
            .then(|| self.matched(first, Provenance::Search))
    }
}
pub struct Details {
    pub kind: Kind,
    pub id: NonZeroU64,
    pub imdb_id: Option<String>,
    pub presentation: Presentation,
}
pub struct Enrichment {
    pub details: Details,
    pub provenance: Provenance,
}
pub struct Vod {
    provider: ProviderVod,
    enrichment: Option<Enrichment>,
}
impl Vod {
    pub fn new(provider: ProviderVod) -> Self {
        Self {
            provider,
            enrichment: None,
        }
    }
    pub fn provider(&self) -> &ProviderVod {
        &self.provider
    }
    pub fn enrichment(&self) -> Option<&Enrichment> {
        self.enrichment.as_ref()
    }
    pub fn refresh(&mut self, provider: ProviderVod) -> bool {
        if self.provider.identity() != provider.identity() {
            return false;
        }
        if MatchInputs::from(&self.provider) != MatchInputs::from(&provider) {
            self.enrichment = None
        }
        self.provider = provider;
        true
    }
    pub fn attach(&mut self, matched: Matched, details: Details) -> bool {
        if matched.inputs != MatchInputs::from(&self.provider)
            || matched.id != details.id
            || details.kind != matched.inputs.key.kind()
        {
            return false;
        }
        self.enrichment = Some(Enrichment {
            details,
            provenance: matched.provenance,
        });
        true
    }
    pub fn display(&self) -> Presentation {
        let p = &self.provider.presentation;
        let empty = Presentation::default();
        let t = self
            .enrichment
            .as_ref()
            .map(|e| &e.details.presentation)
            .unwrap_or(&empty);
        let field =
            |a: &Option<String>, b: &Option<String>| nonblank(a).or_else(|| nonblank(b)).cloned();
        let genres: Vec<_> = t
            .genres
            .iter()
            .filter(|s| !s.trim().is_empty())
            .cloned()
            .collect();
        Presentation {
            title: field(&t.title, &p.title).or_else(|| Some(self.provider.name.clone())),
            overview: field(&t.overview, &p.overview),
            date: t
                .date
                .as_ref()
                .filter(|d| calendar_date(d.trim()))
                .or_else(|| nonblank(&p.date))
                .cloned(),
            poster: field(&t.poster, &p.poster),
            backdrop: field(&t.backdrop, &p.backdrop),
            trailer: field(&t.trailer, &p.trailer),
            runtime_minutes: t.runtime_minutes.filter(|n| *n > 0).or(p.runtime_minutes),
            rating: t.rating.filter(|n| (0.0..=10.0).contains(n)).or(p.rating),
            vote_count: t.vote_count.or(p.vote_count),
            genres: if genres.is_empty() {
                p.genres.clone()
            } else {
                genres
            },
        }
    }
}
