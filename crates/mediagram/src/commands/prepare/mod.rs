//! `mediagram prepare`: drop unwanted audio and subtitle tracks so a file
//! fits in a single upload part, and optionally convert it to something a
//! browser can open without the player converting it on every play.
//!
//! The video is copied untouched either way, so the picture is bit-identical
//! and a season takes minutes. What `--mp4` changes is the wrapper and the
//! audio codec, which is what actually blocks direct play: a Matroska file
//! with E-AC-3 is converted for every viewer, every time, and re-encodes a
//! perfectly good H.264 picture to do it.
//!
//! Replacing the original is irreversible, so it happens only with
//! `--replace`; `--out` writes a parallel tree instead and leaves the sources
//! alone. Either way the new file must pass every check in
//! [`crate::media::prepare::check`] before it is kept.

mod report;
mod rewrite;

use std::path::PathBuf;

use anyhow::{Result, bail};

use super::args::PrepareArgs;
use crate::config::Config;
use crate::media::direct_play;
use crate::media::prepare::plan::{PreparePlan, Verdict, plan_prepare};
use crate::media::streams;
use crate::media::video_files::collect_videos;
use crate::term;
use crate::paths::file_name;
use report::{print_table, truncate, warn_about_video_codecs};
use rewrite::rewrite_all;

/// One probed file and what `prepare` would do with it.
struct Candidate {
    file: PathBuf,
    size: u64,
    /// Seconds, as probed.
    duration: f64,
    plan: PreparePlan,
}

pub async fn run(cfg: &Config, args: PrepareArgs) -> Result<()> {
    if args.replace && args.out.is_some() {
        bail!("--replace rewrites the originals and --out leaves them alone; pick one");
    }
    // With `--replace` the source *is* the destination, so deleting it would
    // delete the result. The flag only means anything when the two differ.
    if args.delete_source && args.out.is_none() {
        bail!("--delete-source needs --out: with --replace the original is already gone");
    }
    let keep_audio = split_languages(&args.audio);
    let keep_subs = split_languages(&args.subs);
    let limit = args.limit.unwrap_or(cfg.part_size);
    let files = collect_videos(&args.path)?;
    if files.is_empty() {
        println!("no video files under {}", args.path.display());
        return Ok(());
    }

    let planned = probe_all(&files, &keep_audio, &keep_subs, limit).await?;

    print_table(&planned, limit);

    if args.mp4 {
        warn_about_video_codecs(&planned);
    }

    if !args.replace && args.out.is_none() {
        println!(
            "\ndry run: nothing was changed. Re-run with --replace to rewrite these files in \
             place, or --out <dir> to write them elsewhere."
        );
        return Ok(());
    }

    // Settled before the first rewrite starts, so the progress line can say
    // which file of how many rather than counting only the ones left.
    let todo: Vec<_> = planned
        .iter()
        .filter(|Candidate { file, plan, .. }| {
            // Size is not the only reason to rewrite. `--mp4` exists to make a
            // file playable, and a file already small enough still plays badly
            // if it is Matroska with E-AC-3 inside.
            let oversized = matches!(
                plan.verdict,
                Verdict::Prepare | Verdict::PrepareStillOversized
            );
            let unplayable = args.mp4 && !direct_play::plays_directly(file, &plan.keep);
            oversized || unplayable
        })
        .collect();

    rewrite_all(&args, &keep_audio, &todo).await
}

/// Probes every file and plans what `prepare` would do with each.
async fn probe_all(
    files: &[PathBuf],
    keep_audio: &[String],
    keep_subs: &[String],
    limit: u64,
) -> Result<Vec<Candidate>> {
    let mut planned = Vec::new();
    for (index, file) in files.iter().enumerate() {
        // A folder of a season takes a probe each, and until the table
        // appears there is otherwise nothing to say it is doing anything.
        term::redraw(&format!(
            "  probing {}/{} · {}",
            index + 1,
            files.len(),
            truncate(&file_name(file), 44)
        ));
        let probed = streams::probe(file).await?;
        let size = probed
            .size
            .unwrap_or_else(|| std::fs::metadata(file).map(|m| m.len()).unwrap_or(0));
        let plan = plan_prepare(
            &probed.streams,
            size,
            probed.duration,
            &borrowed(keep_audio),
            &borrowed(keep_subs),
            limit,
        );
        planned.push(Candidate {
            file: file.clone(),
            size,
            duration: probed.duration,
            plan,
        });
    }
    term::redraw("");
    Ok(planned)
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
