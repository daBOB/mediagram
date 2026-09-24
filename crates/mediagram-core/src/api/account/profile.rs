//! What the Settings screen shows about the connection, and signing out.

use std::time::Duration;

use crate::dto::AccountSummary;

use super::super::{Core, CoreError, State};
use super::{revoked, session};

/// How long a sign-out waits for Telegram before carrying on without it.
/// An offline phone must still forget its login; the key simply stays valid
/// at Telegram until it lapses or is revoked from another session.
const LOGOUT_WAIT: Duration = Duration::from_secs(10);

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// The datacentre this login lives on, read from the stored key — no
    /// network. `None` before any login.
    pub fn dc_id(&self) -> Option<i32> {
        session::load_auth_key(&self.data_dir).map(|(dc_id, _)| dc_id)
    }

    /// The signed-in account's name and username.
    pub async fn account(&self) -> Result<AccountSummary, CoreError> {
        let client = session::client(self).await;
        let me = revoked::checked(self, client.get_me().await, |err| {
            CoreError::network("could not ask Telegram who is signed in")(err)
        })
        .await?;
        let name = [me.first_name(), me.last_name()]
            .into_iter()
            .flatten()
            .filter(|part| !part.is_empty())
            .collect::<Vec<_>>()
            .join(" ");
        Ok(AccountSummary {
            name,
            username: me.username().map(str::to_string),
        })
    }

    /// Signs this device out: at Telegram first, so the login stops working
    /// everywhere rather than only here — deleting the key file alone left it
    /// listed and valid in the account's sessions — then the connection and
    /// the stored key. The key is removed even when Telegram cannot be
    /// reached. Safe to call when already signed out.
    pub async fn sign_out(&self) -> Result<(), CoreError> {
        if session::load_auth_key(&self.data_dir).is_some() {
            let client = session::client(self).await;
            match tokio::time::timeout(LOGOUT_WAIT, client.sign_out()).await {
                Ok(Ok(_)) => {}
                Ok(Err(err)) => tracing::warn!(%err, "logging out at Telegram"),
                Err(_) => tracing::warn!("logging out at Telegram timed out"),
            }
        }
        // Dropping the connection also ends a waiting update listener's
        // stream, which clears itself; its lock is never taken here, since a
        // listener may hold it for hours.
        *self.state.lock().await = State::default();
        session::forget_auth_key(&self.data_dir).map_err(CoreError::io("removing the stored login"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn core(dir: &std::path::Path) -> std::sync::Arc<Core> {
        Core::new(
            dir.display().to_string(),
            1,
            "test-hash".into(),
            "test-device".into(),
        )
    }

    #[test]
    fn no_login_has_no_datacentre() {
        let dir = tempfile::tempdir().unwrap();
        assert_eq!(core(dir.path()).dc_id(), None);
    }

    /// Signing out twice, or before ever signing in, is not an error and
    /// never opens a connection: there is no login to end.
    #[tokio::test]
    async fn signing_out_with_no_login_is_a_quiet_no_op() {
        let dir = tempfile::tempdir().unwrap();
        let core = core(dir.path());
        core.sign_out().await.unwrap();
        core.sign_out().await.unwrap();
        assert!(core.state.lock().await.client.is_none());
        assert!(!core.is_authorized());
    }
}
