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
use grammers_mtsender::InvocationError;

#[path = "auth_attempt.rs"]
mod attempt;
use attempt::Attempt;

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
        state.client = Some(session::connect(core));
    }
    let attempt = Attempt::begin(&mut state)?;
    let client = state.client.as_ref().expect("just set").client.clone();
    drop(state);

    let (mut state, result) = attempt
        .complete(core, client.request_login_code(&phone, &core.api_hash))
        .await?;
    let token = result.map_err(|err| refused(&err))?;

    let id = opaque_id();
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
    let attempt = Attempt::resume(&state)?;
    let pending = state.pending_login.take().expect("checked above");
    let handle = state
        .client
        .as_ref()
        .ok_or_else(|| CoreError::NotAuthorized("no login is in progress".into()))?;
    let client = handle.client.clone();
    drop(state);

    let (mut state, result) = attempt
        .complete(core, client.sign_in(&pending.token, &code))
        .await?;
    match result {
        Ok(_user) => {
            attempt.persist(core)?;
            Ok(AuthOutcome::Done)
        }
        Err(SignInError::PasswordRequired(password_token)) => {
            state.pending_password = Some(PendingPassword(password_token));
            Ok(AuthOutcome::PasswordNeeded)
        }
        Err(SignInError::InvalidCode) => {
            // The token's `phone`/`phone_code_hash` are still good for
            // another attempt; only the code itself was wrong.
            state.pending_login = Some(pending);
            Err(CoreError::NotAuthorized("the code was not accepted".into()))
        }
        Err(SignInError::SignUpRequired) => Err(CoreError::NotAuthorized(NO_ACCOUNT.into())),
        Err(SignInError::Other(err)) => Err(refused(&err)),
        Err(unexpected @ SignInError::InvalidPassword(_)) => Err(out_of_step(unexpected)),
    }
}

pub(in crate::api) async fn check_password(core: &Core, password: String) -> Result<(), CoreError> {
    let mut state = core.state.lock().await;
    let attempt = Attempt::resume(&state)?;
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

    finish_password(
        core,
        attempt,
        client.check_password(pending.0, password.into_bytes()),
    )
    .await
}

async fn finish_password<T>(
    core: &Core,
    attempt: Attempt,
    response: impl std::future::Future<Output = Result<T, SignInError>>,
) -> Result<(), CoreError> {
    let (mut state, result) = attempt.complete(core, response).await?;
    match result {
        Ok(_user) => attempt.persist(core),
        Err(SignInError::InvalidPassword(retry_token)) => {
            // Telegram hands the same password step back specifically so a
            // wrong entry can be retried without a fresh login.
            state.pending_password = Some(PendingPassword(retry_token));
            Err(CoreError::NotAuthorized(
                "the password was not accepted".into(),
            ))
        }
        Err(SignInError::Other(err)) => Err(refused(&err)),
        Err(unexpected) => Err(out_of_step(unexpected)),
    }
}

const NO_ACCOUNT: &str =
    "this number has no Telegram account yet; create one in the Telegram app first";

/// What a failed login step becomes. Telegram refusing what was typed — a
/// malformed or banned number, an expired code — is `NotAuthorized`, named by
/// Telegram's own reason, which carries no account detail. A flood wait or
/// anything that never reached an answer is a network fault, cause logged.
fn refused(err: &InvocationError) -> CoreError {
    match err {
        InvocationError::Rpc(rpc) if (400..500).contains(&rpc.code) && rpc.code != 420 => {
            tracing::warn!(%err, "login step refused");
            CoreError::NotAuthorized(format!("Telegram refused the sign-in ({})", rpc.name))
        }
        _ => CoreError::network("the login could not reach Telegram")(err),
    }
}

/// An answer Telegram should not give at this step, such as a password
/// verdict to a code. Logged, since it means the flow and grammers disagree.
fn out_of_step(err: SignInError) -> CoreError {
    tracing::warn!(%err, "unexpected answer to a login step");
    CoreError::NotAuthorized("sign-in was rejected".into())
}

#[cfg(test)]
#[path = "auth_tests.rs"]
mod tests;
