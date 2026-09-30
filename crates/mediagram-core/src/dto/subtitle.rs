//! A subtitle track as the boundary sees it: enough to label it and ask for
//! its text, never where its bytes live.

/// One subtitle track a set offers — a position in its bundle, once it has
/// one, or, until then, a position among its inline `assets` rows. See
/// `catalog_subtitles::tracks_by_set`.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SubtitleTrack {
    /// This track's position — what `Core::subtitle_text` is asked for, not
    /// an index into anything else.
    pub track: u32,
    pub lang: String,
    pub forced: bool,
    pub sdh: bool,
    pub label: String,
}
