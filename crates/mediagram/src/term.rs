//! The few things that only make sense while a person is watching.
//!
//! A progress line rewrites itself with carriage returns, which is right on a
//! terminal and unreadable in a pipe or a log file — there it produces one
//! enormous line of near-identical text. So drawing is a no-op when stdout is
//! not a terminal, and `tracing` stays the record.
//!
//! Shared because `prepare` and `upload` both keep a line alive while they
//! work, and a percentage or an eta should read the same in both.

use std::io::{IsTerminal, Write};
use std::time::{Duration, Instant};

/// Progress for a program reading stdout rather than a person: whole
/// `progress <step> <done> <total>` lines, since a redraw is unreadable in a
/// pipe. Only when `MEDIAGRAM_PROGRESS=lines` asks (the web player does), so
/// a log or a script reading the summary line sees nothing new; an older
/// build ignores the variable and its reader simply gets no progress.
pub struct ProgressLines {
    on: bool,
    last: Option<Instant>,
}

/// A reader redraws a bar from these, so four a second is plenty.
const PROGRESS_GAP: Duration = Duration::from_millis(250);

impl ProgressLines {
    pub fn from_env() -> Self {
        Self::new(std::env::var("MEDIAGRAM_PROGRESS").is_ok_and(|v| v == "lines"))
    }

    fn new(on: bool) -> Self {
        Self { on, last: None }
    }

    /// Prints the step's first and last line, and the ones between at most
    /// every [`PROGRESS_GAP`].
    pub fn report(&mut self, step: &str, done: usize, total: usize) {
        if let Some(line) = self.line(step, done, total, Instant::now()) {
            println!("{line}");
        }
    }

    fn line(&mut self, step: &str, done: usize, total: usize, now: Instant) -> Option<String> {
        if !self.on {
            return None;
        }
        let edge = done == 0 || done >= total;
        if !edge
            && self
                .last
                .is_some_and(|last| now.duration_since(last) < PROGRESS_GAP)
        {
            return None;
        }
        self.last = Some(now);
        Some(format!("progress {step} {done} {total}"))
    }
}

/// Whether drawing would reach anybody.
pub fn watching() -> bool {
    std::io::stdout().is_terminal()
}

/// Rewrites the current line in place: carriage return, then erase to the end
/// of the line. `""` clears it and leaves the cursor at column zero, so
/// whatever prints next starts clean instead of after a stale tail.
pub fn redraw(text: &str) {
    if !watching() {
        return;
    }
    let mut out = std::io::stdout();
    let _ = write!(out, "\r\x1b[2K{text}");
    let _ = out.flush();
}

/// A whole-number percentage that never runs past 100 or divides by zero.
pub fn percent(part: u64, whole: u64) -> u64 {
    if whole == 0 {
        return 0;
    }
    (part.min(whole) as f64 / whole as f64 * 100.0).round() as u64
}

/// A duration as somebody waiting would say it, not in seconds.
pub fn human_duration(seconds: u64) -> String {
    match seconds {
        s if s < 60 => format!("{s}s"),
        s if s < 3600 => format!("{}m{:02}s", s / 60, s % 60),
        s => format!("{}h{:02}m", s / 3600, (s % 3600) / 60),
    }
}

/// `describing 120/907 (13%) · eta 4m10s`, for a loop over a known number of
/// things. `done` is how many are finished; the line names the one in hand.
/// The eta waits for a first result, because a guess from nothing is noise.
pub fn count_line(verb: &str, done: usize, total: usize, elapsed: std::time::Duration) -> String {
    let mut line = format!(
        "{verb} {}/{total} ({}%)",
        (done + 1).min(total),
        percent(done as u64, total as u64)
    );
    if done > 0 && done < total {
        let left = elapsed.as_secs_f64() / done as f64 * (total - done) as f64;
        line.push_str(&format!(" · eta {}", human_duration(left as u64)));
    }
    line
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_count_line_names_the_item_in_hand_and_estimates_the_rest() {
        use std::time::Duration;
        assert_eq!(
            count_line("describing", 0, 10, Duration::ZERO),
            "describing 1/10 (0%)"
        );
        assert_eq!(
            count_line("describing", 5, 10, Duration::from_secs(50)),
            "describing 6/10 (50%) · eta 50s"
        );
    }

    #[test]
    fn durations_read_as_time() {
        assert_eq!(human_duration(9), "9s");
        assert_eq!(human_duration(75), "1m15s");
        assert_eq!(human_duration(3725), "1h02m");
    }

    #[test]
    fn progress_lines_keep_a_steps_edges_and_thin_out_the_middle() {
        let start = Instant::now();
        let ms = Duration::from_millis;
        let mut lines = ProgressLines::new(true);
        assert_eq!(
            lines.line("lookup", 0, 10, start).as_deref(),
            Some("progress lookup 0 10")
        );
        assert_eq!(lines.line("lookup", 1, 10, start + ms(100)), None);
        assert_eq!(
            lines.line("lookup", 2, 10, start + ms(300)).as_deref(),
            Some("progress lookup 2 10")
        );
        assert_eq!(
            lines.line("lookup", 10, 10, start + ms(310)).as_deref(),
            Some("progress lookup 10 10")
        );
        assert_eq!(ProgressLines::new(false).line("lookup", 0, 10, start), None);
    }

    #[test]
    fn a_percentage_never_runs_past_a_hundred_or_divides_by_zero() {
        assert_eq!(percent(5, 0), 0);
        assert_eq!(percent(11, 10), 100);
    }
}
