//! `mediagram whoami`: prints the signed-in user and the resolved channel.

use anyhow::{Context, Result};

use crate::config::Config;
use crate::telegram::client::Tg;

pub async fn run(cfg: &Config) -> Result<()> {
    let tg = Tg::connect(cfg).await?;

    let me = tg.client.get_me().await.context("fetching account info")?;
    println!("Account: {}", me.full_name());
    println!("Phone:   {}", mask_phone(me.phone()));
    println!("Channel: {}", tg.channel_title);

    tg.shutdown().await;
    Ok(())
}

/// Masks all but the last 2 digits of a phone number, so it is safe to print
/// (secrets must never be logged in full).
fn mask_phone(phone: Option<&str>) -> String {
    match phone {
        None => "unknown".to_string(),
        Some(p) if p.len() <= 2 => "*".repeat(p.len()),
        Some(p) => format!("{}{}", "*".repeat(p.len() - 2), &p[p.len() - 2..]),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn masks_all_but_last_two_digits() {
        assert_eq!(mask_phone(Some("15551234567")), "*********67");
    }

    #[test]
    fn masks_short_numbers_entirely() {
        assert_eq!(mask_phone(Some("1")), "*");
    }

    #[test]
    fn reports_unknown_when_absent() {
        assert_eq!(mask_phone(None), "unknown");
    }
}
