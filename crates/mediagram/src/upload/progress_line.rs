//! The line an upload keeps on the terminal while it works.
//!
//! A sibling to [`super::progress`], reading the same counter, and separate
//! for the same reason the note on disk is separate: that one exists so
//! another process can ask how far this one has got, and it is written
//! whether or not anybody is watching. This one is for the person who typed
//! the command and is looking at a cursor that has not moved in two minutes.
//!
//! It draws only onto a terminal. Redrawing a line with carriage returns
//! into a pipe or a log file produces one long unreadable line, so there the
//! upload stays quiet and `tracing` remains the record.

use std::sync::Arc;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, Instant};

use super::progress::Progress;
use crate::term;

/// How often the line is redrawn. Fast enough to look alive, slow enough
/// that a scrollback recording of the run stays readable.
const INTERVAL: Duration = Duration::from_secs(1);

/// Redraws one line while `counter` moves, until the handle drops.
pub struct Line {
    task: tokio::task::JoinHandle<()>,
}

impl Line {
    /// Starts drawing, or returns `None` when stdout is not a terminal.
    pub fn start(counter: Arc<AtomicU64>, shape: Progress) -> Option<Line> {
        if !term::watching() {
            return None;
        }
        let task = tokio::spawn(async move {
            let started = Instant::now();
            let mut ticker = tokio::time::interval(INTERVAL);
            loop {
                ticker.tick().await;
                let current = Progress {
                    bytes_sent: counter.load(Ordering::Relaxed),
                    ..shape.clone()
                };
                term::redraw(&render(&current, started.elapsed()));
            }
        });
        Some(Line { task })
    }
}

impl Drop for Line {
    fn drop(&mut self) {
        self.task.abort();
        // Leave the cursor on a blank line, so whatever prints next — the
        // next part's line, or what the command has to say — starts clean.
        term::redraw("");
    }
}

/// The line itself. Pure, so what it says can be checked without a terminal.
fn render(p: &Progress, elapsed: Duration) -> String {
    let sent = p.bytes_sent;
    let rate = rate_per_second(sent, elapsed);
    let part = format!(
        "  part {}/{} · {:.2} of {:.2} GB",
        p.part + 1,
        p.parts,
        gb(sent),
        gb(p.part_bytes),
    );
    // With one part, the set is the part, and saying so twice says nothing.
    let share = if p.parts == 1 {
        format!(" ({}%)", term::percent(sent, p.part_bytes))
    } else {
        format!(" · set {}%", term::percent(p.set_bytes_sent(), p.set_bytes))
    };
    let speed = match rate {
        Some(bytes_per_s) => format!(" · {:.1} MB/s", bytes_per_s / 1e6),
        None => String::new(),
    };
    let eta = match eta_seconds(p, rate) {
        Some(seconds) => format!(" · eta {}", term::human_duration(seconds)),
        None => String::new(),
    };
    format!("{part}{share}{speed}{eta}")
}

fn gb(bytes: u64) -> f64 {
    bytes as f64 / 1e9
}

/// `None` until there is enough of a sample to divide by: a rate quoted off
/// the first fraction of a second is noise presented as a measurement.
fn rate_per_second(sent: u64, elapsed: Duration) -> Option<f64> {
    let seconds = elapsed.as_secs_f64();
    (seconds >= 1.0 && sent > 0).then(|| sent as f64 / seconds)
}

/// Time to the end of the whole set, not of this part: what is wanted is
/// when the command finishes, and the parts after this one are also its work.
fn eta_seconds(p: &Progress, rate: Option<f64>) -> Option<u64> {
    let rate = rate?;
    if rate <= 0.0 {
        return None;
    }
    let remaining = p.set_bytes.saturating_sub(p.set_bytes_sent());
    Some((remaining as f64 / rate).round() as u64)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn progress(part: u32, parts: u32, sent: u64) -> Progress {
        Progress {
            set_id: "01ABC".to_string(),
            part,
            parts,
            bytes_sent: sent,
            part_bytes: 3_400_000_000,
            bytes_done: part as u64 * 3_400_000_000,
            set_bytes: parts as u64 * 3_400_000_000,
            updated_at: 0,
        }
    }

    #[test]
    fn a_single_part_set_reports_one_percentage() {
        let line = render(&progress(0, 1, 1_900_000_000), Duration::from_secs(100));
        assert_eq!(
            line,
            "  part 1/1 · 1.90 of 3.40 GB (56%) · 19.0 MB/s · eta 1m19s"
        );
    }

    #[test]
    fn a_multi_part_set_reports_the_whole_set_too() {
        // Second of four parts, half of it sent: 5.1 of 13.6 GB is 38% of the
        // set, and the eta covers the two parts that have not started.
        let line = render(&progress(1, 4, 1_700_000_000), Duration::from_secs(100));
        assert_eq!(
            line,
            "  part 2/4 · 1.70 of 3.40 GB · set 38% · 17.0 MB/s · eta 8m20s"
        );
    }

    #[test]
    fn no_rate_is_quoted_before_there_is_a_second_to_divide_by() {
        let line = render(&progress(0, 1, 40_000_000), Duration::from_millis(200));
        assert_eq!(line, "  part 1/1 · 0.04 of 3.40 GB (1%)");
    }
}
