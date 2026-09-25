//! Reading a `SyncRecord` off the wire: the hostile-input parse
//! `record.rs`'s struct definitions describe. Split out only to keep that
//! file under the line limit — this module leans on `record`'s own parsing
//! helpers throughout, the same way `list_record.rs` does.

use serde_json::Value;

use super::hostile_json::{as_array, is_integer, js_number, text_};
use super::list_record::{collection_row, list_row};
use super::{ProfileState, ProgressRow, SYNC_FORMAT, SyncRecord, WatchedRow};

/// Reads a document from the channel, or `None` if it cannot be trusted.
///
/// Written as if the input were hostile, because in the useful sense it is:
/// it came off the network, it was written by another machine, and it may
/// have been written by a *newer* version of this program. Every row is
/// checked and a bad one is dropped rather than taking the document down
/// with it — one unparseable position should not cost a viewer the rest of
/// their history.
pub fn parse_record(text: &str) -> Option<SyncRecord> {
    let raw: Value = serde_json::from_str(text).ok()?;
    let held = raw.as_object()?;

    let format = js_number(held.get("format"));
    // A document from the future is ignored rather than guessed at. Reading
    // it half-right would merge a half-right answer into a database that is
    // the source of truth for this machine.
    if !is_integer(format) || format < 1.0 || format > SYNC_FORMAT as f64 {
        return None;
    }

    let device = text_(held.get("device"))?;

    let profiles = as_array(held.get("profiles"))
        .iter()
        .filter_map(profile_state)
        .collect();
    let kids = as_array(held.get("kids"))
        .iter()
        .filter_map(list_row)
        .collect();
    let editors_choice = as_array(held.get("editorsChoice"))
        .iter()
        .filter_map(list_row)
        .collect();

    // `Number(held.writtenAt) || 0`: NaN and 0 both fall back to 0, and a
    // negative or positive finite number passes through unchanged.
    let written_at = js_number(held.get("writtenAt"));
    let written_at = if written_at.is_nan() { 0.0 } else { written_at };

    Some(SyncRecord {
        format: format as i64,
        device,
        written_at,
        profiles,
        kids,
        editors_choice,
    })
}

fn profile_state(raw: &Value) -> Option<ProfileState> {
    let row = raw.as_object()?;
    let name = text_(row.get("name"))?;
    Some(ProfileState {
        name,
        local_id: text_(row.get("localId")),
        // Only a literal `true`, as on the web: a restricting flag must not
        // be switched on by a value that merely looks truthy.
        kids: row.get("kids") == Some(&Value::Bool(true)),
        progress: as_array(row.get("progress"))
            .iter()
            .filter_map(progress_row)
            .collect(),
        watched: as_array(row.get("watched"))
            .iter()
            .filter_map(watched_row)
            .collect(),
        watchlist: as_array(row.get("watchlist"))
            .iter()
            .filter_map(list_row)
            .collect(),
        collections: as_array(row.get("collections"))
            .iter()
            .filter_map(collection_row)
            .collect(),
    })
}

fn progress_row(raw: &Value) -> Option<ProgressRow> {
    let row = raw.as_object()?;
    let set_id = text_(row.get("setId"))?;
    let at = js_number(row.get("at"));
    // `at` may legitimately be 0 — the start of a film — so it is checked
    // for finiteness rather than truthiness.
    if !at.is_finite() || at < 0.0 {
        return None;
    }
    let updated_at = js_number(row.get("updatedAt"));
    if !updated_at.is_finite() || updated_at <= 0.0 {
        return None;
    }
    let runtime = js_number(row.get("duration"));
    let duration = if runtime.is_finite() && runtime > 0.0 {
        Some(runtime)
    } else {
        None
    };
    Some(ProgressRow {
        set_id,
        at,
        duration,
        updated_at,
    })
}

fn watched_row(raw: &Value) -> Option<WatchedRow> {
    let row = raw.as_object()?;
    let set_id = text_(row.get("setId"))?;
    let updated_at = js_number(row.get("updatedAt"));
    if !updated_at.is_finite() || updated_at <= 0.0 {
        return None;
    }
    Some(WatchedRow { set_id, updated_at })
}
