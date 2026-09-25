//! Part-row helpers for the rescan: look up, upsert and mark parts done from
//! caption data, keeping the newest message when a part index repeats.

use anyhow::Result;
use mlib_spec::caption::Caption;
use rusqlite::{Connection, OptionalExtension, params};

use crate::index::status::PartStatus;

/// One existing `parts` row's state, enough to detect a duplicate caption.
pub(super) struct ExistingPart {
    pub(super) status: PartStatus,
    pub(super) message_id: Option<i64>,
}

pub(super) fn existing_part(
    conn: &Connection,
    set_id: &str,
    idx: u32,
) -> Result<Option<ExistingPart>> {
    conn.query_row(
        "SELECT status, message_id FROM parts WHERE set_id = ?1 AND idx = ?2",
        params![set_id, idx],
        |row| {
            Ok(ExistingPart {
                status: row.get(0)?,
                message_id: row.get(1)?,
            })
        },
    )
    .optional()
    .map_err(Into::into)
}

/// Records `caption`'s part as done, keyed by `(set_id, idx)`. Returns `true`
/// for a duplicate observation of an already-`done` part under a different
/// message id, only overwriting it when the new id is higher (ids only
/// increase, so the higher one is the more recent, still-live copy).
pub(super) fn upsert_part(
    conn: &Connection,
    chat_id: i64,
    message_id: i64,
    doc_id: i64,
    caption: &Caption,
) -> Result<bool> {
    let existing = existing_part(conn, &caption.set, caption.part.i)?;
    let is_duplicate = match &existing {
        Some(existing) => {
            existing.status == PartStatus::Done && existing.message_id != Some(message_id)
        }
        None => false,
    };
    if is_duplicate {
        let existing_message_id = existing.and_then(|e| e.message_id).unwrap_or(i64::MIN);
        if message_id > existing_message_id {
            write_part_done(conn, chat_id, message_id, doc_id, caption)?;
        }
        return Ok(true);
    }
    write_part_done(conn, chat_id, message_id, doc_id, caption)?;
    Ok(false)
}

pub(super) fn write_part_done(
    conn: &Connection,
    chat_id: i64,
    message_id: i64,
    doc_id: i64,
    caption: &Caption,
) -> Result<()> {
    conn.execute(
        "INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
         ON CONFLICT(set_id, idx) DO UPDATE SET
            byte_offset = excluded.byte_offset,
            byte_length = excluded.byte_length,
            chat_id = excluded.chat_id,
            message_id = excluded.message_id,
            doc_id = excluded.doc_id,
            sha256 = excluded.sha256,
            status = excluded.status",
        params![
            caption.set,
            caption.part.i,
            caption.part.off as i64,
            caption.part.len as i64,
            chat_id,
            message_id,
            doc_id,
            caption.part.sha256,
            PartStatus::Done,
        ],
    )?;
    Ok(())
}
