//! Portable core of the mlib client: Telegram transport and index, shared by
//! the Linux CLI and the Android app.

uniffi::setup_scaffolding!();

pub mod api;
pub mod catalog;
pub mod catalog_assets;
pub mod connection_params;
pub mod dto;
mod error;
pub mod http;
pub mod package;
pub mod range;
pub mod search;
pub mod shows;
pub mod state;
pub mod transport;
pub mod updates;
pub mod versions;
