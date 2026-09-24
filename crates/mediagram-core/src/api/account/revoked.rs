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
use std::sync::Arc;

use grammers_mtsender::{InvocationError, SenderPoolFatHandle};

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
        // io::Error::source skips the wrapped error itself; uploads wrap the
        // InvocationError with io::Error::other, so inspect get_ref first.
        cause = err
            .downcast_ref::<std::io::Error>()
            .and_then(|io| io.get_ref().map(|inner| inner as &(dyn Error + 'static)))
            .or_else(|| err.source());
    }
    false
}

/// A refusal belongs to the connection that made the request. A newer
/// login retains both its live state and its persisted key.
pub(in crate::api) async fn unless_revoked_for(
    core: &Core,
    expected: &SenderPoolFatHandle,
    cause: &(dyn Error + Send + Sync + 'static),
    otherwise: CoreError,
) -> CoreError {
    if !is_revoked(cause) {
        return otherwise;
    }
    // Everything the state holds belongs to the login that ended: the
    // connection, any half-finished sign-in, the documents it resolved.
    let failed = {
        let mut state = core.state.lock().await;
        if !state
            .client
            .as_ref()
            .is_some_and(|live| Arc::ptr_eq(&live.handle.session, &expected.session))
        {
            return otherwise;
        }
        let failed = state.client.as_ref().map(|live| live.handle.clone());
        *state = State::default();
        // Keep connection creation locked until it can no longer reload the
        // revoked key from disk.
        if let Err(err) = session::forget_auth_key(&core.data_dir) {
            tracing::warn!(%err, "removing the revoked session");
        }
        failed
    };
    if let Some(failed) = failed {
        clear_idle_listener(core, &failed);
    }
    CoreError::NotAuthorized(SIGNED_OUT.into())
}

/// A waiter clears its own slot when the stopped pool wakes it. If a new
/// listener opened after state reset, leave that replacement alone.
pub(in crate::api) fn clear_idle_listener(core: &Core, failed: &SenderPoolFatHandle) {
    if let Ok(mut listener) = core.events.try_lock()
        && listener
            .as_ref()
            .is_some_and(|live| Arc::ptr_eq(&live.handle.session, &failed.session))
    {
        *listener = None;
    }
}

pub(in crate::api) async fn checked_for<T, E: Error + Send + Sync + 'static>(
    core: &Core,
    expected: &SenderPoolFatHandle,
    result: Result<T, E>,
    otherwise: impl FnOnce(&E) -> CoreError,
) -> Result<T, CoreError> {
    match result {
        Ok(value) => Ok(value),
        Err(err) => {
            let fallback = otherwise(&err);
            Err(unless_revoked_for(core, expected, &err, fallback).await)
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
    fn an_upload_io_wrapper_keeps_its_revocation_meaning() {
        assert!(is_revoked(&std::io::Error::other(rpc(
            401,
            "SESSION_REVOKED"
        ))));
    }

    #[test]
    fn other_refusals_are_not() {
        assert!(!is_revoked(&rpc(400, "CHANNEL_INVALID")));
        assert!(!is_revoked(&rpc(420, "FLOOD_WAIT_30")));
        let io = anyhow::anyhow!("connection reset").context("downloading a chunk");
        assert!(!is_revoked(io.as_ref()));
    }
}
