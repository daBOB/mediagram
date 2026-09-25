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

#[cfg(test)]
#[path = "error_tests.rs"]
mod tests;

impl CoreError {
    /// `Io(what)` for a failure whose cause is logged rather than returned.
    ///
    /// The message Kotlin sees stays the plain `what`: no variant may carry a
    /// chat, message or document id, and a cause is free to name one. The
    /// cause still matters to whoever diagnoses the failure — permission
    /// denied and disk full read the same without it — so it goes to the log.
    /// Alternate Display preserves `anyhow` context chains in the diagnostic.
    pub(crate) fn io<E: std::fmt::Display>(what: &str) -> impl FnOnce(E) -> CoreError + '_ {
        move |cause| {
            tracing::warn!(cause = %format_args!("{cause:#}"), "{what}");
            CoreError::Io(what.into())
        }
    }

    /// [`CoreError::io`]'s counterpart for a failure on the network.
    pub(crate) fn network<E: std::fmt::Display>(what: &str) -> impl FnOnce(E) -> CoreError + '_ {
        move |cause| {
            tracing::warn!(cause = %format_args!("{cause:#}"), "{what}");
            CoreError::Network(what.into())
        }
    }

    /// This error, standing for a cause that is logged rather than returned —
    /// the same rule as [`CoreError::io`], for the variants whose message is a
    /// fixed sentence written for the person reading it.
    pub(crate) fn logged<E: std::fmt::Display>(self) -> impl FnOnce(E) -> CoreError {
        move |cause| {
            tracing::warn!(cause = %format_args!("{cause:#}"), "{self}");
            self
        }
    }
}
