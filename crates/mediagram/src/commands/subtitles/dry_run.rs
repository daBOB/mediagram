//! Walks the given folders, matches their video files against every
//! complete `movie`/`ep`/`docu` set in the local index, then a header
//! ffprobe of each matched or fallback file counts its de/en text and
//! picture-only subtitle tracks. Nothing here writes to the index, extracts
//! a track or reaches Telegram — `streams::probe` only runs `ffprobe`.

use std::path::PathBuf;

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::config::Config;
use crate::index::db;
use crate::index::set_row::SetRow;
use crate::index::status::SetStatus;
use crate::media::classify;
use crate::media::streams::{self, Probed};
use crate::media::video_files::collect_videos;

use super::dry_run_report::print_report;
use super::match_source::{self, Match, SourceFile};

/// Everything a backfill knows about the folders before it sends anything.
pub struct Survey {
    pub sets: Vec<SetRow>,
    pub files: Vec<SourceFile>,
    pub probes: Vec<Option<Probed>>,
    pub matches: Vec<Match>,
}

pub async fn run(cfg: &Config, folders: &[PathBuf]) -> Result<()> {
    let conn = db::open_read_only(&cfg.data_dir()?, "measure a subtitles backfill")?;
    let found = survey(&conn, folders).await?;
    print_report(&found.files, &found.probes, &found.matches, &found.sets);
    Ok(())
}

/// Walks, probes and matches; reads the index, writes nothing.
pub async fn survey(conn: &Connection, folders: &[PathBuf]) -> Result<Survey> {
    let sets = load_candidate_sets(conn)?;

    let mut files = Vec::new();
    for folder in folders {
        let found =
            collect_videos(folder).with_context(|| format!("walking {}", folder.display()))?;
        files.extend(found);
    }
    files.sort();
    files.dedup();

    // ponytail: every walked file is probed up front, matched or not — the
    // simplest way to get a candidate's duration before the fallback can
    // even be attempted. Media folders are curated (never drive roots), so
    // the wasted probes on files that turn out ambiguous/unmatched stay
    // small; batching probes only for files a first size-only pass left
    // undecided would cut that cost if a folder ever makes it worth it.
    let mut source_files = Vec::with_capacity(files.len());
    let mut probes: Vec<Option<Probed>> = Vec::with_capacity(files.len());
    for path in &files {
        let size = std::fs::metadata(path)
            .with_context(|| format!("reading {}", path.display()))?
            .len();
        let is_mp4 = classify::container_from_ext(path) == "mp4";
        let guess = mlib_spec::filename::parse_filename(&crate::paths::file_name(path));
        let probed = match streams::probe(path).await {
            Ok(probed) => Some(probed),
            Err(err) => {
                eprintln!("warning: probing {} failed: {err:#}", path.display());
                None
            }
        };
        let duration = probed.as_ref().map(|p| p.duration);
        source_files.push(SourceFile {
            path: path.clone(),
            size,
            is_mp4,
            duration,
            guess,
        });
        probes.push(probed);
    }

    let matches = match_source::match_sources(&source_files, &sets);
    Ok(Survey {
        sets,
        files: source_files,
        probes,
        matches,
    })
}

/// Every complete `movie`/`ep`/`docu` set, whether or not it already has a
/// subtitle bundle: the red team's fix for a candidate pool that used to
/// shrink as sets got bundles, letting a later run's fallback drift onto
/// the wrong title.
fn load_candidate_sets(conn: &Connection) -> Result<Vec<SetRow>> {
    let mut stmt = conn.prepare(
        "SELECT * FROM sets WHERE status = ?1 AND kind IN ('movie', 'ep', 'docu')
         ORDER BY set_id",
    )?;
    let rows = stmt
        .query_map([SetStatus::Complete], SetRow::from_row)?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    Ok(rows)
}
