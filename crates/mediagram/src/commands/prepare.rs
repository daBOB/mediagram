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
//! [`crate::media::prepare_check`] before it is kept.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use tokio::process::Command;

use super::args::PrepareArgs;
use crate::config::Config;
use crate::media::direct_play;
use crate::media::ffmpeg_progress;
use crate::media::prepare_check::check_prepared;
use crate::media::prepare_plan::{PreparePlan, StreamKind, Verdict, plan_prepare};
use crate::media::streams;
use crate::media::video_files::{PREPARE_WORKING_SUFFIX, collect_videos};
use crate::term;

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

    let mut planned = Vec::new();
    for (index, file) in files.iter().enumerate() {
        // A folder of a season takes a probe each, and until the table
        // appears there is otherwise nothing to say it is doing anything.
        term::redraw(&format!(
            "  probing {}/{} · {}",
            index + 1,
            files.len(),
            truncate(&name(file), 44)
        ));
        let probed = streams::probe(file).await?;
        let size = probed
            .size
            .unwrap_or_else(|| std::fs::metadata(file).map(|m| m.len()).unwrap_or(0));
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
    term::redraw("");

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
        .filter(|(file, _, _, plan)| {
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

    let mut rewritten = 0usize;
    let mut failed = 0usize;
    let mut freed = 0u64;
    for (index, (file, size, duration, plan)) in todo.iter().enumerate() {
        let dest = match &args.out {
            Some(root) => Some(mirrored(file, &args.path, root, args.mp4)?),
            None => None,
        };
        match rewrite(
            file,
            *size,
            plan,
            &keep_audio,
            dest.as_deref(),
            args.mp4,
            ffmpeg_progress::Job {
                index,
                total: todo.len(),
                name: truncate(&name(file), 44),
                duration: *duration,
            },
        )
        .await
        {
            Ok(new_size) => {
                rewritten += 1;
                println!(
                    "{}: {:.2} GB -> {:.2} GB",
                    name(file),
                    *size as f64 / 1e9,
                    new_size as f64 / 1e9
                );
                // Only here: `rewrite` returns `Ok` after the replacement
                // has passed every check and been renamed into place, so
                // there is something to delete the original in favour of.
                if args.delete_source {
                    match std::fs::remove_file(file) {
                        Ok(()) => freed += *size,
                        Err(err) => println!("  {} kept: {err}", name(file)),
                    }
                }
            }
            Err(err) => {
                failed += 1;
                println!("{}: left unchanged: {err:#}", name(file));
            }
        }
    }
    let verb = if args.out.is_some() {
        "written"
    } else {
        "rewritten"
    };
    println!("\n{rewritten} {verb}, {failed} left unchanged");
    if freed > 0 {
        println!("{:.1} GB freed by deleting the sources", freed as f64 / 1e9);
    }
    if failed > 0 {
        bail!("{failed} file(s) could not be prepared");
    }
    Ok(())
}

/// Writes the pruned copy, checks it, and only then puts it where it belongs.
///
/// `dest` of `None` means replacing the source, which is why the working file
/// always sits beside the source: the rename that finishes the job is only
/// atomic within one filesystem.
async fn rewrite(
    source: &Path,
    source_size: u64,
    plan: &PreparePlan,
    keep_audio: &[String],
    dest: Option<&Path>,
    to_mp4: bool,
    job: ffmpeg_progress::Job,
) -> Result<u64> {
    let final_path = dest.unwrap_or(source);
    let working = working_path(final_path);
    if let Some(parent) = working.parent() {
        std::fs::create_dir_all(parent)
            .with_context(|| format!("creating {}", parent.display()))?;
    }
    let _ = std::fs::remove_file(&working);

    let mut command = Command::new("ffmpeg");
    // `-progress pipe:1` is what makes the copy visible: ffmpeg's own stats
    // go to stderr and are suppressed here anyway, and a key=value stream on
    // stdout is something [`ffmpeg_progress`] can read without guessing.
    command.args([
        "-nostdin", "-v", "error", "-nostats", "-progress", "pipe:1", "-y", "-i",
    ]);
    command.arg(source);
    command.args(plan.map_args());
    if to_mp4 {
        // The picture is always copied. The audio is re-encoded only when it
        // has to be: a track a browser already plays is copied too, because
        // turning AAC into AAC costs a generation of quality to change
        // nothing. `-f mp4` is explicit because the working name ends in
        // `.prepared`, which tells ffmpeg nothing about the format wanted.
        command.args(["-c:v", "copy"]);
        if audio_needs_encoding(plan) {
            command.args(["-c:a", "aac", "-b:a", "384k"]);
        } else {
            command.args(["-c:a", "copy"]);
        }
        command.args(["-c:s", "mov_text", "-movflags", "+faststart", "-f", "mp4"]);
    } else {
        command.args(["-c", "copy"]);
    }
    command.arg(&working);
    if let Err(err) = ffmpeg_progress::run(command, &job)
        .await
        .with_context(|| format!("running ffmpeg on {}", source.display()))
    {
        let _ = std::fs::remove_file(&working);
        return Err(err);
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
        job.duration,
        &expected,
        // Re-encoding the audio can round upwards on a file that had little
        // to drop, so growth is only suspicious when tracks were merely cut.
        to_mp4,
    ) {
        let _ = std::fs::remove_file(&working);
        bail!("prepared file rejected: {rejection:?}");
    }

    // Same directory as the destination, so this is atomic: either the old
    // file or the finished one is in place, never a half-written one.
    std::fs::rename(&working, final_path)
        .with_context(|| format!("writing {}", final_path.display()))?;
    Ok(new_size)
}

/// Where a source file lands under `--out`, keeping the tree it came from.
///
/// A file named directly has no tree to mirror — stripping the root off it
/// leaves nothing — so it lands at the top of the output directory under its
/// own name. Without that case the output directory is itself renamed into
/// the result.
fn mirrored(file: &Path, root: &Path, out: &Path, to_mp4: bool) -> Result<PathBuf> {
    let relative = match file.strip_prefix(root) {
        Ok(rest) if !rest.as_os_str().is_empty() => rest.to_path_buf(),
        _ => PathBuf::from(
            file.file_name()
                .with_context(|| format!("{} has no file name", file.display()))?,
        ),
    };
    let mut dest = out.join(relative);
    if to_mp4 {
        dest.set_extension("mp4");
    }
    if dest == file {
        bail!(
            "{} would be written over its own source; give --out a different directory",
            dest.display()
        );
    }
    Ok(dest)
}

/// Whether any audio track being kept is one a browser would refuse.
///
/// Asked of the tracks that survive the plan, not of the file: dropping the
/// E-AC-3 commentary and keeping the AAC is a file that needs no encoding.
fn audio_needs_encoding(plan: &PreparePlan) -> bool {
    plan.keep
        .iter()
        .filter(|s| s.kind == StreamKind::Audio)
        .any(|s| {
            s.codec
                .as_deref()
                .is_none_or(|codec| !direct_play::known(&direct_play::AUDIO, codec))
        })
}

/// Says which files `--mp4` cannot make direct-playable, and why.
///
/// Converting the wrapper of an HEVC file is wasted work: the player converts
/// it on every play regardless, because the picture itself is what a browser
/// will not open. Better to say so before an hour of encoding than after.
fn warn_about_video_codecs(planned: &[(PathBuf, u64, f64, PreparePlan)]) {
    let mut names: Vec<String> = planned
        .iter()
        .flat_map(|(file, _, _, plan)| direct_play::blockers(file, &plan.keep))
        .filter(|blocker| !blocker.fixable_by_prepare())
        .map(|blocker| blocker.reason())
        .collect();
    let count = names.len();
    if count == 0 {
        return;
    }
    names.sort_unstable();
    names.dedup();
    println!(
        "\nwarning: {count} file(s) carry {}, which a browser will not open. \
         Changing the wrapper does not change that — the picture itself would \
         have to be re-encoded, so these will still be converted on every play.",
        names.join(" / ")
    );
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

fn working_path(source: &Path) -> PathBuf {
    let stem = source
        .file_stem()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_else(|| "output".to_string());
    source.with_file_name(format!("{stem}{PREPARE_WORKING_SUFFIX}"))
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
