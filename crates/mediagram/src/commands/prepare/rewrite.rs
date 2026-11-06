//! Writing one file's pruned copy with ffmpeg, checking it, and putting it
//! where it belongs — for every file a run selected.

use std::path::Path;

use anyhow::{Context, Result, bail};
use tokio::process::Command;

use super::Candidate;
use super::report::truncate;
use crate::commands::args::PrepareArgs;
use crate::media::ffmpeg_progress;
use crate::media::prepare::check::{Measured, check_prepared};
use crate::media::prepare::paths::{mirrored, replaced, working_path};
use crate::media::prepare::plan::PreparePlan;
use crate::media::streams;
use crate::paths::file_name;

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
    for (
        index,
        Candidate {
            file,
            size,
            duration,
            plan,
        },
    ) in todo.iter().enumerate()
    {
        let dest = match &args.out {
            Some(root) => Ok(mirrored(file, &args.path, root, args.mp4)?),
            None => replaced(file, args.mp4),
        };
        let outcome = async {
            let dest = dest?;
            let job = ffmpeg_progress::Job {
                index,
                total: todo.len(),
                name: truncate(&file_name(file), 44),
                duration: *duration,
            };
            let new_size = rewrite(file, *size, plan, keep_audio, &dest, args.mp4, job).await?;
            Ok::<_, anyhow::Error>((dest, new_size))
        }
        .await;
        match outcome {
            Ok((dest, new_size)) => {
                rewritten += 1;
                println!(
                    "{}: {:.2} GB -> {:.2} GB",
                    file_name(file),
                    *size as f64 / 1e9,
                    new_size as f64 / 1e9
                );
                // Only here: `rewrite` returns `Ok` after the replacement
                // has passed every check and been renamed into place, so
                // there is something to delete the original in favour of.
                // A `--replace` that renamed `.mkv` to `.mp4` left the
                // original beside it, and replacing means it goes too.
                let renamed = args.replace && dest != *file;
                if args.delete_source || renamed {
                    match std::fs::remove_file(file) {
                        Ok(()) if args.delete_source => freed += *size,
                        Ok(()) => {}
                        Err(err) => println!("  {} kept: {err}", file_name(file)),
                    }
                }
            }
            Err(err) => {
                failed += 1;
                println!("{}: left unchanged: {err:#}", file_name(file));
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

/// Channel layouts ffmpeg's AAC encoder takes. It refuses others outright, and
/// Dolby Atmos decodes as one (5.1.2, 7.1.4); offered this list, ffmpeg folds
/// the height channels into 5.1 or 7.1 and leaves the listed ones as they are.
/// Long-standing names only, so an older ffmpeg parses the list too.
const AAC_LAYOUTS: &str = "aformat=channel_layouts=mono|stereo|2.1|3.0|3.0(back)|4.0|quad|3.1|5.0|4.1|5.1|6.0|hexagonal|6.1|6.1(back)|7.0|7.1|7.1(wide)|octagonal";

/// Writes the pruned copy, checks it, and only then renames it to
/// `final_path`, which may be the source itself.
///
/// The working file sits beside `final_path` because the rename that
/// finishes the job is only atomic within one filesystem.
async fn rewrite(
    source: &Path,
    source_size: u64,
    plan: &PreparePlan,
    keep_audio: &[String],
    final_path: &Path,
    to_mp4: bool,
    job: ffmpeg_progress::Job,
) -> Result<u64> {
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
        "-nostdin",
        "-v",
        "error",
        "-nostats",
        "-progress",
        "pipe:1",
        "-y",
        "-i",
    ]);
    command.arg(source);
    command.args(plan.map_args(to_mp4));
    if to_mp4 {
        // The picture is always copied. The audio is re-encoded only when it
        // has to be: a track a browser already plays is copied too, because
        // turning AAC into AAC costs a generation of quality to change
        // nothing. `-f mp4` is explicit because the working name ends in
        // `.prepared`, which tells ffmpeg nothing about the format wanted.
        command.args(["-c:v", "copy"]);
        if plan.audio_needs_encoding() {
            command.args(["-c:a", "aac", "-b:a", "384k", "-af", AAC_LAYOUTS]);
        } else {
            command.args(["-c:a", "copy"]);
        }
        command.args(["-c:s", "mov_text", "-movflags", "+faststart", "-f", "mp4"]);
    } else {
        command.args(["-c", "copy"]);
    }
    command.arg(&working);
    // Every failure from here to the check leaves an unverified copy, often
    // gigabytes, beside the source; one exit path removes it whichever step
    // failed.
    let new_size = async {
        ffmpeg_progress::run(command, &job)
            .await
            .with_context(|| format!("running ffmpeg on {}", source.display()))?;
        let probed = streams::probe(&working).await?;
        let new_size = std::fs::metadata(&working)
            .with_context(|| format!("sizing {}", working.display()))?
            .len();
        let expected = plan.expected_audio_languages(keep_audio);

        let prepared = Measured {
            size: new_size,
            duration: probed.duration,
        };
        let original = Measured {
            size: source_size,
            duration: job.duration,
        };
        // Growth is tolerated on every MP4 run: rewrapping as MP4 (mov_text
        // subtitles, MP4 indexes, faststart) and re-encoding the audio can
        // both enlarge a file, while a remux that only drops tracks cannot.
        if let Err(rejection) =
            check_prepared(&probed.streams, prepared, original, &expected, to_mp4)
        {
            bail!("prepared file rejected: {rejection:?}");
        }
        Ok::<_, anyhow::Error>(new_size)
    }
    .await
    .inspect_err(|_| {
        let _ = std::fs::remove_file(&working);
    })?;

    // Same directory as the destination, so this is atomic: either the old
    // file or the finished one is in place, never a half-written one.
    std::fs::rename(&working, final_path)
        .with_context(|| format!("writing {}", final_path.display()))?;
    Ok(new_size)
}
