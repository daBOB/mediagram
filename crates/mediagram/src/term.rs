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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn durations_read_as_time() {
        assert_eq!(human_duration(9), "9s");
        assert_eq!(human_duration(75), "1m15s");
        assert_eq!(human_duration(3725), "1h02m");
    }

    #[test]
    fn a_percentage_never_runs_past_a_hundred_or_divides_by_zero() {
        assert_eq!(percent(5, 0), 0);
        assert_eq!(percent(11, 10), 100);
    }
}
