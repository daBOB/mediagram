//! Correcting a set's metadata after it is already in the channel.
//!
//! Every part of a set carries the whole record, so a correction means
//! rewriting one message per part. Planning that is pure and lives in
//! [`plan`]; performing it talks to Telegram and lives in [`apply`].

pub mod apply;
pub mod plan;
