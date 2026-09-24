//! Round failures keep their original types until the public outcome is rendered.

#[derive(Debug, thiserror::Error)]
pub(super) enum SyncError<E> {
    #[error(transparent)]
    Channel(E),
    #[error(transparent)]
    Serialization(#[from] serde_json::Error),
    // StateDb logs storage causes and returns None; these name the operation
    // that failed without inventing a source error the database did not return.
    #[error("the local state could not be imported")]
    Import,
    #[error("the local state could not be read")]
    Read,
}
