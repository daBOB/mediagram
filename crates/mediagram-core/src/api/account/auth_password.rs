//! The two-factor password step, and keeping it open until it succeeds.
//!
//! A password is proved with SRP, and Telegram's SRP parameters — the
//! `srp_id` and `srp_B` inside a [`PasswordToken`] — are single-use: once a
//! proof has been sent against them, sending another answers
//! `SRP_ID_INVALID`, however right the password. grammers hands the spent
//! parameters back with a wrong-password verdict, so retrying with those
//! would refuse even the correct password. Every token here is therefore
//! used for exactly one check; the next check gets a fresh one from
//! `account.getPassword` on the same connection.
//!
//! A password step is only ever dropped by success or by starting over.
//! Whatever else a check comes to — a wrong password, a refusal, a network
//! fault, or a failure to fetch the next token — the step stays pending, so
//! the viewer can retype the password instead of being told no password
//! step is in progress.

use std::future::Future;

use grammers_client::SignInError;
use grammers_client::client::PasswordToken;
use grammers_client::tl;

use super::attempt::Attempt;
use super::{out_of_step, refused};
use crate::api::{Core, CoreError};

pub(in crate::api) enum PendingPassword {
    /// Parameters no proof has been sent against yet. Boxed because they
    /// dwarf the other variant.
    Ready(Box<PasswordToken>),
    /// The last parameters were spent, or may have been, and fetching their
    /// replacement did not succeed; the next check fetches first.
    Refetch,
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

    let client = &client;
    finish_password(
        core,
        attempt,
        pending,
        move |token| async move { client.check_password(token, password).await.map(drop) },
        || fetch_token(client),
    )
    .await
}

async fn fetch_token(client: &grammers_client::Client) -> Result<PasswordToken, CoreError> {
    let password: tl::types::account::Password = client
        .invoke(&tl::functions::account::GetPassword {})
        .await
        .map_err(|err| refused(&err))?
        .into();
    // grammers unwraps these while building the proof, so parameters
    // without them would panic across the FFI boundary instead of failing.
    if password.current_algo.is_none() || password.srp_b.is_none() || password.srp_id.is_none() {
        return Err(CoreError::NotAuthorized(
            "this account no longer asks for a password; start the sign-in again".into(),
        ));
    }
    Ok(PasswordToken::new(password))
}

/// Checks one password and applies the verdict, but only if the attempt
/// that sent it is still the current one. `check` and `fetch` are the two
/// Telegram requests, passed in so the step can be exercised without one.
pub(super) async fn finish_password<C, F>(
    core: &Core,
    attempt: Attempt,
    pending: PendingPassword,
    check: impl FnOnce(PasswordToken) -> C,
    fetch: impl Fn() -> F,
) -> Result<(), CoreError>
where
    C: Future<Output = Result<(), SignInError>>,
    F: Future<Output = Result<PasswordToken, CoreError>>,
{
    let (mut state, verdict) = attempt
        .complete(core, answer(pending, check, fetch))
        .await?;
    match verdict {
        Ok(()) => attempt.persist(core),
        Err((err, next)) => {
            state.pending_password = Some(next);
            Err(err)
        }
    }
}

/// Everything one check says to Telegram, finished before the state lock is
/// retaken: the verdict, and on failure the step to keep pending.
async fn answer<C, F>(
    pending: PendingPassword,
    check: impl FnOnce(PasswordToken) -> C,
    fetch: impl Fn() -> F,
) -> Result<(), (CoreError, PendingPassword)>
where
    C: Future<Output = Result<(), SignInError>>,
    F: Future<Output = Result<PasswordToken, CoreError>>,
{
    let token = match pending {
        PendingPassword::Ready(token) => *token,
        PendingPassword::Refetch => fetch()
            .await
            .map_err(|err| (err, PendingPassword::Refetch))?,
    };
    let err = match check(token).await {
        Ok(()) => return Ok(()),
        // The token inside is the one just spent; see the module comment.
        Err(SignInError::InvalidPassword(_spent)) => {
            CoreError::NotAuthorized("the password was not accepted".into())
        }
        Err(SignInError::Other(err)) => refused(&err),
        Err(unexpected) => out_of_step(unexpected),
    };
    // Whether a proof that met a network fault reached Telegram is unknown,
    // so its parameters are treated as spent; fetching their replacement
    // over a connection that just failed is left to the next check.
    if matches!(err, CoreError::Network(_)) {
        return Err((err, PendingPassword::Refetch));
    }
    let next = match fetch().await {
        Ok(token) => PendingPassword::Ready(Box::new(token)),
        Err(fetch_err) => {
            tracing::warn!(%fetch_err, "could not fetch fresh password parameters");
            PendingPassword::Refetch
        }
    };
    Err((err, next))
}
