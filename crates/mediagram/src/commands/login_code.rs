//! `mediagram login-code`: the login code Telegram just sent, read from the
//! account's own message history.
//!
//! Telegram delivers a login code in-app whenever the account is signed in
//! somewhere, and "in-app" here means the uploader: a headless client with no
//! interface to show it. The code is nonetheless an ordinary message from the
//! Telegram service account, so the session that cannot display it can still
//! be asked for it.
//!
//! Used when logging the player backend in. That login creates its own auth
//! key, so this reads over a different one and the two do not collide.

use anyhow::{Context, Result};
use grammers_session::types::PeerId;

use crate::config::Config;
use crate::telegram::client::Tg;

/// Telegram's service account, the sender of login codes and warnings.
const TELEGRAM_SERVICE_USER: i64 = 777000;

/// How many recent service messages to show.
const RECENT: usize = 5;

pub async fn run(cfg: &Config) -> Result<()> {
    let tg = Tg::connect(cfg).await?;

    // Ambient authority: the service account needs no access hash.
    let peer = PeerId::user_unchecked(TELEGRAM_SERVICE_USER).to_ambient_ref();

    let mut messages = tg.client.iter_messages(peer);
    let mut shown = 0;
    println!("Recent messages from Telegram:\n");
    while shown < RECENT {
        let Some(message) = messages
            .next()
            .await
            .context("reading the Telegram service chat")?
        else {
            break;
        };
        let text = message.text().replace('\n', " ");
        // The codes are what anyone runs this for; the rest is context.
        let marker = if text.contains("Login code") {
            ">>"
        } else {
            "  "
        };
        println!("{marker} {}  {}", message.date(), truncate(&text, 400));
        shown += 1;
    }
    if shown == 0 {
        println!("  (none)");
    }
    println!("\nA login code expires within minutes; request a fresh one if this is stale.");

    tg.shutdown().await;
    Ok(())
}

fn truncate(text: &str, limit: usize) -> String {
    if text.chars().count() <= limit {
        return text.to_string();
    }
    text.chars().take(limit).collect::<String>() + "…"
}
