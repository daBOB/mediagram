//! `mediagram accept-login`: approve a QR login from the uploader's session.
//!
//! This is what a phone does when you scan a Telegram QR code. The player
//! backend needs its own authorization — two MTProto clients cannot share one
//! auth key — and asking Telegram for a login *code* is unreliable: it will
//! happily issue a fresh `phone_code_hash` while silently declining to
//! deliver the code, and offer no SMS fallback (`next_type` absent).
//!
//! A QR login sidesteps all of that. The new client exports a token, an
//! already-authorized session accepts it, and the new client is signed in.
//! No code, no SMS, nothing to type but the token.

use anyhow::{Context, Result, bail};
use base64::Engine;
use grammers_tl_types as tl;

use crate::config::Config;
use crate::telegram::client::Tg;

pub async fn run(cfg: &Config, token: &str) -> Result<()> {
    // The player prints the token base64; accept it with or without padding,
    // and in the URL-safe alphabet, since it travels through a terminal.
    let token = decode_token(token.trim())?;

    let tg = Tg::connect(cfg).await?;
    let result = tg
        .client
        .invoke(&tl::functions::auth::AcceptLoginToken { token })
        .await;
    tg.shutdown().await;

    match result {
        Ok(tl::enums::Authorization::Authorization(auth)) => {
            println!(
                "Approved. New session: {} on {}",
                auth.device_model, auth.platform
            );
            println!("The player should now finish logging in.");
            Ok(())
        }
        Err(err) => Err(err).context(
            "accepting the login token; it expires within about a minute, so re-run \
             the player's login to get a fresh one",
        ),
    }
}

/// Accepts standard or URL-safe base64, padded or not.
fn decode_token(text: &str) -> Result<Vec<u8>> {
    use base64::engine::general_purpose::{STANDARD, STANDARD_NO_PAD, URL_SAFE, URL_SAFE_NO_PAD};
    for engine in [
        &STANDARD as &dyn EngineLike,
        &STANDARD_NO_PAD,
        &URL_SAFE,
        &URL_SAFE_NO_PAD,
    ] {
        if let Some(bytes) = engine.try_decode(text) {
            return Ok(bytes);
        }
    }
    bail!("the token is not base64; paste exactly what the player printed")
}

/// Small shim so the four alphabets can be tried in one loop.
trait EngineLike {
    fn try_decode(&self, text: &str) -> Option<Vec<u8>>;
}

impl<T: Engine> EngineLike for T {
    fn try_decode(&self, text: &str) -> Option<Vec<u8>> {
        self.decode(text).ok()
    }
}
