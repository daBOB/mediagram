//! Correcting a set's metadata after it is already in the channel.
//!
//! Every part of a set carries the whole record, so a correction means
//! rewriting one message per part. Planning that is pure and lives in
//! [`plan`] (the corrected row) and [`captions`] (the text each part will
//! carry); performing it talks to Telegram and lives in [`apply`].

pub mod apply;
pub mod captions;
pub mod plan;
