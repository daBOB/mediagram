//! Subtitle files sitting beside a video.
//!
//! A file belongs to a video when its name is the video's stem, then only
//! words that say what the file is (a language, `forced`, `sdh`), then `.vtt`
//! or `.srt`. Any other word means it belongs to another video, so `Lesson 1`
//! never takes `Lesson 10.srt` nor `Lesson 1 - Recap.srt`.

use std::path::{Path, PathBuf};

use mlib_spec::subtitle_bundle::MAX_TRACK_BYTES;

/// Where a sidecar's words come from.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Payload {
    /// A WebVTT file already checked: valid UTF-8, starting with `WEBVTT`.
    Vtt(String),
    /// A SubRip file, for ffmpeg to convert.
    Srt(PathBuf),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Sidecar {
    /// `de`, `en`, or `und` when the file names none and the video's audio
    /// does not either.
    pub lang: String,
    pub forced: bool,
    pub sdh: bool,
    pub payload: Payload,
}

/// Finds the sidecars of `video`. A file without a language in its name is
/// in the audio's language, since that is what a transcript is.
pub fn discover(video: &Path, audio_lang: Option<&str>) -> Vec<Sidecar> {
    let Some(stem) = stem_of(video) else {
        return Vec::new();
    };
    let folder = video.parent().unwrap_or(Path::new("."));
    let Ok(entries) = std::fs::read_dir(folder) else {
        return Vec::new();
    };
    let mut names: Vec<_> = entries
        .flatten()
        .filter_map(|e| Some((e.file_name().to_str()?.to_string(), e.path())))
        .collect();
    names.sort();

    let mut found: Vec<Sidecar> = Vec::new();
    for (name, path) in names {
        let Some((rest, is_vtt)) = strip_extension(&name).and_then(|(base, vtt)| {
            base.strip_prefix(stem.as_str())
                .map(|rest| (rest.to_string(), vtt))
        }) else {
            continue;
        };
        let Some(words) = parse_words(&rest) else {
            continue;
        };
        let lang = words
            .lang
            .or_else(|| audio_lang.map(str::to_string))
            .unwrap_or_else(|| "und".to_string());
        if !matches!(lang.as_str(), "de" | "en" | "und") {
            tracing::warn!("subtitles: {name} is in {lang}, which is not kept");
            continue;
        }
        let payload = if is_vtt {
            match read_vtt(&path) {
                Some(text) => Payload::Vtt(text),
                None => continue,
            }
        } else {
            Payload::Srt(path)
        };
        let sidecar = Sidecar {
            lang,
            forced: words.forced,
            sdh: words.sdh && !words.forced,
            payload,
        };
        // A `.vtt` beats an `.srt` of the same track, so a lesson with both
        // converts nothing.
        let same = |o: &Sidecar| {
            (&o.lang, o.forced, o.sdh) == (&sidecar.lang, sidecar.forced, sidecar.sdh)
        };
        match found.iter_mut().find(|o| same(o)) {
            Some(old) if matches!(old.payload, Payload::Srt(_)) && is_vtt => *old = sidecar,
            Some(_) => {}
            None => found.push(sidecar),
        }
    }
    found
}

/// The stem the sidecars are named after; a faststart remux is written as
/// `name.faststart.mp4` and its sidecars still sit under `name`.
fn stem_of(video: &Path) -> Option<String> {
    let stem = video.file_stem()?.to_str()?;
    Some(stem.strip_suffix(".faststart").unwrap_or(stem).to_string())
}

fn strip_extension(name: &str) -> Option<(&str, bool)> {
    let dot = name.rfind('.')?;
    match name[dot + 1..].to_ascii_lowercase().as_str() {
        "vtt" => Some((&name[..dot], true)),
        "srt" => Some((&name[..dot], false)),
        _ => None,
    }
}

struct Words {
    lang: Option<String>,
    forced: bool,
    sdh: bool,
}

/// `None` when the text between stem and extension is anything but
/// separated language and flag words.
fn parse_words(rest: &str) -> Option<Words> {
    let mut words = Words {
        lang: None,
        forced: false,
        sdh: false,
    };
    if rest.is_empty() {
        return Some(words);
    }
    if !rest.starts_with([' ', '.', '_', '-']) {
        return None;
    }
    for word in rest.split([' ', '.', '_', '-']).filter(|w| !w.is_empty()) {
        match word.to_lowercase().as_str() {
            "de" | "deu" | "ger" | "german" | "deutsch" => words.lang = Some("de".into()),
            "en" | "eng" | "english" | "englisch" => words.lang = Some("en".into()),
            "forced" => words.forced = true,
            "sdh" | "cc" | "hi" => words.sdh = true,
            _ => return None,
        }
    }
    Some(words)
}

fn read_vtt(path: &Path) -> Option<String> {
    let bytes = match std::fs::read(path) {
        Ok(bytes) if bytes.len() <= MAX_TRACK_BYTES => bytes,
        Ok(bytes) => {
            tracing::warn!(
                "subtitles: {} is {} bytes, too large",
                path.display(),
                bytes.len()
            );
            return None;
        }
        Err(err) => {
            tracing::warn!("subtitles: {}: {err}", path.display());
            return None;
        }
    };
    match String::from_utf8(bytes) {
        Ok(text) if text.trim_start_matches('\u{feff}').starts_with("WEBVTT") => Some(text),
        _ => {
            tracing::warn!("subtitles: {} is not UTF-8 WebVTT", path.display());
            None
        }
    }
}

#[cfg(test)]
#[path = "sidecars_tests.rs"]
mod tests;
