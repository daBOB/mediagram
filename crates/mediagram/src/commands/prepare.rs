//! `mediagram prepare`: drop unwanted audio and subtitle tracks so a file
//! fits in a single upload part.
//!
//! The video is copied untouched, so the picture is bit-identical and a season
//! takes minutes. Replacing the original is irreversible, so it happens only
//! with `--replace`, and only after the new file passes every check in
//! [`crate::media::prepare_check`].

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use tokio::process::Command;

use super::args::PrepareArgs;
use crate::config::Config;
use crate::course::plan::is_video;
use crate::media::prepare_check::check_prepared;
use crate::media::prepare_plan::{PreparePlan, Verdict, plan_prepare};
use crate::media::streams;

/// Suffix of the file written beside the original before it is renamed over
/// it. Shares the `.prepared.` marker so a crashed run leaves something
/// recognisable rather than a plausible-looking video.
const WORKING_SUFFIX: &str = ".prepared.mkv";

pub async fn run(cfg: &Config, args: PrepareArgs) -> Result<()> {
    let keep_audio = split_languages(&args.audio);
    let keep_subs = split_languages(&args.subs);
    let limit = args.limit.unwrap_or(cfg.part_size);
    let files = collect(&args.path)?;
    if files.is_empty() {
        println!("no video files under {}", args.path.display());
        return Ok(());
    }

    let mut planned = Vec::new();
    for file in &files {
        let probed = streams::probe(file).await?;
        let size = probed
            .size
            .unwrap_or(std::fs::metadata(file).map(|m| m.len()).unwrap_or(0));
        let plan = plan_prepare(
            &probed.streams,
            size,
            probed.duration,
            &borrowed(&keep_audio),
            &borrowed(&keep_subs),
            limit,
        );
        planned.push((file.clone(), size, probed.duration, plan));
    }

    print_table(&planned, limit);

    if !args.replace {
        println!(
            "\ndry run: nothing was changed. Re-run with --replace to rewrite these files in place."
        );
        return Ok(());
    }

    let mut rewritten = 0usize;
    let mut failed = 0usize;
    for (file, size, duration, plan) in &planned {
        if !matches!(
            plan.verdict,
            Verdict::Prepare | Verdict::PrepareStillOversized
        ) {
            continue;
        }
        match rewrite(file, *size, *duration, plan, &keep_audio).await {
            Ok(new_size) => {
                rewritten += 1;
                println!(
                    "{}: {:.2} GB -> {:.2} GB",
                    name(file),
                    *size as f64 / 1e9,
                    new_size as f64 / 1e9
                );
            }
            Err(err) => {
                failed += 1;
                println!("{}: left unchanged: {err:#}", name(file));
            }
        }
    }
    println!("\n{rewritten} rewritten, {failed} left unchanged");
    if failed > 0 {
        bail!("{failed} file(s) could not be prepared");
    }
    Ok(())
}

/// Writes the pruned copy, checks it, and only then replaces the original.
async fn rewrite(
    source: &Path,
    source_size: u64,
    source_duration: f64,
    plan: &PreparePlan,
    keep_audio: &[String],
) -> Result<u64> {
    let working = working_path(source);
    let _ = std::fs::remove_file(&working);

    let mut command = Command::new("ffmpeg");
    command.args(["-nostdin", "-v", "error", "-y", "-i"]);
    command.arg(source);
    command.args(plan.map_args());
    command.args(["-c", "copy"]);
    command.arg(&working);
    let status = command
        .status()
        .await
        .with_context(|| format!("running ffmpeg on {}", source.display()))?;
    if !status.success() {
        let _ = std::fs::remove_file(&working);
        bail!("ffmpeg exited with {status}");
    }

    let probed = streams::probe(&working).await?;
    let new_size = std::fs::metadata(&working)
        .with_context(|| format!("sizing {}", working.display()))?
        .len();
    // Languages actually present in the source decide what the output must
    // keep: demanding a language the source never had would reject every
    // correct result.
    let expected = keep_audio
        .iter()
        .filter(|lang| {
            plan.keep.iter().any(|s| {
                s.language
                    .as_deref()
                    .is_some_and(|l| l.eq_ignore_ascii_case(lang))
            })
        })
        .cloned()
        .collect::<Vec<_>>();

    if let Err(rejection) = check_prepared(
        &probed.streams,
        new_size,
        probed.duration,
        source_size,
        source_duration,
        &expected,
    ) {
        let _ = std::fs::remove_file(&working);
        bail!("prepared file rejected: {rejection:?}");
    }

    // Same directory, so this is atomic: either the original or the prepared
    // file is in place, never a half-written one.
    std::fs::rename(&working, source).with_context(|| format!("replacing {}", source.display()))?;
    Ok(new_size)
}

fn print_table(planned: &[(PathBuf, u64, f64, PreparePlan)], limit: u64) {
    println!(
        "{:<44} {:>9} {:>6} {:>5} {:>11}  verdict",
        "file", "size", "audio", "subs", "estimated"
    );
    for (file, size, _, plan) in planned {
        let verdict = match plan.verdict {
            Verdict::AlreadyFits => "already fits".to_string(),
            Verdict::NothingToDrop => "nothing to drop".to_string(),
            Verdict::Prepare => "prepare".to_string(),
            Verdict::PrepareStillOversized => "prepare, still needs 2 parts".to_string(),
        };
        println!(
            "{:<44} {:>8.2}G {:>6} {:>5} {:>10.2}G  {}",
            truncate(&name(file), 44),
            *size as f64 / 1e9,
            plan.dropped_audio,
            plan.dropped_subtitles,
            plan.estimated_bytes as f64 / 1e9,
            verdict
        );
    }
    let total: u64 = planned.iter().map(|(_, s, _, _)| *s).sum();
    let after: u64 = planned
        .iter()
        .map(|(_, s, _, p)| match p.verdict {
            Verdict::Prepare | Verdict::PrepareStillOversized => p.estimated_bytes,
            _ => *s,
        })
        .sum();
    println!(
        "\n{:.1} GB -> {:.1} GB, saving {:.1} GB. One part is {:.2} GB.",
        total as f64 / 1e9,
        after as f64 / 1e9,
        (total - after) as f64 / 1e9,
        limit as f64 / 1e9
    );
}

fn collect(path: &Path) -> Result<Vec<PathBuf>> {
    if path.is_file() {
        return Ok(vec![path.to_path_buf()]);
    }
    let mut out = Vec::new();
    walk(path, &mut out)?;
    out.sort();
    Ok(out)
}

fn walk(dir: &Path, out: &mut Vec<PathBuf>) -> Result<()> {
    let entries = std::fs::read_dir(dir).with_context(|| format!("reading {}", dir.display()))?;
    for entry in entries {
        let entry = entry.context("reading a directory entry")?;
        let file_type = entry.file_type().context("typing a directory entry")?;
        if file_type.is_dir() {
            walk(&entry.path(), out)?;
        } else if file_type.is_file() {
            let name = entry.file_name().to_string_lossy().to_string();
            // `is_video` already skips our own remux temporaries.
            if is_video(&name) && !name.ends_with(WORKING_SUFFIX) {
                out.push(entry.path());
            }
        }
    }
    Ok(())
}

fn working_path(source: &Path) -> PathBuf {
    let stem = source
        .file_stem()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_else(|| "output".to_string());
    source.with_file_name(format!("{stem}{WORKING_SUFFIX}"))
}

fn split_languages(raw: &str) -> Vec<String> {
    raw.split(',')
        .map(|s| s.trim().to_ascii_lowercase())
        .filter(|s| !s.is_empty())
        .collect()
}

fn borrowed(owned: &[String]) -> Vec<&str> {
    owned.iter().map(String::as_str).collect()
}

fn name(path: &Path) -> String {
    path.file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_default()
}

fn truncate(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        return s.to_string();
    }
    s.chars().take(max - 1).collect::<String>() + "…"
}
