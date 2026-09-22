//! Uploading one course document.
//!
//! A document takes the same road as a lesson from the moment there are bytes
//! to send — the same parts, the same captions, the same `finish_set` — and
//! skips everything before it. There is nothing to probe: a PDF has no
//! duration, no codecs and no languages, and `ffprobe` would only be asked a
//! question it cannot answer. There is nothing to remux either, and nothing
//! to look up: a document belongs to a course, and a course is described by
//! hand.
//!
//! Not a subcommand. Documents are found by walking a course, so `add-course`
//! is the one caller and a second entry point would be a second way to get
//! the numbering wrong.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use mlib_spec::{Caption, Part};
use mlib_spec::caption::{Episode, Kind};
use mlib_spec::ids::ProviderIds;

use super::finish_set;
use crate::config::Config;
use crate::index::sets::SetRow;
use crate::index::{db, parts, sets};
use crate::media::classify;

/// One document to upload, as the walk describes it.
pub struct Document {
    pub file: PathBuf,
    /// Course title, which is what a document is filed under.
    pub course: String,
    pub cid: String,
    pub chapter: u32,
    pub chapter_title: Option<String>,
    /// Folders within the course, `/`-separated; `None` at the root.
    pub path: Option<String>,
    /// Number within the chapter. Shares the lesson's sequence deliberately:
    /// a handout numbered like its lesson is what puts the two on adjacent
    /// rows.
    pub number: u32,
    pub title: Option<String>,
    pub variant: Option<String>,
}

pub async fn run(cfg: &Config, doc: &Document) -> Result<()> {
    let total = tokio::fs::metadata(&doc.file)
        .await
        .with_context(|| format!("stat {}", doc.file.display()))?
        .len();
    let part_ranges = mlib_spec::plan_parts(total, cfg.part_size).context("planning parts")?;

    let set_id = ulid::Ulid::new().to_string();
    let caption = caption_for(doc, &set_id, total, part_ranges.len() as u32);

    // The longest caption a set will produce is its last part's, whose
    // offsets are the largest. Measured before anything is written, because a
    // caption that cannot be sent is a set that cannot be finished.
    let probe = caption.with_part(Part {
        i: part_ranges.len() as u32 - 1,
        n: part_ranges.len() as u32,
        off: total,
        len: total,
        sha256: "0".repeat(64),
    });
    mlib_spec::to_text(&probe, &probe.display_name())
        .context("caption exceeds Telegram's budget; shorten --variant or the course title")?;

    let created_at = crate::clock::now_unix();
    let set_row = SetRow::from_caption(&caption, created_at)?;

    let data_dir = cfg.data_dir()?;
    let mut conn = db::open(&data_dir)?;
    {
        let tx = conn.transaction().context("starting index transaction")?;
        sets::insert_set(&tx, &set_row)?;
        parts::insert_parts(&tx, &set_id, &part_ranges)?;
        // No remux ever runs on a document, so the source recorded here is
        // the file itself and there is no temporary to clean up after.
        db::set_meta(&tx, &db::source_key(&set_id), &source_value(&doc.file))?;
        tx.commit().context("committing index transaction")?;
    }
    drop(conn);

    // Uploaded in this process rather than handed to a background one: the
    // caller is walking a course and wants the documents finished in the
    // order it found them, the way it already uploads lessons.
    finish_set::run(cfg, &set_id, None, true).await
}

/// The record every part of this document carries.
///
/// The fields a video would fill are left empty rather than given a stand-in
/// value. A `0` duration or an `application/pdf` masquerading as a codec is a
/// value a reader has to learn to disbelieve; absence is a value it already
/// knows.
fn caption_for(doc: &Document, set_id: &str, total: u64, part_count: u32) -> Caption {
    Caption {
        t: Kind::Doc,
        ids: ProviderIds::default(),
        cid: Some(doc.cid.clone()),
        show: Some(doc.course.clone()),
        chap: doc.chapter_title.clone(),
        path: doc.path.clone(),
        title: doc.title.clone(),
        year: None,
        s: Some(doc.chapter),
        e: Some(Episode::Single(doc.number)),
        abs: None,
        q: None,
        hdr: None,
        container: classify::container_from_ext(&doc.file),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: doc.variant.clone(),
        set: set_id.to_string(),
        part: Part {
            i: 0,
            n: part_count,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total,
    }
}

fn source_value(file: &Path) -> String {
    file.canonicalize()
        .unwrap_or_else(|_| file.to_path_buf())
        .to_string_lossy()
        .into_owned()
}
