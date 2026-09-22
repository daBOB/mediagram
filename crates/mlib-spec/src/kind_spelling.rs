//! How a [`Kind`] is spelled: on the wire, in the index's `kind` column,
//! and when a person types one — the same word in every place.

use crate::caption::Kind;

impl Kind {
    /// Every kind, in the order a reader would list them.
    pub const ALL: [Kind; 4] = [Kind::Movie, Kind::Ep, Kind::Tut, Kind::Doc];

    /// The spelling used on the wire and in the index's `kind` column — the
    /// same one serde writes, so a caption and a row can never disagree.
    pub fn as_str(self) -> &'static str {
        match self {
            Kind::Movie => "movie",
            Kind::Ep => "ep",
            Kind::Tut => "tut",
            Kind::Doc => "doc",
        }
    }
}

impl std::fmt::Display for Kind {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.as_str())
    }
}

/// A `kind` spelling no version of the spec has defined.
#[derive(thiserror::Error, Debug, Clone, PartialEq, Eq)]
#[error("unknown kind `{0}`")]
pub struct UnknownKind(pub String);

impl std::str::FromStr for Kind {
    type Err = UnknownKind;

    fn from_str(s: &str) -> Result<Kind, UnknownKind> {
        Kind::ALL
            .into_iter()
            .find(|kind| kind.as_str() == s)
            .ok_or_else(|| UnknownKind(s.to_string()))
    }
}
