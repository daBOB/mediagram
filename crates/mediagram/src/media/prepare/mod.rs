//! `prepare`'s pipeline: which streams a file keeps ([`plan`]), where the
//! rewritten copy is written ([`paths`]), and the check a rewritten copy must
//! pass before it replaces anything ([`check`]).

pub mod check;
pub mod paths;
pub mod plan;
