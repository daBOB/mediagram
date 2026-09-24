//! Keeping a login response attached to the connection and attempt that sent it.

use std::future::Future;
use std::sync::Arc;

use grammers_mtsender::SenderPoolFatHandle;
use tokio::sync::MutexGuard;

use crate::api::{Core, CoreError, State};

use super::session;

pub(super) struct Attempt {
    id: Arc<()>,
    handle: SenderPoolFatHandle,
}

impl Attempt {
    pub(super) fn begin(state: &mut State) -> Result<Self, CoreError> {
        state.login_attempt = Some(Arc::new(()));
        state.pending_login = None;
        state.pending_password = None;
        Self::resume(state)
    }

    pub(super) fn resume(state: &State) -> Result<Self, CoreError> {
        let id = state.login_attempt.clone().ok_or_else(stale)?;
        let handle = state.client.as_ref().ok_or_else(stale)?.handle.clone();
        Ok(Self { id, handle })
    }

    pub(super) async fn complete<'a, T>(
        &self,
        core: &'a Core,
        response: impl Future<Output = T>,
    ) -> Result<(MutexGuard<'a, State>, T), CoreError> {
        let result = response.await;
        let state = core.state.lock().await;
        // A new request can reuse the connection, and sign-out can drop it
        // altogether. Check both before any result may restore a token or
        // persist a key; keep the lock until that result has been applied.
        let same_attempt = state
            .login_attempt
            .as_ref()
            .is_some_and(|id| Arc::ptr_eq(id, &self.id));
        let same_client = state
            .client
            .as_ref()
            .is_some_and(|client| Arc::ptr_eq(&client.handle.session, &self.handle.session));
        if !same_attempt || !same_client {
            return Err(stale());
        }
        Ok((state, result))
    }

    pub(super) fn persist(&self, core: &Core) -> Result<(), CoreError> {
        session::persist(&self.handle, &core.data_dir)
    }
}

fn stale() -> CoreError {
    CoreError::NotAuthorized("this sign-in attempt is no longer active".into())
}
