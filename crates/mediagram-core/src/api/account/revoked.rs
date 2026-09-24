//! Noticing that Telegram no longer honours this device's login.
//!
//! A login can end away from this device: revoked from another client's
//! active sessions, or the account deleted. From then on Telegram refuses
//! every call with a 401 (`AUTH_KEY_UNREGISTERED`, `SESSION_REVOKED`, ...).
//! Left alone, the stored key keeps `is_authorized` true and each refusal
//! reads as a network fault that retrying would fix. Instead the key is
//! forgotten and the caller told `NotAuthorized`, so the app asks for a fresh
//! login rather than retrying one that can never succeed.

use std::error::Error;

use grammers_mtsender::InvocationError;

use crate::api::{Core, CoreError, State};

use super::session;

const SIGNED_OUT: &str = "this device was signed out of Telegram";

/// Whether `err`, or anything it wraps, is Telegram refusing the login
/// itself rather than the one call.
pub(crate) fn is_revoked(err: &(dyn Error + 'static)) -> bool {
    let mut cause = Some(err);
    while let Some(err) = cause {
        if let Some(InvocationError::Rpc(rpc)) = err.downcast_ref::<InvocationError>()
            && rpc.code == 401
        {
            return true;
        }
        cause = err.source();
    }
    false
}

/// `otherwise`, unless `cause` says the login is gone — then the stored key
/// and the live connection are dropped, and the answer is `NotAuthorized`.
pub(in crate::api) async fn unless_revoked(
    core: &Core,
    cause: &(dyn Error + Send + Sync + 'static),
    otherwise: CoreError,
) -> CoreError {
    if !is_revoked(cause) {
        return otherwise;
    }
    // Everything the state holds belongs to the login that ended: the
    // connection, any half-finished sign-in, the documents it resolved.
    *core.state.lock().await = State::default();
    if let Err(err) = session::forget_auth_key(&core.data_dir) {
        tracing::warn!(%err, "removing the revoked session");
    }
    CoreError::NotAuthorized(SIGNED_OUT.into())
}

/// A Telegram call's result, a refusal mapped by `otherwise` unless it is
/// the login itself that was refused.
pub(in crate::api) async fn checked<T>(
    core: &Core,
    result: Result<T, InvocationError>,
    otherwise: impl FnOnce(&InvocationError) -> CoreError,
) -> Result<T, CoreError> {
    match result {
        Ok(value) => Ok(value),
        Err(err) => {
            let fallback = otherwise(&err);
            Err(unless_revoked(core, &err, fallback).await)
        }
    }
}

#[cfg(test)]
mod tests {
    use grammers_mtsender::RpcError;

    use super::*;

    fn rpc(code: i32, message: &str) -> InvocationError {
        InvocationError::Rpc(RpcError::from(grammers_tl_types::types::RpcError {
            error_code: code,
            error_message: message.into(),
        }))
    }

    #[test]
    fn a_401_is_a_revoked_login_even_wrapped() {
        assert!(is_revoked(&rpc(401, "SESSION_REVOKED")));
        let wrapped =
            anyhow::Error::new(rpc(401, "AUTH_KEY_UNREGISTERED")).context("downloading a chunk");
        assert!(is_revoked(wrapped.as_ref()));
    }

    #[test]
    fn other_refusals_are_not() {
        assert!(!is_revoked(&rpc(400, "CHANNEL_INVALID")));
        assert!(!is_revoked(&rpc(420, "FLOOD_WAIT_30")));
        let io = anyhow::anyhow!("connection reset").context("downloading a chunk");
        assert!(!is_revoked(io.as_ref()));
    }
}
