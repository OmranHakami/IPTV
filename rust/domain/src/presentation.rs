#[derive(Clone, Debug, Default, PartialEq)]
pub struct Presentation {
    pub title: Option<String>,
    pub overview: Option<String>,
    pub date: Option<String>,
    pub poster: Option<String>,
    pub backdrop: Option<String>,
    pub trailer: Option<String>,
    pub runtime_minutes: Option<u32>,
    pub rating: Option<f32>,
    pub vote_count: Option<u64>,
    pub genres: Vec<String>,
}
