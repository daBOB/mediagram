//! Active sessions: which devices are signed in through this app, and
//! revoking one remotely.
//!
//! Scoped to this app's `api_id` (plus the current row, which carries none
//! of its own to compare): the account's official Telegram apps are not
//! this app's business, and revoking one from a media player would be out
//! of proportion. `shape`/`revoke_error` are pure and pinned to
//! `tests/shared_authorizations_fixture.rs`, which `web/src/settings/sessions.ts`
//! reads too — the two surfaces must agree on what a session list says.

use grammers_client::tl;
use grammers_mtsender::InvocationError;

use crate::dto::SessionSummary;

use super::account::{revoked, session};
use super::{Core, CoreError};

/// One row as read off the wire, before it is scoped to this app.
pub(crate) struct RawAuthorization {
    pub current: bool,
    pub unconfirmed: bool,
    pub hash: String,
    pub device_model: String,
    pub platform: String,
    pub api_id: i32,
    pub app_name: String,
    pub app_version: String,
    pub date_created: i64,
    pub date_active: i64,
    pub country: String,
    pub region: String,
}

fn location_of(row: &RawAuthorization) -> Option<String> {
    let country = row.country.trim();
    if country.is_empty() {
        return None;
    }
    let region = row.region.trim();
    Some(if region.is_empty() {
        country.to_string()
    } else {
        format!("{country}, {region}")
    })
}

/// This app's sessions, plus the current one, newest active first.
pub(crate) fn shape(raw: &[RawAuthorization], api_id: i32) -> Vec<SessionSummary> {
    let mut rows: Vec<SessionSummary> = raw
        .iter()
        .filter(|row| row.current || row.api_id == api_id)
        .map(|row| SessionSummary {
            id: row.hash.clone(),
            device: row.device_model.clone(),
            platform: row.platform.clone(),
            app: row.app_name.clone(),
            app_version: row.app_version.clone(),
            location: location_of(row),
            last_active: row.date_active,
            created: row.date_created,
            current: row.current,
            unconfirmed: row.unconfirmed,
        })
        .collect();
    rows.sort_by_key(|row| std::cmp::Reverse(row.last_active));
    rows
}

const FRESH_SESSION_GUARD: &str = "This device signed in less than a day ago; \
    Telegram allows removing other sessions after 24 hours.";

/// What a failed revoke becomes. Telegram's 24-hour guard on a fresh
/// session is named for the person reading it, not echoed as an RPC code.
pub(crate) fn revoke_error(err: &InvocationError) -> CoreError {
    match err {
        InvocationError::Rpc(rpc) if rpc.name == "FRESH_RESET_AUTHORISATION_FORBIDDEN" => {
            CoreError::NotAuthorized(FRESH_SESSION_GUARD.into())
        }
        InvocationError::Rpc(rpc) if (400..500).contains(&rpc.code) && rpc.code != 420 => {
            tracing::warn!(%err, "session revoke refused");
            CoreError::NotAuthorized(format!("Telegram refused ({})", rpc.name))
        }
        _ => CoreError::network("could not reach Telegram")(err),
    }
}

/// Whether `err` says the session was already gone — revoking it again is
/// not a failure, since it is gone either way.
fn already_gone(err: &InvocationError) -> bool {
    matches!(err, InvocationError::Rpc(rpc) if rpc.name == "HASH_INVALID")
}

fn to_raw(entry: tl::enums::Authorization) -> RawAuthorization {
    let tl::enums::Authorization::Authorization(a) = entry;
    RawAuthorization {
        current: a.current,
        unconfirmed: a.unconfirmed,
        hash: a.hash.to_string(),
        device_model: a.device_model,
        platform: a.platform,
        api_id: a.api_id,
        app_name: a.app_name,
        app_version: a.app_version,
        date_created: a.date_created as i64,
        date_active: a.date_active as i64,
        country: a.country,
        region: a.region,
    }
}

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// This app's sessions: the ones sharing its `api_id`, plus the current one.
    pub async fn sessions(&self) -> Result<Vec<SessionSummary>, CoreError> {
        let (client, owner) = session::connection(self).await;
        let answer = client.invoke(&tl::functions::account::GetAuthorizations {}).await;
        let authorizations = revoked::checked_for(self, &owner, answer, |err| {
            CoreError::network("could not list sessions")(err)
        })
        .await?;
        let tl::enums::account::Authorizations::Authorizations(list) = authorizations;
        let raw: Vec<RawAuthorization> = list.authorizations.into_iter().map(to_raw).collect();
        Ok(shape(&raw, self.api_id))
    }

    /// Revokes `id`. A session already gone counts as success.
    pub async fn revoke_session(&self, id: String) -> Result<(), CoreError> {
        if id == "0" {
            return Err(CoreError::NotAuthorized(
                "sign out to end this device's own session".into(),
            ));
        }
        let hash: i64 = id
            .parse()
            .map_err(|_| CoreError::NotAuthorized("not a session id".into()))?;
        let (client, owner) = session::connection(self).await;
        match client
            .invoke(&tl::functions::account::ResetAuthorization { hash })
            .await
        {
            Ok(_) => Ok(()),
            Err(err) if already_gone(&err) => Ok(()),
            Err(err) => {
                let fallback = revoke_error(&err);
                Err(revoked::unless_revoked_for(self, &owner, &err, fallback).await)
            }
        }
    }
}

#[cfg(test)]
#[path = "sessions_tests.rs"]
mod tests;
