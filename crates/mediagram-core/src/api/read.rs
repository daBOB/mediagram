//! Serving one read against a set's virtual file: the same range-planning
//! code `mediagram serve` uses, driving the Telegram transport directly
//! rather than through an HTTP response body.

use grammers_session::types::PeerId;
use tokio::sync::mpsc;

use crate::catalog as queries;
use crate::range::{self, ByteRange, PartSpan};
use crate::stream;

use super::{Core, CoreError, catalog, session};

/// Chunks buffered between the download and this call's own accumulation.
/// Matches [`crate::telegram`]'s buffer: enough to keep the download busy
/// without letting it run far ahead of a caller that stopped reading.
const BUFFERED_CHUNKS: usize = 4;

pub(super) async fn read(
    core: &Core,
    set_id: String,
    offset: u64,
    len: u32,
) -> Result<Vec<u8>, CoreError> {
    let locations = {
        let conn = catalog::open(core)?;
        queries::part_locations(&conn, &set_id)
            .map_err(|_| CoreError::Io("reading the catalog".into()))?
    };
    if locations.is_empty() {
        return Err(CoreError::NotFound("set not found".into()));
    }

    let spans: Vec<PartSpan> = locations.iter().map(|location| location.span).collect();
    let total = range::total_size(&spans);
    if total == 0 || offset >= total {
        return Err(CoreError::NotFound("read is past the end of the set".into()));
    }
    let end = offset
        .saturating_add(u64::from(len))
        .saturating_sub(1)
        .min(total - 1);
    let steps = range::plan_reads(&spans, &ByteRange { start: offset, end });

    let (api_id, _) = super::api_credentials()?;
    let client = {
        let mut state = core.state.lock().await;
        if state.client.is_none() {
            state.client = Some(session::connect(&core.data_dir, api_id));
        }
        state.client.as_ref().expect("just set").client.clone()
    };

    let mut out = Vec::with_capacity(len as usize);
    for step in steps {
        let location = locations
            .iter()
            .find(|location| location.span.idx == step.part_idx)
            .ok_or_else(|| CoreError::NotFound("set not found".into()))?;
        let channel = PeerId::from_bot_api_dialog_id(location.chat_id)
            .ok_or_else(|| CoreError::Io("the catalog names an invalid channel".into()))?
            .to_ambient_ref();
        let document = stream::part_document(&client, channel, location.message_id)
            .await
            .map_err(|_| CoreError::Network("the part could not be resolved".into()))?;

        // A separate task drains while `pump_step` runs, so a step whose
        // bytes outgrow the buffer cannot deadlock against a receiver that
        // only starts reading once the sender is done.
        let (tx, mut rx) = mpsc::channel(BUFFERED_CHUNKS);
        let pump_client = client.clone();
        let pump = tokio::spawn(async move { stream::pump_step(&pump_client, &document, &step, &tx).await });
        while let Some(chunk) = rx.recv().await {
            out.extend(chunk.map_err(|_| CoreError::Network("the download was interrupted".into()))?);
        }
        pump.await
            .map_err(|_| CoreError::Network("the download task did not finish cleanly".into()))?
            .map_err(|_| CoreError::Network("the download ended before it finished".into()))?;
    }
    Ok(out)
}
