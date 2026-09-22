//! `mediagram add`: inspect → resolve → remux → plan → index → upload, the
//! last step either watched here or handed to a background process.

use anyhow::{Context, Result, bail};
use mediagram_tmdb::tmdb_client::TmdbClient;
use mlib_spec::{Caption, Part};

use super::args::AddArgs;
use super::{background, finish_set};
use crate::config::Config;
use crate::index::sets::SetRow;
use crate::index::{assets, db, parts, sets, shows};
use crate::media::{classify, inspect, remux};
use crate::metadata::prompt::DialoguerPrompter;
use crate::metadata::resolve::{self, ResolveInput};
use crate::metadata::show_details;

pub async fn run(cfg: &Config, args: AddArgs) -> Result<()> {
    let info = inspect::inspect(&args.file)
        .await
        .with_context(|| format!("inspecting {}", args.file.display()))?;
    let file_name = args
        .file
        .file_name()
        .and_then(|n| n.to_str())
        .with_context(|| format!("{} has no usable file name", args.file.display()))?
        .to_string();

    // A course is described by hand, so it needs neither a key nor a lookup.
    let course = args.course.clone();
    if course.is_some() && (args.tmdb.is_some() || args.tvdb.is_some() || args.imdb.is_some()) {
        bail!("--course describes a tutorial, which has no provider id; drop --tmdb/--tvdb/--imdb");
    }
    if course.is_none() && !args.manual && cfg.tmdb_key.is_none() {
        bail!("no tmdb_key configured in config.toml; set one or pass --manual");
    }

    let data_dir = cfg.data_dir()?;
    let api = TmdbClient::with_cache(
        reqwest::Client::new(),
        cfg.tmdb_key.as_deref().unwrap_or(""),
        &data_dir,
        &cfg.tmdb_language,
    );
    let mut prompter = DialoguerPrompter;
    let resolve_input = ResolveInput {
        file_name,
        tmdb: args.tmdb,
        tvdb: args.tvdb,
        imdb: args.imdb,
        season: args.season.or(args.chapter),
        episode: args.episode.or(args.lesson),
        abs: args.abs_no,
        manual: args.manual,
    };
    let resolved = match &course {
        Some(title) => resolve::tutorial(title, &resolve_input),
        None => resolve::resolve(&api, &resolve_input, &mut prompter)
            .await
            .context("resolving metadata")?,
    };

    // Kept before the caption takes ownership of the resolved ids.
    let show_id = course.is_none().then_some(resolved.ids.tmdb).flatten();
    let show_kind = resolved.kind;

    let source_path = remux::ensure_faststart(&args.file, cfg.tmp_dir.as_deref(), args.no_remux)
        .await
        .context("preparing file for splitting")?;
    let total = tokio::fs::metadata(&source_path)
        .await
        .with_context(|| format!("stat {}", source_path.display()))?
        .len();
    let part_ranges = mlib_spec::plan_parts(total, cfg.part_size).context("planning parts")?;

    let set_id = ulid::Ulid::new().to_string();
    let cid = course.as_ref().map(|title| {
        args.cid
            .clone()
            .unwrap_or_else(|| mlib_spec::slug::slug(title))
    });
    let caption = Caption {
        cid,
        chap: args.chap.clone(),
        path: args.path.clone(),
        t: resolved.kind,
        ids: resolved.ids,
        show: resolved.show,
        title: resolved.title,
        year: resolved.year,
        s: resolved.season,
        e: resolved.episode,
        abs: resolved.abs,
        q: info.quality,
        hdr: Some(args.hdr.unwrap_or(info.hdr)),
        container: classify::container_from_ext(&source_path),
        vcodec: info.vcodec,
        acodec: info.acodec,
        alang: args.alang.unwrap_or(info.alang),
        slang: args.slang.unwrap_or(info.slang),
        dur: info.duration_s,
        variant: args.variant,
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

    let created_at = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64;
    let probe = caption.with_part(Part {
        i: part_ranges.len() as u32 - 1,
        n: part_ranges.len() as u32,
        off: total,
        len: total,
        sha256: "0".repeat(64),
    });
    mlib_spec::to_text(&probe, &probe.display_name())
        .context("caption exceeds Telegram's budget; shorten --variant or the language lists")?;
    let set_row = SetRow::from_caption(&caption, created_at)?;

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
    let source_key = format!("source:{set_id}");
    let source_value = source_path
        .canonicalize()
        .unwrap_or_else(|_| source_path.clone())
        .to_string_lossy()
        .into_owned();
    {
        let tx = conn.transaction().context("starting index transaction")?;
        sets::insert_set(&tx, &set_row)?;
        parts::insert_parts(&tx, &set_id, &part_ranges)?;
        db::set_meta(&tx, &source_key, &source_value)?;
        store_sidecars(&tx, &set_id, &args.file, &caption)?;
        if source_path != args.file {
            // A faststart remux was written; remember it so only that file is deleted later.
            db::set_meta(&tx, &format!("tmp:{set_id}"), &source_value)?;
        }
        tx.commit().context("committing index transaction")?;
    }

    // Everything that can ask a question or refuse has happened: the file was
    // inspected, the title resolved, the caption measured, the rows written.
    // What is left is bytes, which is the part worth handing away.
    let to_delete = args.delete_source.then(|| args.file.clone());
    if !args.watch {
        // The child opens the index itself, and two handles on it from one
        // process is one more than the work needs.
        drop(conn);
        // Asked before the child is started, because the child is what will
        // be holding it a moment later.
        let queued = crate::upload::lock::is_held(&data_dir);
        let started =
            background::spawn_finish_set(cfg, &set_id, to_delete.as_deref(), args.no_push)?;
        println!(
            "set {set_id} planned · {} · {:.2} GB",
            caption.display_name(),
            total as f64 / 1e9
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
        return Ok(());
    }
    drop(conn);
    finish_set::run(cfg, &set_id, to_delete.as_deref(), args.no_push).await
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
    source: &std::path::Path,
    caption: &mlib_spec::Caption,
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
