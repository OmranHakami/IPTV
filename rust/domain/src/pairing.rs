use std::sync::atomic::{AtomicBool, Ordering};
fn normalize(code: &str) -> String {
    code.chars()
        .filter(|c| !matches!(c, ' ' | '-'))
        .flat_map(char::to_uppercase)
        .collect()
}
pub struct Session {
    id: String,
    code: String,
    created_ms: u64,
    consumed: AtomicBool,
}
#[derive(Debug, Eq, PartialEq)]
pub enum Rejection {
    WrongCode,
    Expired,
    Consumed,
}
impl Session {
    pub fn new(id: String, code: &str, created_ms: u64) -> Option<Self> {
        let code = normalize(code);
        if code.is_empty() {
            return None;
        }
        Some(Self {
            id,
            code,
            created_ms,
            consumed: AtomicBool::new(false),
        })
    }
    pub fn id(&self) -> &str {
        &self.id
    }
    /// All submitters for a session share this instance and use the same monotonic time base.
    pub fn accept(&self, code: &str, now_ms: u64) -> Result<(), Rejection> {
        if !matches!(now_ms.checked_sub(self.created_ms), Some(0..300_000)) {
            return Err(Rejection::Expired);
        }
        if self.code != normalize(code) {
            return Err(Rejection::WrongCode);
        }
        self.consumed
            .compare_exchange(false, true, Ordering::AcqRel, Ordering::Acquire)
            .map(|_| ())
            .map_err(|_| Rejection::Consumed)
    }
}
