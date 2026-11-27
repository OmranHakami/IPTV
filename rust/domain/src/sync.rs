use crate::catalog::Kind;
#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
pub enum State {
    #[default]
    Waiting,
    Running,
    Ready,
    Empty,
    Failed,
    Stale,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
pub struct Scope {
    pub state: State,
    pub cached: u64,
    pub processed: u64,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Outcome {
    Success(u64),
    Failed,
    Cancelled,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RefreshAction {
    Retain,
    Replace,
}
impl Outcome {
    /// Applies equally to a catalog scope and a validated series episode set.
    pub fn catalog_action(self) -> RefreshAction {
        match self {
            Self::Success(_) => RefreshAction::Replace,
            _ => RefreshAction::Retain,
        }
    }
}
impl Scope {
    pub fn begin(self) -> Refresh {
        Refresh {
            previous: self,
            processed: 0,
        }
    }
}
pub struct Refresh {
    previous: Scope,
    pub processed: u64,
}
impl Refresh {
    pub fn progress(&self) -> Scope {
        Scope {
            state: State::Running,
            processed: self.processed,
            ..self.previous
        }
    }
    /// Called on the snapshot captured before refresh. Cancellation restores that snapshot.
    pub fn finish(self, outcome: Outcome) -> Scope {
        match outcome {
            Outcome::Cancelled => self.previous,
            Outcome::Success(n) => Scope {
                state: if n == 0 { State::Empty } else { State::Ready },
                cached: n,
                processed: n,
            },
            Outcome::Failed => Scope {
                state: if self.previous.cached > 0 {
                    State::Stale
                } else {
                    State::Failed
                },
                processed: self.processed,
                ..self.previous
            },
        }
    }
}
#[derive(Default)]
pub struct Summary {
    pub live: Scope,
    pub movies: Scope,
    pub shows: Scope,
}
impl Summary {
    pub fn get_mut(&mut self, kind: Kind) -> &mut Scope {
        match kind {
            Kind::Movie => &mut self.movies,
            Kind::Series => &mut self.shows,
            Kind::Live => &mut self.live,
        }
    }
    pub fn usable(&self) -> bool {
        [self.live, self.movies, self.shows]
            .iter()
            .any(|s| matches!(s.state, State::Ready | State::Stale))
    }
}
