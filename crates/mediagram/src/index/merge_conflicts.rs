//! Rewriting a conflicting set's row from its own captions.
//!
//! `merge::merge_from` finds shared sets whose metadata differs but picks no
//! side: a caption is the one place a set's own upload wrote that metadata,
//! and neither index is more authoritative than the other about it. This
//! resolves the conflict the way `edit` already does — by rewriting the row
//! from a caption, via the same [`crate::index::set_row::SetRow::from_caption`]
//! mapping `rescan` uses to rebuild a set from scratch.

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::set_row::SetRow;
use crate::index::sets;

/// Rewrites `set_id`'s metadata from the first caption in `captions` that
/// parses as one of its own parts. `captions` is raw message text, gone
/// entries included as empty strings — this only reads what parses.
///
/// Pure: a test supplies caption text directly instead of a Telegram fetch.
/// Returns whether a caption was found and applied.
pub fn resolve_from_captions(conn: &Connection, set_id: &str, captions: &[String]) -> Result<bool> {
    let existing = sets::get_set(conn, set_id)?
        .with_context(|| format!("set {set_id} vanished before its conflict could be resolved"))?;
    for text in captions {
        if !mlib_spec::caption_codec::is_mlib(text) {
            continue;
        }
        let Ok(caption) = mlib_spec::parse(text) else {
            continue;
        };
        if caption.set != set_id {
            continue;
        }
        let row = SetRow::from_caption(&caption, existing.created_at);
        sets::update_metadata(conn, &row)?;
        return Ok(true);
    }
    Ok(false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use mlib_spec::caption::{Caption, Kind, Part};
    use mlib_spec::ids::ProviderIds;

    fn caption_text(set_id: &str, title: &str) -> String {
        let caption = Caption {
            cid: None,
            chap: None,
            path: None,
            t: Kind::Movie,
            ids: ProviderIds {
                tmdb: Some(42),
                tvdb: None,
                imdb: None,
            },
            show: None,
            title: Some(title.to_string()),
            year: Some(2020),
            s: None,
            e: None,
            abs: None,
            q: None,
            hdr: None,
            container: "mkv".into(),
            vcodec: None,
            acodec: None,
            alang: vec![],
            slang: vec![],
            dur: None,
            variant: None,
            set: set_id.to_string(),
            part: Part {
                i: 0,
                n: 1,
                off: 0,
                len: 10,
                sha256: "a".repeat(64),
            },
            total: 10,
        };
        mlib_spec::to_text(&caption, "").unwrap()
    }

    fn seeded_set(conn: &Connection, set_id: &str) {
        let caption = {
            let text = caption_text(set_id, "Old Title");
            mlib_spec::parse(&text).unwrap()
        };
        let row = SetRow::from_caption(&caption, 1_700_000_000);
        sets::insert_set(conn, &row).unwrap();
    }

    #[test]
    fn the_first_parseable_caption_for_this_set_rewrites_the_row() {
        let dir = tempfile::tempdir().unwrap();
        let conn = crate::index::db::open(dir.path()).unwrap();
        seeded_set(&conn, "01JQ8F2K9M4XZ00000000001");

        let captions = vec![
            "not mlib at all".to_string(),
            caption_text("01JQ8F2K9M4XZ00000000099", "Wrong Set"),
            caption_text("01JQ8F2K9M4XZ00000000001", "New Title"),
        ];
        let applied = resolve_from_captions(&conn, "01JQ8F2K9M4XZ00000000001", &captions).unwrap();
        assert!(applied);

        let row = sets::get_set(&conn, "01JQ8F2K9M4XZ00000000001")
            .unwrap()
            .unwrap();
        assert_eq!(row.title.as_deref(), Some("New Title"));
        // Metadata-only: the byte-describing fields stay as they were.
        assert_eq!(row.total, 10);
    }

    #[test]
    fn no_matching_caption_applies_nothing() {
        let dir = tempfile::tempdir().unwrap();
        let conn = crate::index::db::open(dir.path()).unwrap();
        seeded_set(&conn, "01JQ8F2K9M4XZ00000000001");

        let captions = vec!["gone".to_string()];
        let applied = resolve_from_captions(&conn, "01JQ8F2K9M4XZ00000000001", &captions).unwrap();
        assert!(!applied);

        let row = sets::get_set(&conn, "01JQ8F2K9M4XZ00000000001")
            .unwrap()
            .unwrap();
        assert_eq!(row.title.as_deref(), Some("Old Title"));
    }
}
