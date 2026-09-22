//! `mediagram add`: inspect → resolve → remux → plan → index → upload, the
//! last step either watched here or handed to a background process.
//!
//! [`plan`] is everything up to the upload, and is what `add-show` and
//! `add-course` call for each file they walk; they upload over one
//! connection of their own.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use mediagram_tmdb::tmdb_client::TmdbClient;
use mlib_spec::{Caption, Part};

use super::args::AddArgs;
use super::{background, finish_set};
use crate::config::Config;
use crate::index::{assets, db, shows};
use crate::media::{classify, inspect, remux};
use crate::metadata::prompt::DialoguerPrompter;
use crate::metadata::resolve::{self, ResolveInput};
use crate::metadata::show_details;
use crate::upload::plan::{Source, record_planned};

/// One file to add, however it was asked for: the `add` command's flags, or
/// one entry of a walked show or course.
#[derive(Debug, Default, Clone)]
pub struct NewSet {
    pub file: PathBuf,
    pub tmdb: Option<u64>,
    pub tvdb: Option<u64>,
    pub imdb: Option<String>,
    pub season: Option<u32>,
    pub episode: Option<u32>,
    /// Absolute episode number (anime).
    pub abs: Option<u32>,
    pub variant: Option<String>,
    /// Enter metadata by hand instead of looking it up.
    pub manual: bool,
    pub no_remux: bool,
    /// Overrides for what the file itself says.
    pub alang: Option<Vec<String>>,
    pub slang: Option<Vec<String>>,
    pub hdr: Option<String>,
    /// Set when this is a lesson, which is described by hand rather than
    /// looked up.
    pub lesson: Option<LessonOf>,
}

/// Where a lesson sits in its course.
#[derive(Debug, Default, Clone)]
pub struct LessonOf {
    pub course: String,
    /// Collection id grouping the course's lessons.
    pub cid: String,
    pub chapter: Option<u32>,
    pub chapter_title: Option<String>,
    /// Folders within the course, `/`-separated.
    pub path: Option<String>,
    pub number: Option<u32>,
}

impl From<AddArgs> for NewSet {
    fn from(args: AddArgs) -> NewSet {
        let lesson = args.course.map(|course| LessonOf {
            cid: args.cid.unwrap_or_else(|| mlib_spec::slug::slug(&course)),
            course,
            chapter: args.chapter,
            chapter_title: args.chap,
            path: args.path,
            number: args.lesson,
        });
        NewSet {
            file: args.file,
            tmdb: args.tmdb,
            tvdb: args.tvdb,
            imdb: args.imdb,
            season: args.season,
            episode: args.episode,
            abs: args.abs_no,
            variant: args.variant,
            manual: args.manual,
            no_remux: args.no_remux,
            alang: args.alang,
            slang: args.slang,
            hdr: args.hdr,
            lesson,
        }
    }
}

/// A set written to the index, with its bytes still to send.
pub struct Planned {
    pub set_id: String,
    pub display_name: String,
    pub total: u64,
}

pub async fn run(cfg: &Config, args: AddArgs) -> Result<()> {
    let (watch, no_push) = (args.watch, args.no_push);
    let to_delete = args.delete_source.then(|| args.file.clone());
    let planned = plan(cfg, &NewSet::from(args)).await?;

    // Everything that can ask a question or refuse has happened: the file was
    // inspected, the title resolved, the caption measured, the rows written.
    // What is left is bytes, which is the part worth handing away.
    if watch {
        return finish_set::run(cfg, &planned.set_id, to_delete.as_deref(), no_push).await;
    }
    // Asked before the child is started, because the child is what will be
    // holding it a moment later.
    let queued = crate::upload::lock::is_held(&cfg.data_dir()?);
    let started = background::spawn_finish_set(cfg, &planned.set_id, to_delete.as_deref(), no_push)?;
    println!(
        "set {} planned · {} · {:.2} GB",
        planned.set_id,
        planned.display_name,
        planned.total as f64 / 1e9
    );
    println!(
        "  {} (pid {}); `mediagram status` says how far it has got",
        if queued {
            "queued behind the upload already running"
        } else {
            "uploading in the background"
        },
        started.pid
    );
    println!("  output: {}", started.log.display());
    Ok(())
}

/// Inspects, resolves and remuxes one file and writes it to the index as a
/// set ready to upload.
pub async fn plan(cfg: &Config, new: &NewSet) -> Result<Planned> {
    let info = inspect::inspect(&new.file)
        .await
        .with_context(|| format!("inspecting {}", new.file.display()))?;
    let file_name = new
        .file
        .file_name()
        .and_then(|n| n.to_str())
        .with_context(|| format!("{} has no usable file name", new.file.display()))?
        .to_string();

    // A lesson is described by hand, so it needs neither a key nor a lookup.
    if new.lesson.is_some() && (new.tmdb.is_some() || new.tvdb.is_some() || new.imdb.is_some()) {
        bail!("--course describes a tutorial, which has no provider id; drop --tmdb/--tvdb/--imdb");
    }
    if new.lesson.is_none() && !new.manual && cfg.tmdb_key.is_none() {
        bail!("no tmdb_key configured in config.toml; set one or pass --manual");
    }

    let data_dir = cfg.data_dir()?;
    let api = TmdbClient::with_cache(
        mediagram_core::api::http::client()?,
        cfg.tmdb_key.as_deref().unwrap_or(""),
        &data_dir,
        &cfg.tmdb_language,
    );
    let lesson = new.lesson.as_ref();
    let resolve_input = ResolveInput {
        file_name,
        tmdb: new.tmdb,
        tvdb: new.tvdb,
        imdb: new.imdb.clone(),
        season: new.season.or(lesson.and_then(|l| l.chapter)),
        episode: new.episode.or(lesson.and_then(|l| l.number)),
        abs: new.abs,
        manual: new.manual,
    };
    let resolved = match lesson {
        Some(lesson) => resolve::lesson(&lesson.course, &resolve_input),
        None => resolve::resolve(&api, &resolve_input, &mut DialoguerPrompter)
            .await
            .context("resolving metadata")?,
    };

    // Kept before the caption takes ownership of the resolved ids.
    let show_id = lesson.is_none().then_some(resolved.ids.tmdb).flatten();
    let show_kind = resolved.kind;

    let source_path = remux::ensure_faststart(&new.file, cfg.tmp_dir.as_deref(), new.no_remux)
        .await
        .context("preparing file for splitting")?;
    let total = tokio::fs::metadata(&source_path)
        .await
        .with_context(|| format!("stat {}", source_path.display()))?
        .len();
    let part_ranges = mlib_spec::plan_parts(total, cfg.part_size).context("planning parts")?;

    let set_id = ulid::Ulid::new().to_string();
    let caption = Caption {
        cid: lesson.map(|l| l.cid.clone()),
        chap: lesson.and_then(|l| l.chapter_title.clone()),
        path: lesson.and_then(|l| l.path.clone()),
        t: resolved.kind,
        ids: resolved.ids,
        show: resolved.show,
        title: resolved.title,
        year: resolved.year,
        s: resolved.season,
        e: resolved.episode,
        abs: resolved.abs,
        q: info.quality,
        hdr: Some(new.hdr.clone().unwrap_or(info.hdr)),
        container: classify::container_from_ext(&source_path),
        vcodec: info.vcodec,
        acodec: info.acodec,
        alang: new.alang.clone().unwrap_or(info.alang),
        slang: new.slang.clone().unwrap_or(info.slang),
        dur: info.duration_s,
        variant: new.variant.clone(),
        set: set_id.clone(),
        part: Part {
            i: 0,
            n: part_ranges.len() as u32,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total,
    };

    let mut conn = db::open(&data_dir)?;
    // What the provider says about the show this belongs to. The payload is
    // already cached from resolving the title, so this is a read of disk.
    // A failure costs the show its description and nothing else — the upload
    // is the point, and a synopsis is not worth failing it for.
    if let Some(id) = show_id {
        match show_details::fetch(&api, show_kind, id, &cfg.tmdb_language).await {
            Ok(row) => shows::upsert(&conn, &row)?,
            Err(err) => tracing::warn!(id, error = %err, "no description recorded for this title"),
        }
    }
    let source = Source {
        path: &source_path,
        remux: source_path != new.file,
    };
    record_planned(&mut conn, &caption, &part_ranges, source, |tx| {
        store_sidecars(tx, &set_id, &new.file, &caption)
    })?;

    Ok(Planned {
        display_name: caption.display_name(),
        set_id,
        total,
    })
}

/// Stores the subtitle and summary sitting beside a video, if any.
///
/// Read from the source the user named rather than from a faststart remux:
/// the remux is a temporary file this command wrote, and the sidecars belong
/// to the original.
///
/// A missing sidecar is the ordinary case and says nothing. A present one
/// that cannot be stored is worth a warning, and no more: a lesson without
/// its subtitle is still worth having.
fn store_sidecars(
    conn: &rusqlite::Connection,
    set_id: &str,
    source: &Path,
    caption: &Caption,
) -> Result<()> {
    let found = crate::course::sidecars::find_sidecars(source);

    if let Some(subtitle) = &found.subtitle {
        // The subtitle is the audio written down, so it is in the audio's
        // language; `und` when the file never said.
        let lang = caption.alang.first().map(String::as_str).unwrap_or("und");
        if let Err(err) = assets::put(conn, set_id, assets::Kind::Subtitle, lang, subtitle) {
            tracing::warn!("subtitle for {set_id} not stored: {err:#}");
        }
    }
    if let Some(summary) = &found.summary
        && let Err(err) = assets::put(conn, set_id, assets::Kind::Summary, "", summary)
    {
        tracing::warn!("summary for {set_id} not stored: {err:#}");
    }
    Ok(())
}
