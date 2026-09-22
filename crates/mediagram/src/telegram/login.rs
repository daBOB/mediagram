//! Signing the uploader in: the phone number, login code and two-factor
//! password prompts, run only when the stored session is not authorized.

use anyhow::{Context, Result};
use dialoguer::{Input, Password};
use grammers_client::{Client, SignInError};

use crate::config::Config;

/// Ensures `client` is authorized, running the interactive phone/code/2FA
/// flow if not. Returns `true` if a fresh login just happened, `false` if
/// the session was already authorized.
pub async fn ensure_login(client: &Client, cfg: &Config) -> Result<bool> {
    if client
        .is_authorized()
        .await
        .context("checking Telegram authorization")?
    {
        return Ok(false);
    }
    login_interactive(client, cfg).await?;
    Ok(true)
}

/// Runs the phone number → login code → optional 2FA password prompts.
async fn login_interactive(client: &Client, cfg: &Config) -> Result<()> {
    let phone: String = Input::new()
        .with_prompt("Phone number (e.g. +15551234567)")
        .interact_text()
        .context("reading phone number")?;

    let token = client
        .request_login_code(&phone, &cfg.api_hash)
        .await
        .context("requesting login code")?;

    let code: String = Input::new()
        .with_prompt("Login code")
        .interact_text()
        .context("reading login code")?;

    match client.sign_in(&token, &code).await {
        Ok(_user) => Ok(()),
        Err(SignInError::PasswordRequired(password_token)) => {
            let hint = password_token.hint().unwrap_or("none");
            let password = Password::new()
                .with_prompt(format!("2FA password (hint: {hint})"))
                .interact()
                .context("reading 2FA password")?;
            client
                .check_password(password_token, password.into_bytes())
                .await
                .map(drop)
                .map_err(|err| anyhow::anyhow!("2FA check failed: {err}"))
        }
        Err(err) => Err(anyhow::anyhow!("sign-in failed: {err}")),
    }
}
