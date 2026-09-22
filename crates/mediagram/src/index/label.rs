//! The one way a listing names a set: the show or course, the spec's code
//! for where it sits (`S01E07`, `S01E01-E02`, `C02L03`, `C02D01`), then its
//! own title. `status` and `remove` both print it, so a set reads the same
//! wherever a person meets it.

use mlib_spec::caption::{Episode, Kind, document_code, episode_code, lesson_code};

/// The fields a label is made of, borrowed from whichever row holds them.
pub struct Named<'a> {
    pub set_id: &'a str,
    /// The index's `kind` spelling; an unknown one simply gets no code.
    pub kind: &'a str,
    pub show: Option<&'a str>,
    pub title: Option<&'a str>,
    pub season: Option<u32>,
    /// The index's JSON-encoded `Episode`: `7` or `[1,2]`.
    pub episode: Option<&'a str>,
}

impl Named<'_> {
    /// `Star City  S01E07  Plow Deep`, or the set id when nothing else names
    /// it — a set resolved badly is still better called something than
    /// nothing.
    pub fn label(&self) -> String {
        let mut parts: Vec<String> = Vec::new();
        parts.extend(self.show.map(str::to_string));
        parts.extend(self.code());
        parts.extend(self.title.map(str::to_string));
        if parts.is_empty() {
            return self.set_id.to_string();
        }
        parts.join("  ")
    }

    fn code(&self) -> Option<String> {
        let season = self.season?;
        let episode: Episode = serde_json::from_str(self.episode?).ok()?;
        match self.kind.parse::<Kind>().ok()? {
            Kind::Ep => Some(episode_code(season, episode)),
            Kind::Tut => Some(lesson_code(season, episode)),
            Kind::Doc => Some(document_code(season, episode)),
            Kind::Movie => None,
        }
    }
}
