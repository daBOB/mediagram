//! The interactive login flow: request a code, sign in, and — for accounts
//! with two-factor authentication on — check the password.
//!
//! Every step reuses the one connection [`request_code`] opens: Telegram
//! correlates a login attempt to that connection's session, not to a phone
//! number alone, so a later step reconnecting would lose it. This client
//! logs itself in and never accepts an auth key exported from elsewhere:
//! two clients sharing one key break each other until a restart.
//!
//! A wrong code or password must never cost the flood-wait budget of a
//! fresh `request_code`: Telegram still accepts another attempt against the
//! same login token, so every rejection below hands the pending state back
//! rather than discarding it, and a caller can just ask the person to
//! retype what they typed.

use grammers_client::SignInError;
use grammers_client::client::{LoginToken, PasswordToken};

use super::session;
use crate::api::{AuthOutcome, Core, CoreError};

pub(in crate::api) struct PendingLogin {
    id: String,
    token: LoginToken,
}

pub(in crate::api) struct PendingPassword(PasswordToken);

/// An opaque id, not a secret: it only lets `sign_in` recognise which
/// in-flight attempt a caller means, the way a form's hidden field would.
fn opaque_id() -> String {
    let mut bytes = [0u8; 16];
    getrandom::fill(&mut bytes).expect("the OS random source is available");
    hex::encode(bytes)
}

pub(in crate::api) async fn request_code(core: &Core, phone: String) -> Result<String, CoreError> {
    let mut state = core.state.lock().await;
    if state.client.is_none() {
        state.client = Some(session::connect(&core.data_dir, core.api_id));
    }
    let client = state.client.as_ref().expect("just set").client.clone();
    drop(state);

    let token = client
        .request_login_code(&phone, &core.api_hash)
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

pub(in crate::api) async fn sign_in(
    core: &Core,
    token: String,
    code: String,
) -> Result<AuthOutcome, CoreError> {
    let mut state = core.state.lock().await;
    // Checked before taking: an id that does not match must leave a genuine
    // pending login untouched, so a caller who mistypes the token can still
    // retry with the right one instead of having to start over.
    if !state.pending_login.as_ref().is_some_and(|p| p.id == token) {
        return Err(CoreError::NotAuthorized(
            "no matching login is in progress".into(),
        ));
    }
    let pending = state.pending_login.take().expect("checked above");
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
        Err(SignInError::InvalidCode) => {
            // The token's `phone`/`phone_code_hash` are still good for
            // another attempt; only the code itself was wrong.
            core.state.lock().await.pending_login = Some(pending);
            Err(CoreError::NotAuthorized("the code was not accepted".into()))
        }
        Err(SignInError::Other(err)) => Err(CoreError::Network(err.to_string())),
        Err(_other) => Err(CoreError::NotAuthorized("sign-in was rejected".into())),
    }
}

pub(in crate::api) async fn check_password(core: &Core, password: String) -> Result<(), CoreError> {
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

    match client.check_password(pending.0, password.into_bytes()).await {
        Ok(_user) => {
            let state = core.state.lock().await;
            let handle = state.client.as_ref().expect("connected above");
            session::persist(&handle.handle, &core.data_dir)
        }
        Err(SignInError::InvalidPassword(retry_token)) => {
            // Telegram hands the same password step back specifically so a
            // wrong entry can be retried without a fresh login.
            core.state.lock().await.pending_password = Some(PendingPassword(retry_token));
            Err(CoreError::NotAuthorized(
                "the password was not accepted".into(),
            ))
        }
        Err(SignInError::Other(err)) => Err(CoreError::Network(err.to_string())),
        Err(_other) => Err(CoreError::NotAuthorized("password check was rejected".into())),
    }
}
