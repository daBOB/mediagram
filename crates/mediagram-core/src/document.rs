//! Safe access to a document's Telegram id.
//!
//! `grammers_client::media::Document::id()` unwraps its optional inner
//! `document` field, and `Media::from_raw` builds a `Media::Document` for
//! every `messageMediaDocument` even when that field is absent (a stripped
//! or expired document), so calling `id()` on remote data can panic. Every
//! call site goes through [`document_id`] instead.

use grammers_client::media::{Document, Media};
use grammers_client::message::Message;
use grammers_tl_types::enums::Document as RawDocument;

/// The document's globally unique id, or `None` when the message carries no
/// usable document (absent or `documentEmpty`, which has no bytes to fetch).
pub fn document_id(document: &Document) -> Option<i64> {
    match document.raw.document.as_ref() {
        Some(RawDocument::Document(d)) => Some(d.id),
        _ => None,
    }
}

/// The document media of a message, keeping only documents with a usable id.
pub fn message_document(message: &Message) -> Option<(Document, i64)> {
    match message.media() {
        Some(Media::Document(doc)) => document_id(&doc).map(|id| (doc, id)),
        _ => None,
    }
}
