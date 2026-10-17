//! A viewer's report of a broken video, to the account's own Saved Messages.
//!
//! Scheduled rather than sent: every screen is signed in to the same
//! account, and Telegram notifies an account of its own message only when it
//! is a reminder — a scheduled message in Saved Messages. 60 s, as the web's
//! `REPORT_DELAY_SECONDS`.

use std::time::{Duration, SystemTime};

use grammers_client::message::InputMessage;

use super::account::revoked::checked_for;
use super::account::session;
use super::{Core, CoreError};

const DELAY: Duration = Duration::from_secs(60);
const FAILED: &str = "the report could not be sent";

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// Schedules `text`, as written, into Saved Messages a minute ahead.
    pub async fn report_send(&self, text: String) -> Result<(), CoreError> {
        let (client, owner) = session::connection(self).await;
        let me = checked_for(self, &owner, client.get_me().await, |err| {
            CoreError::network(FAILED)(err)
        })
        .await?;
        let peer = match me.to_ref().await {
            Ok(Some(peer)) => peer,
            Ok(None) => return Err(CoreError::Network(FAILED.into())),
            Err(err) => return Err(CoreError::network(FAILED)(err)),
        };
        let message = InputMessage::new()
            .text(text)
            .schedule_date(Some(SystemTime::now() + DELAY));
        let sent = client.send_message(peer, message).await.map(|_| ());
        checked_for(self, &owner, sent, |err| CoreError::network(FAILED)(err)).await
    }
}
