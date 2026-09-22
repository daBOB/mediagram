//! Portable core of the mlib client: Telegram transport and index, shared by
//! the Linux CLI and the Android app.

uniffi::setup_scaffolding!();

pub mod api;
pub mod catalog;
pub mod dto;
pub mod package;
pub mod range;
pub mod shows;
pub mod transport;

// The uploader still imports these by their old root paths.
pub use transport::source as telegram;
pub use transport::{document, stream};
