//! `Caption::display_name`, split out of `caption.rs` to keep that file under
//! this crate's line limit — one match arm per `Kind`, nothing else.

use super::{Caption, Kind, docu_code, document_code, episode_code, lesson_code};

impl Caption {
    /// Human-readable label, e.g. `Dune: Part Two (2024)` or `Severance S02E01`.
    #[must_use]
    pub fn display_name(&self) -> String {
        match self.t {
            Kind::Movie => match (self.title.as_deref(), self.year) {
                (Some(t), Some(y)) => format!("{t} ({y})"),
                (Some(t), None) => t.to_string(),
                (None, _) => self.set.clone(),
            },
            Kind::Ep => {
                let show = self.show.as_deref().unwrap_or("?");
                match (self.s, self.e, self.abs) {
                    (Some(s), Some(e), _) => format!("{show} {}", episode_code(s, e)),
                    (_, _, Some(a)) => format!("{show} #{a:03}"),
                    _ => show.to_string(),
                }
            }
            Kind::Tut => {
                let course = self.show.as_deref().unwrap_or("?");
                match (self.s, self.e) {
                    (Some(c), Some(l)) => format!("{course} {}", lesson_code(c, l)),
                    _ => course.to_string(),
                }
            }
            Kind::Doc => {
                let course = self.show.as_deref().unwrap_or("?");
                match (self.s, self.e) {
                    (Some(c), Some(d)) => format!("{course} {}", document_code(c, d)),
                    _ => course.to_string(),
                }
            }
            Kind::Docu => match self.show.as_deref() {
                Some(collection) => match (self.s, self.e) {
                    (Some(c), Some(e)) => format!("{collection} {}", docu_code(c, e)),
                    _ => collection.to_string(),
                },
                None => match (self.title.as_deref(), self.year) {
                    (Some(t), Some(y)) => format!("{t} ({y})"),
                    (Some(t), None) => t.to_string(),
                    (None, _) => self.set.clone(),
                },
            },
        }
    }
}
