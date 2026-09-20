//! Re-exported from `mediagram-core`: the byte source and the CLI's own
//! Telegram calls both need to tell a usable document from an absent one, so
//! the check lives in one place.

pub use mediagram_core::document::{document_id, message_document};
