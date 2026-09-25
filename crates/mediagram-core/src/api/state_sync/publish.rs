//! Editing state documents and recovering missing messages or refused pins.

use grammers_mtsender::{InvocationError, SenderPoolFatHandle};

use crate::api::account::revoked::{checked_for, unless_revoked_for};
use crate::api::{Core, CoreError};
use crate::state::channel::state_caption;

/// The Telegram operations used by the production recovery flow.
pub(super) trait DocumentWriter {
    type Upload: Clone;

    async fn upload(&self, body: &str) -> Result<Self::Upload, std::io::Error>;
    async fn edit(
        &self,
        id: i32,
        caption: String,
        uploaded: Self::Upload,
    ) -> Result<(), InvocationError>;
    async fn send(&self, caption: String, uploaded: Self::Upload) -> Result<i32, InvocationError>;
    async fn pin(&self, id: i32) -> Result<(), InvocationError>;
    async fn delete(&self, id: i32) -> Result<(), InvocationError>;
}

pub(super) async fn put(
    core: &Core,
    owner: &SenderPoolFatHandle,
    writer: &impl DocumentWriter,
    body: String,
    message_id: Option<i32>,
) -> Result<i32, CoreError> {
    let caption = state_caption(device_of(&body).as_deref().unwrap_or(""));
    let uploaded = checked_for(core, owner, writer.upload(&body).await, |err| {
        CoreError::network("the state document could not be uploaded")(err)
    })
    .await?;
    if let Some(id) = message_id {
        match writer.edit(id, caption.clone(), uploaded.clone()).await {
            Ok(()) => return Ok(id),
            // Only a deleted message permits replacement; unrelated failures
            // must not accumulate duplicate state documents.
            Err(InvocationError::Rpc(rpc)) if rpc.name == "MESSAGE_ID_INVALID" => {}
            Err(err) => {
                let fallback = CoreError::network("the state document could not be edited")(&err);
                return Err(unless_revoked_for(core, owner, &err, fallback).await);
            }
        }
    }
    send_and_pin(core, owner, writer, caption, uploaded).await
}

async fn send_and_pin<W: DocumentWriter>(
    core: &Core,
    owner: &SenderPoolFatHandle,
    writer: &W,
    caption: String,
    uploaded: W::Upload,
) -> Result<i32, CoreError> {
    let id = checked_for(core, owner, writer.send(caption, uploaded).await, |err| {
        CoreError::network("the state document could not be sent")(err)
    })
    .await?;
    if let Err(err) = checked_for(core, owner, writer.pin(id).await, |err| {
        CoreError::network("the state document's pin was refused")(err)
    })
    .await
    {
        // Discovery uses pins: leaving an unpinned document would orphan it.
        let _ = writer.delete(id).await;
        return Err(err);
    }
    Ok(id)
}

/// Sync supplies a serialized state body; malformed input keeps the existing
/// harmless empty-device fallback used by the web adapter.
fn device_of(body: &str) -> Option<String> {
    let value: serde_json::Value = serde_json::from_str(body).ok()?;
    value.get("device")?.as_str().map(str::to_string)
}

#[cfg(test)]
#[path = "publish_tests.rs"]
mod tests;
