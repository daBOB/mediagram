//! `Caption::display_name`, split out of `caption.rs` to keep that file under
//! this crate's line limit: a show, course or collection and its position
//! code, or a title and year.

use super::{Caption, Kind, position_code};

impl Caption {
    /// Human-readable label, e.g. `Dune: Part Two (2024)` or `Severance S02E01`.
    #[must_use]
    pub fn display_name(&self) -> String {
        let code = self
            .s
            .zip(self.e)
            .and_then(|(s, e)| position_code(self.t, s, e));
        match (self.t, self.show.as_deref()) {
            // A standalone documentary reads exactly as a movie does.
            (Kind::Movie, _) | (Kind::Docu, None) => match (self.title.as_deref(), self.year) {
                (Some(t), Some(y)) => format!("{t} ({y})"),
                (Some(t), None) => t.to_string(),
                (None, _) => self.set.clone(),
            },
            (Kind::Ep, show) => {
                let show = show.unwrap_or("?");
                match (code, self.abs) {
                    (Some(code), _) => format!("{show} {code}"),
                    (None, Some(a)) => format!("{show} #{a:03}"),
                    (None, None) => show.to_string(),
                }
            }
            (Kind::Tut | Kind::Doc | Kind::Docu, show) => {
                let course = show.unwrap_or("?");
                match code {
                    Some(code) => format!("{course} {code}"),
                    None => course.to_string(),
                }
            }
        }
    }
}

#[cfg(test)]
#[path = "caption_display_tests.rs"]
mod tests;
