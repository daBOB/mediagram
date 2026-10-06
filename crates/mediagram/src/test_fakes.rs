//! The in-memory channel, transport and connection the integration tests
//! use, compiled into the unit tests from the very same files.
//!
//! One copy rather than two: a second fake of `ChannelRemote` would drift
//! from the first, and a unit test passing against a fake the integration
//! tests no longer trust proves nothing. They are reached by path, not
//! exported, so no test scaffolding joins the library's public face; the
//! price is that those files may only name this crate as `mediagram` and
//! each other as `super::`, which is all they do today.
//!
//! Each unit test reaches for a few of their helpers, never all of them.
#![allow(dead_code)]

#[path = "../tests/support/channel.rs"]
pub(crate) mod channel;
#[path = "../tests/support/media.rs"]
pub(crate) mod media;
#[path = "../tests/support/session.rs"]
pub(crate) mod session;
#[path = "../tests/support/upload.rs"]
pub(crate) mod upload;
