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
    // A zero-length request is trivially satisfied, and must return before
    // `end` is computed: `end = offset - 1` below is only ever valid because
    // `len >= 1` guarantees `offset + len - 1 >= offset`. At `len == 0` that
    // guarantee is gone, `end` can land one below `offset`, and subtracting
    // `offset` back out of it underflows a `u64`.
    if len == 0 {
        return Ok(Vec::new());
    }
    let end = offset
        .saturating_add(u64::from(len))
        .saturating_sub(1)
        .min(total - 1);
    let steps = range::plan_reads(&spans, &ByteRange { start: offset, end });

    let client = {
        let mut state = core.state.lock().await;
        if state.client.is_none() {
            state.client = Some(session::connect(&core.data_dir, core.api_id));
        }
        state.client.as_ref().expect("just set").client.clone()
    };

    // Clamped to what this read can actually return, not to the caller's
    // raw `len`: an out-of-range `UInt` from Kotlin must not become an
    // attempt to reserve up to 4 GiB before a single byte is read.
    let capacity = usize::try_from(end - offset + 1).unwrap_or(usize::MAX);
    let mut out = Vec::with_capacity(capacity);
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

#[cfg(test)]
mod tests {
    use rusqlite::Connection;

    use super::*;

    /// A minimal catalog with one done part, just enough for `read` to plan
    /// against. `read` never queries `sets`, only `parts`, so that table is
    /// left empty.
    fn core_with_one_part(dir: &std::path::Path, set_id: &str, part_len: u64) -> std::sync::Arc<Core> {
        let current = dir.join("catalog").join("current");
        std::fs::create_dir_all(&current).unwrap();
        let conn = Connection::open(current.join("library.db")).unwrap();
        for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
            conn.execute(stmt, []).unwrap();
        }
        conn.execute(
            "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
             VALUES (?1, 'movie', 'mkv', ?2, 1, 'complete', 0, 1)",
            rusqlite::params![set_id, part_len as i64],
        )
        .unwrap();
        conn.execute(
            "INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, status)
             VALUES (?1, 0, 0, ?2, -1001, 100, 'done')",
            rusqlite::params![set_id, part_len as i64],
        )
        .unwrap();
        Core::new(dir.display().to_string(), 1, "test-hash".into())
    }

    /// `len == 0` at an in-range, nonzero `offset` is the exact shape that
    /// underflowed `end - offset` before the early return above existed:
    /// `end` computed from a zero-length range landed one below `offset`.
    /// Run under both `cargo test` (a debug-mode underflow panics) and
    /// `cargo test --release` (it would otherwise wrap to a huge capacity).
    #[tokio::test]
    async fn a_zero_length_read_at_a_nonzero_offset_returns_empty() {
        let dir = tempfile::tempdir().unwrap();
        let core = core_with_one_part(dir.path(), "01SET0000000000000000001", 1000);

        let bytes = read(&core, "01SET0000000000000000001".into(), 500, 0)
            .await
            .unwrap();

        assert!(bytes.is_empty());
    }
}
