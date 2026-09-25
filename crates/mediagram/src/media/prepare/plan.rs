//! Deciding which streams a prepared file keeps.
//!
//! Pure, and deliberately so: `prepare --replace` overwrites the original, and
//! there is no undo. The table a dry run prints and the `ffmpeg` arguments a
//! real run uses both come from this one function, so they cannot disagree
//! about what is being dropped.

use crate::media::direct_play;
use crate::media::streams::{Stream, StreamKind};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Verdict {
    /// Already within the limit; nothing is touched.
    AlreadyFits,
    /// Over the limit, but every track is one we keep.
    NothingToDrop,
    /// Dropping the unwanted tracks brings it under the limit.
    Prepare,
    /// Worth dropping tracks, but it will still need more than one part.
    PrepareStillOversized,
}

#[derive(Debug, Clone, PartialEq)]
pub struct PreparePlan {
    pub keep: Vec<Stream>,
    pub dropped_audio: usize,
    pub dropped_subtitles: usize,
    pub estimated_bytes: u64,
    pub verdict: Verdict,
}

impl PreparePlan {
    /// Whether any audio track being kept is one a browser would refuse.
    ///
    /// Asked of the tracks that survive the plan, not of the file: dropping the
    /// E-AC-3 commentary and keeping the AAC is a file that needs no encoding.
    pub fn audio_needs_encoding(&self) -> bool {
        self.keep
            .iter()
            .filter(|s| s.kind == StreamKind::Audio)
            .any(|s| {
                s.codec
                    .as_deref()
                    .is_none_or(|codec| !direct_play::known(&direct_play::AUDIO, codec))
            })
    }

    /// `-map` arguments naming exactly the kept streams, in order.
    pub fn map_args(&self) -> Vec<String> {
        self.keep
            .iter()
            .flat_map(|s| ["-map".to_string(), format!("0:{}", s.index)])
            .collect()
    }
}

/// Which streams survive, and how big the result is likely to be.
///
/// `size` and `duration` come from the container. `keep_audio` and
/// `keep_subtitles` are language codes; matching ignores case, so both `ger`
/// and `deu` can be listed for German.
pub fn plan_prepare(
    streams: &[Stream],
    size: u64,
    duration: f64,
    keep_audio: &[&str],
    keep_subtitles: &[&str],
    limit: u64,
) -> PreparePlan {
    let mut keep = Vec::new();
    let mut dropped_audio = 0usize;
    let mut dropped_subtitles = 0usize;
    let mut dropped_bits = 0u64;

    for stream in streams {
        let wanted = match stream.kind {
            // Never drop the picture, and never drop attachments or chapters:
            // this feature exists to remove languages, nothing else.
            StreamKind::Video | StreamKind::Other => true,
            StreamKind::Audio => language_wanted(stream, keep_audio),
            StreamKind::Subtitle => language_wanted(stream, keep_subtitles),
        };
        if wanted {
            keep.push(stream.clone());
            continue;
        }
        match stream.kind {
            StreamKind::Audio => {
                dropped_audio += 1;
                // A track with no declared bitrate contributes nothing to the
                // estimate. That only ever makes the estimate pessimistic,
                // which is the safe direction: the real size is measured
                // after the copy anyway.
                dropped_bits += stream.bit_rate.unwrap_or(0);
            }
            StreamKind::Subtitle => dropped_subtitles += 1,
            _ => {}
        }
    }

    let saved = if duration > 0.0 {
        (dropped_bits as f64 * duration / 8.0) as u64
    } else {
        0
    };
    let estimated_bytes = size.saturating_sub(saved);

    let verdict = if size <= limit {
        Verdict::AlreadyFits
    } else if dropped_audio == 0 && dropped_subtitles == 0 {
        Verdict::NothingToDrop
    } else if estimated_bytes <= limit {
        Verdict::Prepare
    } else {
        Verdict::PrepareStillOversized
    };

    PreparePlan {
        keep,
        dropped_audio,
        dropped_subtitles,
        estimated_bytes,
        verdict,
    }
}

/// An untagged track is kept. Dropping a stream we cannot identify is how a
/// library loses its only audio track.
fn language_wanted(stream: &Stream, keep: &[&str]) -> bool {
    match &stream.language {
        None => true,
        Some(lang) => {
            let lang = lang.to_ascii_lowercase();
            keep.iter().any(|k| k.eq_ignore_ascii_case(&lang))
        }
    }
}
