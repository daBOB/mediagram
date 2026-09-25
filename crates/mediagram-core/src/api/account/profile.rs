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
        account_with(self, |client| async move { client.get_me().await }).await
    }

    /// Signs this device out: at Telegram first, so the login stops working
    /// everywhere rather than only here — deleting the key file alone left it
    /// listed and valid in the account's sessions — then the connection and
    /// the stored key. The key is removed even when Telegram cannot be
    /// reached. Safe to call when already signed out.
    pub async fn sign_out(&self) -> Result<(), CoreError> {
        sign_out_with(
            self,
            |client| async move { client.sign_out().await.map(|_| ()) },
            session::forget_auth_key,
        )
        .await
    }
}

async fn sign_out_with<F>(
    core: &Core,
    logout: impl FnOnce(grammers_client::Client) -> F,
    forget: impl FnOnce(&std::path::Path) -> std::io::Result<()>,
) -> Result<(), CoreError>
where
    F: std::future::Future<Output = Result<(), grammers_mtsender::InvocationError>>,
{
    if session::load_auth_key(&core.data_dir).is_some() {
        let client = session::client(core).await;
        match tokio::time::timeout(LOGOUT_WAIT, logout(client)).await {
            Ok(Ok(_)) => {}
            Ok(Err(err)) => tracing::warn!(%err, "logging out at Telegram"),
            Err(_) => tracing::warn!("logging out at Telegram timed out"),
        }
    }
    // Dropping the connection also ends a waiting update listener's
    // stream, which clears itself; its lock is never taken here, since a
    // listener may hold it for hours.
    let mut state = core.state.lock().await;
    *state = State::default();
    // A lazy connection must not reload the outgoing key while it still
    // exists on disk, even when Telegram refused or timed out the logout.
    forget(&core.data_dir).map_err(CoreError::io("removing the stored login"))
}

async fn account_with<F>(
    core: &Core,
    request: impl FnOnce(grammers_client::Client) -> F,
) -> Result<AccountSummary, CoreError>
where
    F: std::future::Future<
            Output = Result<grammers_client::peer::User, grammers_mtsender::InvocationError>,
        >,
{
    let (client, owner) = session::connection(core).await;
    let me = revoked::checked_for(core, &owner, request(client).await, |err| {
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

#[cfg(test)]
#[path = "profile_tests.rs"]
mod tests;
