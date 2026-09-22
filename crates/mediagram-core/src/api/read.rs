//! Serving one read against a set's virtual file: the same range-planning
//! code `mediagram serve` uses, driving the Telegram transport directly
//! rather than through an HTTP response body.

use grammers_session::types::{PeerId, PeerRef};
use tokio::sync::mpsc;

use crate::catalog as queries;
use crate::document::is_stale_reference;
use crate::range::{self, ByteRange, PartSpan};
use crate::stream;

use super::{Core, CoreError, catalog, library, session};

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

    let client = session::client(core).await;
    // Read once, not per part: every part of a set lives in the same
    // channel, and this is a file on disk.
    let handles = library::read(&library::path(core))?;

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
        let channel = channel_ref(&handles, location.chat_id)?;
        let document = document_for(core, &client, channel, &set_id, location.message_id).await?;

        let start = out.len();
        match fetch_step(&client, document, step, &mut out).await {
            Ok(()) => {}
            // The held handle's file reference has expired — a film paused
            // for hours comes back to one. Resolve the part again for a
            // fresh reference and retry once; a second refusal is real.
            Err(err) if is_stale_reference(&err) => {
                out.truncate(start);
                core.state.lock().await.documents.evict(&set_id, location.message_id);
                let fresh = document_for(core, &client, channel, &set_id, location.message_id).await?;
                fetch_step(&client, fresh, step, &mut out).await.map_err(interrupted)?;
            }
            Err(err) => return Err(interrupted(err)),
        }
    }
    Ok(out)
}

/// Downloads one step onto the end of `out`.
///
/// A separate task drains while `pump_step` runs, so a step whose bytes
/// outgrow the buffer cannot deadlock against a receiver that only starts
/// reading once the sender is done. The pump's own error is returned intact,
/// so the caller can tell a stale file reference from a dropped connection.
async fn fetch_step(
    client: &grammers_client::Client,
    document: grammers_client::media::Document,
    step: range::Step,
    out: &mut Vec<u8>,
) -> anyhow::Result<()> {
    let (tx, mut rx) = mpsc::channel(BUFFERED_CHUNKS);
    let pump_client = client.clone();
    let pump = tokio::spawn(async move { stream::pump_step(&pump_client, &document, &step, &tx).await });
    while let Some(chunk) = rx.recv().await {
        out.extend(chunk?);
    }
    pump.await
        .map_err(|_| anyhow::anyhow!("the download task did not finish cleanly"))?
}

/// What Kotlin is told when a download fails: the cause stays in Rust.
fn interrupted(_: anyhow::Error) -> CoreError {
    CoreError::Network("the download ended before it finished".into())
}

/// The document a part's bytes live in, resolved once per set.
///
/// A player reads the same few parts a few hundred times, and resolving is
/// a round trip every time — a third of the cost of a read that otherwise
/// fetches two chunks.
async fn document_for(
    core: &Core,
    client: &grammers_client::Client,
    channel: PeerRef,
    set_id: &str,
    message_id: i64,
) -> Result<grammers_client::media::Document, CoreError> {
    if let Some(held) = core.state.lock().await.documents.get(set_id, message_id) {
        return Ok(held);
    }
    let document = stream::part_document(client, channel, message_id)
        .await
        .map_err(|_| CoreError::Network("the part could not be resolved".into()))?;
    core.state
        .lock()
        .await
        .documents
        .put(set_id, message_id, document.clone());
    Ok(document)
}

/// How to address the channel a part lives in.
///
/// The recorded `access_hash` is the whole of it. Telegram refuses a channel
/// addressed without one — `CHANNEL_INVALID` — and the ambient authority a
/// bare id carries is only ever enough for a bot or a contact, which a
/// library channel is not. So a channel this device has no record of is said
/// to be unaddressable here rather than asked for and refused: the answer is
/// to pick the library again, which is what records it.
fn channel_ref(handles: &library::Handles, chat_id: i64) -> Result<PeerRef, CoreError> {
    if PeerId::from_bot_api_dialog_id(chat_id).is_none() {
        return Err(CoreError::Io("the catalog names an invalid channel".into()));
    }
    library::peer_for_chat(handles, chat_id).ok_or_else(|| {
        CoreError::NotFound(
            "this device has no way to reach the channel that set is in. \
             Choose the library again to record it."
                .into(),
        )
    })
}

#[cfg(test)]
mod tests {
    use grammers_session::types::PeerAuth;
    use rusqlite::Connection;

    use super::*;

    fn handles_naming(chat: i64, auth: i64) -> library::Handles {
        let mut handles = library::Handles::new();
        library::handle_for(
            &mut handles,
            library::LibraryEntry {
                chat,
                auth,
                title: "Films".into(),
            },
        );
        handles
    }

    /// The whole of what makes a set playable. Telegram answers a channel
    /// addressed with its `access_hash` and refuses the same channel
    /// addressed without one, so a part resolved against ambient authority
    /// fails for every set in the library while the catalog still lists
    /// them all.
    #[test]
    fn a_part_is_addressed_with_the_hash_recorded_for_its_channel() {
        let handles = handles_naming(-1_001_234_567_890, 7_654_321);

        let peer = channel_ref(&handles, -1_001_234_567_890).expect("a recorded channel");

        assert_eq!(peer.auth.hash(), 7_654_321);
        assert_ne!(peer.auth, PeerAuth::default(), "a channel refuses ambient authority");
    }

    /// Said plainly, and with the thing to do about it. Asking anyway would
    /// spend a round trip to be told `CHANNEL_INVALID`, which reaches a
    /// viewer as "could not be resolved" and names nothing they can act on.
    #[test]
    fn a_channel_this_device_never_recorded_says_so_rather_than_asking_anyway() {
        let handles = handles_naming(-1_001_234_567_890, 7_654_321);

        let refused = channel_ref(&handles, -1_001_999_999_999).unwrap_err();

        assert!(matches!(refused, CoreError::NotFound(_)));
        assert!(refused.to_string().contains("Choose the library again"));
    }

    #[test]
    fn a_chat_id_that_is_not_a_dialog_id_is_a_broken_catalog_not_a_missing_channel() {
        let refused = channel_ref(&library::Handles::new(), 0).unwrap_err();
        assert!(matches!(refused, CoreError::Io(_)));
    }

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
