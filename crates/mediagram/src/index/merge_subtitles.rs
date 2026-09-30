//! Merging `subtitle_files`/`subtitle_tracks` from the attached channel
//! snapshot.
//!
//! A bundled set is not filled in column by column the way `shows` is: the
//! newer `uploaded_at` wins outright, and its tracks come with it whole,
//! because the two uploaders never split one set's bundle between them —
//! whichever upload is newer is simply the whole truth for that set. New in
//! v13, so `shared_columns` tolerates a channel snapshot with no
//! `subtitle_files` table yet, the same way it tolerates a missing column.
//!
//! Must run before `merge_copy::fill_missing_assets`, which only stops
//! filling in a bundled set's legacy inline rows once this has recorded its
//! `subtitle_files` row.

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::merge_columns::shared_columns;

/// What a subtitle merge did.
#[derive(Debug, Default, Clone, Copy)]
pub(super) struct SubtitleMergeReport {
    pub subtitles_taken: usize,
    /// The channel snapshot has no `subtitle_files` table while this index
    /// holds subtitle rows: an uploader older than this schema published
    /// over a channel that had them.
    pub channel_lacks_subtitles: bool,
}

/// Sets whose channel file just became, or already was, the one this index
/// holds — a fresh insert and a newer replacement both count; an older
/// channel file that lost the comparison does not.
const TAKEN_SET_IDS: &str = "SELECT ch.set_id FROM channel.subtitle_files ch
     JOIN main.subtitle_files m ON m.set_id = ch.set_id
     WHERE m.message_id = ch.message_id";

pub(super) fn merge(conn: &Connection) -> Result<SubtitleMergeReport> {
    let file_cols = shared_columns(conn, "subtitle_files")?;
    if file_cols.is_empty() {
        let local_has_rows: bool = conn
            .query_row(
                "SELECT EXISTS(SELECT 1 FROM main.subtitle_files)",
                [],
                |row| row.get(0),
            )
            .context("checking for local subtitle rows")?;
        return Ok(SubtitleMergeReport {
            channel_lacks_subtitles: local_has_rows,
            ..SubtitleMergeReport::default()
        });
    }

    let col_list = file_cols.join(", ");
    let update_list: String = file_cols
        .iter()
        .filter(|c| c.as_str() != "set_id")
        .map(|c| format!("{c} = excluded.{c}"))
        .collect::<Vec<_>>()
        .join(", ");
    let subtitles_taken = conn
        .execute(
            &format!(
                "INSERT INTO main.subtitle_files ({col_list})
                 SELECT {col_list} FROM channel.subtitle_files ch
                 WHERE EXISTS (SELECT 1 FROM main.sets s WHERE s.set_id = ch.set_id)
                 ON CONFLICT(set_id) DO UPDATE SET {update_list}
                 WHERE excluded.uploaded_at > subtitle_files.uploaded_at"
            ),
            [],
        )
        .context("merging subtitle files")?;

    conn.execute(
        &format!("DELETE FROM main.subtitle_tracks WHERE set_id IN ({TAKEN_SET_IDS})"),
        [],
    )
    .context("clearing tracks a newer channel bundle replaces")?;
    let track_cols = shared_columns(conn, "subtitle_tracks")?;
    let track_col_list = track_cols.join(", ");
    conn.execute(
        &format!(
            "INSERT INTO main.subtitle_tracks ({track_col_list})
             SELECT {track_col_list} FROM channel.subtitle_tracks ch
             WHERE ch.set_id IN ({TAKEN_SET_IDS})"
        ),
        [],
    )
    .context("copying tracks for the subtitle files just taken")?;

    // A bundled set is self-describing; the legacy per-language rows it
    // replaces would otherwise linger as unreachable duplicates.
    conn.execute(
        "DELETE FROM main.assets
         WHERE kind = 'subtitle' AND set_id IN (SELECT set_id FROM main.subtitle_files)",
        [],
    )
    .context("dropping inline subtitle rows superseded by a bundle")?;

    Ok(SubtitleMergeReport {
        subtitles_taken,
        channel_lacks_subtitles: false,
    })
}

#[cfg(test)]
#[path = "merge_subtitles_tests.rs"]
mod tests;
