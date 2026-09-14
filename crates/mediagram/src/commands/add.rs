//! `mediagram add`: inspect → resolve → remux → plan → upload parts → index.

use anyhow::{Context, Result, bail};
use mlib_spec::{Caption, Part};

use super::args::AddArgs;
use super::push_index;
use crate::config::Config;
use crate::index::sets::SetRow;
use crate::index::{db, parts, sets};
use crate::media::{classify, inspect, remux};
use crate::metadata::prompt::DialoguerPrompter;
use crate::metadata::resolve::{self, ResolveInput};
use crate::metadata::tmdb_client::TmdbClient;
use crate::telegram::client::Tg;
use crate::upload::pipeline::run_set;
use crate::upload::transport::TelegramTransport;

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

    if !args.manual && cfg.tmdb_key.is_none() {
        bail!("no tmdb_key configured in config.toml; set one or pass --manual");
    }

    let data_dir = cfg.data_dir()?;
    let api = TmdbClient::with_cache(cfg.tmdb_key.as_deref().unwrap_or(""), &data_dir);
    let mut prompter = DialoguerPrompter;
    let resolve_input = ResolveInput {
        file_name,
        tmdb: args.tmdb,
        tvdb: args.tvdb,
        imdb: args.imdb,
        season: args.season,
        episode: args.episode,
        abs: args.abs_no,
        manual: args.manual,
    };
    let resolved = resolve::resolve(&api, &resolve_input, &mut prompter)
        .await
        .context("resolving metadata")?;

    let source_path = remux::ensure_faststart(&args.file, cfg.tmp_dir.as_deref(), args.no_remux)
        .await
        .context("preparing file for splitting")?;
    let total = tokio::fs::metadata(&source_path)
        .await
        .with_context(|| format!("stat {}", source_path.display()))?
        .len();
    let part_ranges = mlib_spec::plan_parts(total, cfg.part_size).context("planning parts")?;

    let set_id = ulid::Ulid::new().to_string();
    let caption = Caption {
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
        if source_path != args.file {
            // A faststart remux was written; remember it so only that file is deleted later.
            db::set_meta(&tx, &format!("tmp:{set_id}"), &source_value)?;
        }
        tx.commit().context("committing index transaction")?;
    }

    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let transport = TelegramTransport::new(&tg, cfg.max_attempts);
    let upload_result = run_set(&conn, &transport, cfg.throttle_ms, &set_row, &source_path).await;
    tg.shutdown().await;
    upload_result.context("uploading set")?;

    let completed = parts::pending_parts(&conn, &set_id)?.is_empty();
    if completed {
        db::delete_meta(&conn, &source_key)?;
    }

    println!("set {set_id} added");
    if completed && !args.no_push {
        push_index::push_after_set(cfg).await?;
    }
    Ok(())
}
