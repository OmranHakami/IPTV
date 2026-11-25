#[derive(Clone, Debug, Eq, PartialEq, Hash, Ord, PartialOrd)]
pub struct AccountId(pub String);
/// A handle to platform-managed credentials. No credential values cross this boundary.
#[derive(Clone, PartialEq, Eq)]
pub struct CredentialRef(pub String);
pub struct XtreamProfile {
    pub id: AccountId,
    pub display_name: String,
    pub provider_base_url: String,
    pub credentials: CredentialRef,
}
#[derive(Debug, Eq, PartialEq)]
pub enum AuthError {
    InvalidCredentials,
    Expired,
    Http(u16),
    Malformed,
    Network,
}
pub struct AuthReply {
    pub authenticated: bool,
    pub status: String,
    pub expires_epoch_seconds: Option<i64>,
}
pub struct Account {
    profile: XtreamProfile,
    status: String,
    expiry: Option<i64>,
    authenticated_at: i64,
}
impl Account {
    pub fn accept(
        profile: XtreamProfile,
        reply: Result<AuthReply, AuthError>,
        now: i64,
    ) -> Result<Self, AuthError> {
        let reply = reply?;
        if !reply.authenticated {
            return Err(AuthError::InvalidCredentials);
        }
        if reply.status.eq_ignore_ascii_case("expired") {
            return Err(AuthError::Expired);
        }
        if !reply.status.eq_ignore_ascii_case("active") {
            return Err(AuthError::Malformed);
        }
        Ok(Self {
            profile,
            status: reply.status,
            expiry: reply.expires_epoch_seconds,
            authenticated_at: now,
        })
    }
    pub fn profile(&self) -> &XtreamProfile {
        &self.profile
    }
    pub fn status(&self) -> &str {
        &self.status
    }
    pub fn expiry(&self) -> Option<i64> {
        self.expiry
    }
    pub fn authenticated_at(&self) -> i64 {
        self.authenticated_at
    }
}
pub fn latest(accounts: &[Account]) -> Option<&Account> {
    accounts.iter().max_by_key(|a| a.authenticated_at)
}
/// L4 must complete both obligations, including on retry. Each account gets a unique
/// opaque ID from the caller. L5 enforces its uniqueness.
pub struct Removal<'a> {
    pub account_data: &'a AccountId,
    pub credential: &'a CredentialRef,
}
impl Account {
    pub fn removal(&self) -> Removal<'_> {
        Removal {
            account_data: &self.profile.id,
            credential: &self.profile.credentials,
        }
    }
}
