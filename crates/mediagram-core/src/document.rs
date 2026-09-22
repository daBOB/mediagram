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

/// Whether a failed download was refused because the document handle it
/// used has gone stale.
///
/// A `Document` carries a file reference, and Telegram expires those: the
/// same handle that fetched a part an hour ago is refused with
/// `FILE_REFERENCE_EXPIRED` now. That is not a network failure and waiting
/// will not fix it — the answer is to fetch the message again, which hands
/// back a fresh reference. `FILE_REFERENCE_INVALID` is matched too, for the
/// same remedy.
pub fn is_stale_reference(err: &anyhow::Error) -> bool {
    err.chain().any(|cause| {
        matches!(
            cause.downcast_ref::<grammers_mtsender::InvocationError>(),
            Some(grammers_mtsender::InvocationError::Rpc(rpc)) if rpc.name.starts_with("FILE_REFERENCE_")
        )
    })
}

#[cfg(test)]
mod tests {
    use anyhow::Context;
    use grammers_mtsender::{InvocationError, RpcError};

    use super::*;

    fn rpc(message: &str) -> anyhow::Error {
        let error = InvocationError::Rpc(RpcError::from(grammers_tl_types::types::RpcError {
            error_code: 400,
            error_message: message.into(),
        }));
        // Wrapped the way `pump_step` wraps it, so the check has to look
        // past the context to find the refusal.
        Err::<(), _>(error).context("downloading chunk").unwrap_err()
    }

    #[test]
    fn an_expired_reference_is_stale_even_behind_context() {
        assert!(is_stale_reference(&rpc("FILE_REFERENCE_EXPIRED")));
    }

    #[test]
    fn an_invalid_reference_is_stale_too() {
        assert!(is_stale_reference(&rpc("FILE_REFERENCE_INVALID")));
    }

    #[test]
    fn other_refusals_are_not_mistaken_for_a_stale_reference() {
        assert!(!is_stale_reference(&rpc("FLOOD_WAIT_30")));
        assert!(!is_stale_reference(&anyhow::anyhow!("connection reset")));
    }
}
