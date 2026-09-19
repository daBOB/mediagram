//! The interactive login flow: request a code, sign in, and — for accounts
//! with two-factor authentication on — check the password.
//!
//! Every step reuses the one connection [`request_code`] opens: Telegram
//! correlates a login attempt to that connection's session, not to a phone
//! number alone, so a later step reconnecting would lose it. This client
//! logs itself in; it never accepts an auth key exported from elsewhere; a
//! measured case (`commands::export_session`, on the Linux side of this
//! project) is two clients sharing one key breaking each other until a
//! restart.

use grammers_client::SignInError;
use grammers_client::client::{LoginToken, PasswordToken};

use super::{AuthOutcome, Core, CoreError, session};

pub(super) struct PendingLogin {
    id: String,
    token: LoginToken,
}

pub(super) struct PendingPassword(PasswordToken);

/// An opaque id, not a secret: it only lets `sign_in` recognise which
/// in-flight attempt a caller means, the way a form's hidden field would.
fn opaque_id() -> String {
    let mut bytes = [0u8; 16];
    getrandom::fill(&mut bytes).expect("the OS random source is available");
    hex::encode(bytes)
}

pub(super) async fn request_code(core: &Core, phone: String) -> Result<String, CoreError> {
    let (api_id, api_hash) = super::api_credentials()?;
    let mut state = core.state.lock().await;
    if state.client.is_none() {
        state.client = Some(session::connect(&core.data_dir, api_id));
    }
    let client = state.client.as_ref().expect("just set").client.clone();
    drop(state);

    let token = client
        .request_login_code(&phone, &api_hash)
        .await
        .map_err(|err| CoreError::Network(err.to_string()))?;

    let id = opaque_id();
    let mut state = core.state.lock().await;
    state.pending_login = Some(PendingLogin {
        id: id.clone(),
        token,
    });
    Ok(id)
}

pub(super) async fn sign_in(
    core: &Core,
    token: String,
    code: String,
) -> Result<AuthOutcome, CoreError> {
    let mut state = core.state.lock().await;
    let pending = state
        .pending_login
        .take()
        .filter(|p| p.id == token)
        .ok_or_else(|| CoreError::NotAuthorized("no matching login is in progress".into()))?;
    let handle = state
        .client
        .as_ref()
        .ok_or_else(|| CoreError::NotAuthorized("no login is in progress".into()))?;
    let client = handle.client.clone();
    drop(state);

    match client.sign_in(&pending.token, &code).await {
        Ok(_user) => {
            let state = core.state.lock().await;
            let handle = state.client.as_ref().expect("connected above");
            session::persist(&handle.handle, &core.data_dir)?;
            Ok(AuthOutcome::Done)
        }
        Err(SignInError::PasswordRequired(password_token)) => {
            core.state.lock().await.pending_password = Some(PendingPassword(password_token));
            Ok(AuthOutcome::PasswordNeeded)
        }
        Err(err) => Err(CoreError::Network(err.to_string())),
    }
}

pub(super) async fn check_password(core: &Core, password: String) -> Result<(), CoreError> {
    let mut state = core.state.lock().await;
    let pending = state
        .pending_password
        .take()
        .ok_or_else(|| CoreError::NotAuthorized("no password step is in progress".into()))?;
    let handle = state
        .client
        .as_ref()
        .ok_or_else(|| CoreError::NotAuthorized("no login is in progress".into()))?;
    let client = handle.client.clone();
    drop(state);

    client
        .check_password(pending.0, password.into_bytes())
        .await
        .map_err(|err| CoreError::Network(err.to_string()))?;

    let state = core.state.lock().await;
    let handle = state.client.as_ref().expect("connected above");
    session::persist(&handle.handle, &core.data_dir)
}
