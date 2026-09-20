//! Portable core of the mlib client: Telegram transport and index, shared by
//! the Linux CLI and the Android app.

uniffi::setup_scaffolding!();

pub mod api;
pub mod catalog;
pub mod document;
pub mod dto;
pub mod package;
pub mod range;
pub mod shows;
pub mod stream;
pub mod telegram;
