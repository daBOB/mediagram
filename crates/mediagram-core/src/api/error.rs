//! The errors the Kotlin-facing surface can return.

/// Every error this surface can hand to Kotlin.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("network error: {0}")]
    Network(String),
    #[error("not authorized: {0}")]
    NotAuthorized(String),
    #[error("not found: {0}")]
    NotFound(String),
    #[error("cipher error: {0}")]
    Cipher(String),
    #[error("io error: {0}")]
    Io(String),
    /// The chosen channel does not hold one readable index. Its own variant
    /// because it is neither a network fault nor a missing file: the channel
    /// answered, and what it holds is not a library yet — which is something
    /// a person can go and fix.
    #[error("library error: {0}")]
    Library(String),
}

impl CoreError {
    /// `Io(what)` for a failure whose cause is logged rather than returned.
    ///
    /// The message Kotlin sees stays the plain `what`: no variant may carry a
    /// chat, message or document id, and a cause is free to name one. The
    /// cause still matters to whoever diagnoses the failure — permission
    /// denied and disk full read the same without it — so it goes to the log.
    pub(crate) fn io<E: std::fmt::Display>(what: &str) -> impl FnOnce(E) -> CoreError + '_ {
        move |cause| {
            tracing::warn!(%cause, "{what}");
            CoreError::Io(what.into())
        }
    }

    /// [`CoreError::io`]'s counterpart for a failure on the network.
    pub(crate) fn network<E: std::fmt::Display>(what: &str) -> impl FnOnce(E) -> CoreError + '_ {
        move |cause| {
            tracing::warn!(%cause, "{what}");
            CoreError::Network(what.into())
        }
    }
}
