//! A round's failure reaches Kotlin only as `SyncOutcome::failed`, this
//! error's text, so the text is what a caller relies on.

use super::SyncError;

#[test]
fn a_channel_failure_reads_as_the_channels_own_words() {
    let cause = std::io::Error::other("channel refused (503)");
    let error = SyncError::Channel(cause);
    assert_eq!(error.to_string(), "channel refused (503)");
}

#[test]
fn a_document_that_will_not_serialize_reads_as_serdes_own_words() {
    fn round() -> Result<String, SyncError<std::io::Error>> {
        Ok(serde_json::from_str::<String>("{")?)
    }
    let cause = serde_json::from_str::<String>("{").unwrap_err().to_string();
    let error = round().unwrap_err();
    assert!(matches!(error, SyncError::Serialization(_)));
    assert_eq!(error.to_string(), cause);
}

/// The store logs its own cause and answers nothing, so these name the
/// operation rather than invent a source the database never returned.
#[test]
fn a_store_failure_names_which_half_of_the_round_failed() {
    let import: SyncError<std::io::Error> = SyncError::Import;
    let read: SyncError<std::io::Error> = SyncError::Read;
    assert_eq!(import.to_string(), "the local state could not be imported");
    assert_eq!(read.to_string(), "the local state could not be read");
    assert!(std::error::Error::source(&import).is_none());
}
