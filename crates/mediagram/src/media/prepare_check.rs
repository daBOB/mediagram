//! Checking a prepared file before it replaces the original.
//!
//! `prepare --replace` is irreversible: the dropped tracks are gone. Every
//! check here exists because of a way ffmpeg can exit successfully having
//! produced something that is not a usable replacement. Pure, so each one can
//! be tested against the case it guards.

use super::prepare_plan::{Stream, StreamKind};

/// Why a prepared file was rejected. Each variant names one real failure.
#[derive(Debug, Clone, PartialEq)]
pub enum Rejection {
    /// ffmpeg can exit 0 having written nothing useful.
    Empty,
    /// A bad stream map can drop the picture entirely.
    NoVideo,
    /// The keep set was not applied, or was applied to nothing.
    MissingLanguage(String),
    /// The likeliest silent failure: a truncated copy.
    DurationChanged { source: f64, prepared: f64 },
    /// Dropping tracks cannot make a file bigger; something was misunderstood.
    NotSmaller { source: u64, prepared: u64 },
}

/// Seconds the prepared duration may differ from the source before it counts
/// as truncated. Remuxing shifts the last frame slightly; a truncation is
/// always far larger than this.
pub const DURATION_TOLERANCE: f64 = 1.0;

/// A file's size in bytes and its duration in seconds, as probed.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Measured {
    pub size: u64,
    pub duration: f64,
}

/// Whether a prepared file may replace its original.
pub fn check_prepared(
    prepared_streams: &[Stream],
    prepared: Measured,
    source: Measured,
    expected_languages: &[String],
    // Whether the result may be no smaller than the source. Dropping tracks
    // can only shrink a file, but re-encoding the audio can round the other
    // way on one that had little to drop.
    allow_growth: bool,
) -> Result<(), Rejection> {
    if prepared.size == 0 || prepared_streams.is_empty() {
        return Err(Rejection::Empty);
    }
    if !prepared_streams.iter().any(|s| s.kind == StreamKind::Video) {
        return Err(Rejection::NoVideo);
    }
    for language in expected_languages {
        let present = prepared_streams.iter().any(|s| {
            s.kind == StreamKind::Audio
                && s.language
                    .as_deref()
                    .is_some_and(|l| l.eq_ignore_ascii_case(language))
        });
        if !present {
            return Err(Rejection::MissingLanguage(language.clone()));
        }
    }
    if (prepared.duration - source.duration).abs() > DURATION_TOLERANCE {
        return Err(Rejection::DurationChanged {
            source: source.duration,
            prepared: prepared.duration,
        });
    }
    if !allow_growth && prepared.size >= source.size {
        return Err(Rejection::NotSmaller {
            source: source.size,
            prepared: prepared.size,
        });
    }
    Ok(())
}
