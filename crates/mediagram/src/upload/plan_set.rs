//! Planning one file as a set: inspect, resolve, remux, then write it to the
//! index ready to upload. `add` runs this for the file it is handed, and
//! `add-show` and `add-course` for each file they walk.

use anyhow::{Context, Result, bail};
use mlib_spec::{Caption, Part};

use crate::config::Config;
use crate::index::{db, shows};
use crate::media::{classify, inspect, remux};
use crate::metadata::prompt::DialoguerPrompter;
use crate::metadata::resolve::{self, ResolveInput};
use crate::metadata::title_details;
use crate::upload::new_set::{NewSet, Planned};
use crate::upload::plan::{Source, record_planned};

/// Inspects, resolves and remuxes one file and writes it to the index as a
/// set ready to upload.
pub async fn plan_set(cfg: &Config, new: &NewSet) -> Result<Planned> {
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
    let api = cfg.tmdb_client(mediagram_core::http::client()?)?;
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
    let title_id = lesson.is_none().then_some(resolved.ids.tmdb).flatten();
    let title_kind = resolved.kind;

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
    if let Some(id) = title_id {
        match title_details::fetch(&api, title_kind, id, &cfg.tmdb_language).await {
            Ok(row) => shows::upsert(&conn, &row)?,
            Err(err) => tracing::warn!(id, error = %err, "no description recorded for this title"),
        }
    }
    let source = Source {
        path: &source_path,
        remux: source_path != new.file,
    };
    record_planned(&mut conn, &caption, &part_ranges, source, |tx| {
        crate::course::sidecars::store_sidecars(tx, &set_id, &new.file, &caption)
    })?;

    Ok(Planned {
        display_name: caption.display_name(),
        set_id,
        total,
    })
}
