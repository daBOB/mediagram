//! The line `mediagram prepare` keeps on the terminal while ffmpeg works.
//!
//! At `-v error` ffmpeg says nothing at all until it is finished, so a season
//! of remuxes looks like a hung command for an hour — which is exactly how it
//! looked to the person who typed it. `-progress pipe:1` makes ffmpeg report
//! its position on stdout a couple of times a second as plain `key=value`
//! lines. This module reads those and turns them into one line that rewrites
//! itself, the same shape the upload command draws.
//!
//! Nothing here changes what ffmpeg does: it runs the command it is handed and
//! returns what `status()` would have. What it does change is where ffmpeg's
//! complaints go — into the error, rather than smeared across the line being
//! redrawn underneath them.

use std::process::Stdio;
use std::time::{Duration, Instant};

use anyhow::{Context, Result, bail};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Command;

use crate::term;

/// What the line says about the work, as opposed to how far it has got.
pub struct Job {
    /// Which file of how many, zero-based. A run over one file says neither.
    pub index: usize,
    pub total: usize,
    /// The name to show, already shortened by the caller if it should be.
    pub name: String,
    /// Length of the source in seconds, or `0.0` when the probe did not say —
    /// without it there is a position but no percentage and no eta.
    pub duration: f64,
}

/// Runs `command`, drawing where it has got to, and fails with what ffmpeg
/// said rather than only with its exit status.
///
/// The caller is responsible for putting `-progress pipe:1 -nostats` on the
/// command line; without them this simply draws nothing.
pub async fn run(mut command: Command, job: &Job) -> Result<()> {
    command.stdout(Stdio::piped()).stderr(Stdio::piped());
    let mut child = command.spawn().context("starting ffmpeg")?;
    let stdout = child.stdout.take().expect("stdout was piped");
    let stderr = child.stderr.take().expect("stderr was piped");

    // Drained on its own task: a full pipe blocks the process writing into
    // it, so a run that complains a lot would hang waiting to be read.
    let complaints = tokio::spawn(async move {
        let mut text = String::new();
        let mut lines = BufReader::new(stderr).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            text.push_str(&line);
            text.push('\n');
        }
        text
    });

    let started = Instant::now();
    let mut at = Position::default();
    let mut lines = BufReader::new(stdout).lines();
    while let Some(line) = lines
        .next_line()
        .await
        .context("reading ffmpeg's progress")?
    {
        if at.feed(&line) {
            term::redraw(&render(job, &at, started.elapsed()));
        }
    }

    let status = child.wait().await.context("waiting for ffmpeg")?;
    term::redraw("");
    if !status.success() {
        let complaints = complaints.await.unwrap_or_default();
        match complaints.trim().lines().next_back() {
            Some(why) => bail!("ffmpeg exited with {status}: {why}"),
            None => bail!("ffmpeg exited with {status}"),
        }
    }
    Ok(())
}

/// How far into the source ffmpeg has read, and how much it has written.
#[derive(Default)]
struct Position {
    out_time_us: u64,
    total_size: u64,
}

impl Position {
    /// Absorbs one `key=value` line and says whether it ended a block.
    ///
    /// ffmpeg closes every block with `progress=continue`, or `progress=end`
    /// for the last one. That is the only moment at which the fields above it
    /// describe the same instant, so it is the only moment worth drawing.
    /// Early blocks can carry `N/A` instead of a number, which leaves the
    /// previous value standing rather than resetting the line to zero.
    fn feed(&mut self, line: &str) -> bool {
        let Some((key, value)) = line.split_once('=') else {
            return false;
        };
        let value = value.trim();
        match key.trim() {
            "out_time_us" => {
                self.out_time_us = value.parse().unwrap_or(self.out_time_us);
                false
            }
            "total_size" => {
                self.total_size = value.parse().unwrap_or(self.total_size);
                false
            }
            "progress" => true,
            _ => false,
        }
    }
}

/// The line itself. Pure, so what it says can be checked without ffmpeg.
fn render(job: &Job, at: &Position, elapsed: Duration) -> String {
    let done = at.out_time_us as f64 / 1e6;
    // One file is its own count, and saying "[1/1]" says nothing.
    let which = if job.total > 1 {
        format!("[{}/{}] ", job.index + 1, job.total)
    } else {
        String::new()
    };
    let through = if job.duration > 0.0 {
        format!(
            "{:.1} of {:.1} min ({}%)",
            done / 60.0,
            job.duration / 60.0,
            term::percent(at.out_time_us, (job.duration * 1e6) as u64)
        )
    } else {
        format!("{:.1} min", done / 60.0)
    };
    // Nothing is written until the first block is muxed, and "0.00 GB" reads
    // like a stall rather than like a start.
    let written = if at.total_size > 0 {
        format!(" · {:.2} GB", at.total_size as f64 / 1e9)
    } else {
        String::new()
    };
    let rate = rate_per_second(done, elapsed);
    let speed = match rate {
        Some(times) => format!(" · {times:.0}x"),
        None => String::new(),
    };
    let eta = match eta_seconds(job.duration, done, rate) {
        Some(seconds) => format!(" · eta {}", term::human_duration(seconds)),
        None => String::new(),
    };
    format!("  {which}{} · {through}{written}{speed}{eta}", job.name)
}

/// Seconds of source consumed per second of waiting — what ffmpeg calls
/// `speed`, measured here instead so it is one number over the whole file
/// rather than the last half second of it.
///
/// `None` until there is enough of a sample to divide by: a rate quoted off
/// the first fraction of a second is noise presented as a measurement.
fn rate_per_second(done: f64, elapsed: Duration) -> Option<f64> {
    let seconds = elapsed.as_secs_f64();
    (seconds >= 1.0 && done > 0.0).then(|| done / seconds)
}

/// Time left on this file. A file whose length nobody knows has none to quote.
fn eta_seconds(duration: f64, done: f64, rate: Option<f64>) -> Option<u64> {
    let rate = rate?;
    if duration <= 0.0 || rate <= 0.0 {
        return None;
    }
    Some(((duration - done).max(0.0) / rate).round() as u64)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn job(index: usize, total: usize, duration: f64) -> Job {
        Job {
            index,
            total,
            name: "S01E03.mkv".to_string(),
            duration,
        }
    }

    fn at(minutes: f64, bytes: u64) -> Position {
        Position {
            out_time_us: (minutes * 60.0 * 1e6) as u64,
            total_size: bytes,
        }
    }

    #[test]
    fn a_block_is_drawn_only_once_it_is_complete() {
        let mut position = Position::default();
        assert!(!position.feed("frame=125"));
        assert!(!position.feed("out_time_us=4000000"));
        assert!(!position.feed("total_size=1200000000"));
        assert!(position.feed("progress=continue"));
        assert_eq!(position.out_time_us, 4_000_000);
        assert_eq!(position.total_size, 1_200_000_000);
    }

    #[test]
    fn an_unknown_field_leaves_the_last_known_position_standing() {
        let mut position = Position::default();
        position.feed("out_time_us=4000000");
        position.feed("out_time_us=N/A");
        assert_eq!(position.out_time_us, 4_000_000);
    }

    #[test]
    fn one_file_of_a_folder_says_which_one_and_how_far_it_has_to_go() {
        // Four minutes of a 45 minute episode, done in 12 seconds: 20x, so
        // the 41 minutes still to copy take another two.
        let line = render(&job(2, 10, 2700.0), &at(4.0, 1_200_000_000), Duration::from_secs(12));
        assert_eq!(
            line,
            "  [3/10] S01E03.mkv · 4.0 of 45.0 min (9%) · 1.20 GB · 20x · eta 2m03s"
        );
    }

    #[test]
    fn a_single_file_is_not_counted_against_itself() {
        let line = render(&job(0, 1, 2700.0), &at(4.0, 1_200_000_000), Duration::from_secs(12));
        assert!(line.starts_with("  S01E03.mkv · 4.0 of 45.0 min"));
    }

    #[test]
    fn a_source_of_unknown_length_reports_a_position_and_promises_nothing() {
        let line = render(&job(0, 1, 0.0), &at(4.0, 1_200_000_000), Duration::from_secs(12));
        assert_eq!(line, "  S01E03.mkv · 4.0 min · 1.20 GB · 20x");
    }

    #[test]
    fn nothing_written_yet_is_left_off_rather_than_shown_as_zero() {
        let line = render(&job(0, 1, 2700.0), &at(0.0, 0), Duration::from_millis(300));
        assert_eq!(line, "  S01E03.mkv · 0.0 of 45.0 min (0%)");
    }
}
