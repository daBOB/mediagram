//! How a management call ended. Split out of `manage.rs` to keep it under
//! the line limit.

/// One variant per reason the web answers a refusal with — `invalid`,
/// `name-taken`, `not-found`, `wait`, `no-pin`, `wrong-pin`, `not-allowed` —
/// so both surfaces can say exactly what went wrong; and one the web never
/// needs, `NotSynced`.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum ProfileOutcome {
    Done,
    /// A blank name, a new PIN that is not four digits, a limit not 6 or 12.
    Invalid,
    /// A new profile's name is one a profile here already answers to.
    NameTaken,
    /// The actor or the target names nobody here.
    NotFound,
    /// That profile's PIN is not compared for `seconds` more.
    Wait { seconds: u32 },
    /// A grown-up from before PINs, who has to set one first.
    NoPin,
    WrongPin,
    /// The rule says no.
    NotAllowed,
    /// A first profile on a device that has not yet taken in a sync round:
    /// it has not heard who the household already is. Core only — the web's
    /// server finishes a round before it answers anyone.
    NotSynced,
}

/// An answer, or the store could not be read or written.
pub type Answer = rusqlite::Result<ProfileOutcome>;
