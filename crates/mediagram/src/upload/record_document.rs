//! Recording one course document as a set ready to upload.
//!
//! A document takes the same road as a lesson from the moment there are bytes
//! to send — the same parts, the same captions, the same upload — and skips
//! everything before it. There is nothing to probe: a PDF has no
//! duration, no codecs and no languages, and `ffprobe` would only be asked a
//! question it cannot answer. There is nothing to remux either, and nothing
//! to look up: a document belongs to a course, and a course is described by
//! hand.
//!
//! No subcommand records a document on its own. Documents are found by walking
//! a course, so `add-course` is the one caller and a second entry point would
//! be a second way to get the numbering wrong.

use std::path::PathBuf;

use anyhow::{Context, Result};
use mlib_spec::caption::{Episode, Kind};
use mlib_spec::ids::ProviderIds;
use mlib_spec::{Caption, Part};

use crate::config::Config;
use crate::index::db;
use crate::media::classify;
use crate::upload::plan::{Source, record_planned};

#[deprecated(
    since = "0.40.2",
    note = "Use record_document_set; this operation persists the document"
)]
pub use record_document_set as plan_document;

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

/// Writes one document to the index as a set ready to upload, and returns
/// its id.
pub fn record_document_set(cfg: &Config, doc: &Document) -> Result<String> {
    let total = std::fs::metadata(&doc.file)
        .with_context(|| format!("stat {}", doc.file.display()))?
        .len();
    let part_ranges = mlib_spec::plan_parts(total, cfg.part_size).context("planning parts")?;

    let set_id = ulid::Ulid::new().to_string();
    let caption = caption_for(doc, &set_id, total, part_ranges.len() as u32);

    let mut conn = db::open(&cfg.data_dir()?)?;
    // No remux ever runs on a document, so the source recorded is the file
    // itself and there is no temporary to clean up after.
    let source = Source {
        path: &doc.file,
        remux: false,
    };
    record_planned(&mut conn, &caption, &part_ranges, source, |_| Ok(()))?;
    Ok(set_id)
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
