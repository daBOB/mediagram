//! Writing one file's pruned copy with ffmpeg, checking it, and putting it
//! where it belongs — for every file a run selected.

use std::path::Path;

use anyhow::{Context, Result, bail};
use tokio::process::Command;

use super::Candidate;
use super::report::{name, truncate};
use crate::commands::args::PrepareArgs;
use crate::media::direct_play;
use crate::media::ffmpeg_progress;
use crate::media::prepare_check::{Measured, check_prepared};
use crate::media::prepare_paths::{mirrored, working_path};
use crate::media::prepare_plan::{PreparePlan, StreamKind};
use crate::media::streams;

/// Rewrites every selected file in turn, reporting each, and fails the run
/// if any could not be prepared.
pub(super) async fn rewrite_all(
    args: &PrepareArgs,
    keep_audio: &[String],
    todo: &[&Candidate],
) -> Result<()> {
    let mut rewritten = 0usize;
    let mut failed = 0usize;
    let mut freed = 0u64;
    for (index, Candidate { file, size, duration, plan }) in todo.iter().enumerate() {
        let dest = match &args.out {
            Some(root) => Some(mirrored(file, &args.path, root, args.mp4)?),
            None => None,
        };
        match rewrite(
            file,
            *size,
            plan,
            keep_audio,
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

    let prepared = Measured {
        size: new_size,
        duration: probed.duration,
    };
    let original = Measured {
        size: source_size,
        duration: job.duration,
    };
    // Re-encoding the audio can round upwards on a file that had little to
    // drop, so growth is only suspicious when tracks were merely cut.
    if let Err(rejection) = check_prepared(&probed.streams, prepared, original, &expected, to_mp4) {
        let _ = std::fs::remove_file(&working);
        bail!("prepared file rejected: {rejection:?}");
    }

    // Same directory as the destination, so this is atomic: either the old
    // file or the finished one is in place, never a half-written one.
    std::fs::rename(&working, final_path)
        .with_context(|| format!("writing {}", final_path.display()))?;
    Ok(new_size)
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
