//! Which embedded subtitle streams are worth extracting, and what each one
//! claims to be.
//!
//! Pure: it reads what `ffprobe` reported and decides before any cue is
//! read. What needs the cues themselves (a sparse track being the forced
//! one) is settled afterwards by [`super::arrange`].

use crate::media::classify::lang_code;
use crate::media::prepare::plan::PICTURE_SUBTITLES;
use crate::media::streams::{Stream, StreamKind};

/// Codecs ffmpeg turns into WebVTT. Anything else that is not a picture
/// (closed-caption data, say) is left alone rather than failing the pass.
const TEXT_SUBTITLES: &[&str] = &["subrip", "srt", "ass", "ssa", "mov_text", "webvtt", "text"];

/// An embedded text stream to extract.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Wanted {
    pub index: u32,
    /// `de` or `en`.
    pub lang: String,
    /// Claimed by the flag or the title. A track claiming nothing may still
    /// turn out to be the forced one, by its cue density.
    pub forced: bool,
    pub sdh: bool,
    pub default: bool,
    pub codec: String,
}

/// What looking at the streams found.
#[derive(Debug, Default)]
pub struct Found {
    pub wanted: Vec<Wanted>,
    /// German or English picture tracks (PGS, VobSub) that cannot be
    /// extracted: a known gap, reported rather than silent.
    pub pictures: usize,
}

pub fn find_wanted(streams: &[Stream]) -> Found {
    let mut found = Found::default();
    for stream in streams.iter().filter(|s| s.kind == StreamKind::Subtitle) {
        let title = stream.title.as_deref().unwrap_or("").to_lowercase();
        if title.contains("comment") || title.contains("kommentar") {
            continue;
        }
        let Some(lang) = language(stream, &title) else {
            continue;
        };
        let codec = stream.codec.as_deref().unwrap_or("");
        if PICTURE_SUBTITLES.contains(&codec) {
            found.pictures += 1;
        } else if TEXT_SUBTITLES.contains(&codec) {
            let forced = stream.forced || title.contains("forced") || title.contains("erzwungen");
            found.wanted.push(Wanted {
                index: stream.index,
                lang,
                forced,
                sdh: !forced && (stream.hearing_impaired || claims_sdh(&title)),
                default: stream.default,
                codec: codec.to_string(),
            });
        }
    }
    found
}

/// The tag first, the title when the tag says nothing; only German and
/// English are kept.
fn language(stream: &Stream, title: &str) -> Option<String> {
    let lang = lang_code(stream.language.as_deref()).or_else(|| {
        if title.contains("german") || title.contains("deutsch") {
            Some("de".to_string())
        } else if title.contains("english") || title.contains("englisch") {
            Some("en".to_string())
        } else {
            None
        }
    })?;
    matches!(lang.as_str(), "de" | "en").then_some(lang)
}

/// Arcane flags none of its SDH tracks; the title is the only signal.
fn claims_sdh(title: &str) -> bool {
    title.contains("hearing")
        || title.contains("hörgeschädigt")
        || title
            .split(|c: char| !c.is_alphanumeric())
            .any(|word| word == "sdh" || word == "cc")
}

#[cfg(test)]
#[path = "select_tests.rs"]
mod tests;
