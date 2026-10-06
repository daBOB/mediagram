//! Portable core of the mlib client, shared by the Linux CLI and the Android
//! app: the Telegram transport and byte path, the catalog store over the
//! index, search, watch state and its sync, achievements, and what a device
//! enriches a library with on its own.

uniffi::setup_scaffolding!();

pub mod api;
pub mod artwork;
pub mod catalog;
pub mod catalog_achievements;
pub mod catalog_assets;
pub mod catalog_categories;
pub mod catalog_subtitles;
pub mod connection_params;
pub mod credits;
pub mod dto;
mod error;
pub mod franchises;
pub mod http;
pub mod package;
pub mod range;
pub mod search;
pub mod shows;
mod sqlite_schema;
pub mod state;
pub mod transport;
pub mod updates;
pub mod versions;
