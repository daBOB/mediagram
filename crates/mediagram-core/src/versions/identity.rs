//! Which package a version of the catalog was decrypted from, recorded
//! beside it so a later refresh can tell a replay from a real update.
//!
//! The channel path never writes this file — only a refresh from a package
//! does, after decrypting it — so its absence under a version directory is
//! what tells a package-installed catalog from a channel-installed one.

use std::path::Path;

use serde::{Deserialize, Serialize};

use crate::error::CoreError;

const IDENTITY_FILE: &str = "identity.json";

/// The five fields the cipher authenticates — what "already held" means.
/// Recorded only after a successful decrypt, never from `sha256`: that field
/// is not authenticated, and a reader that treats it as identity can have an
/// update suppressed by whoever last wrote the pointer.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Identity {
    pub format: u32,
    pub created_at: i64,
    pub key_id: String,
    pub schema: i64,
    pub spec: u32,
}

pub fn identity_of(pointer: &mlib_spec::package::LatestPointer) -> Identity {
    Identity {
        format: pointer.format,
        created_at: pointer.created_at,
        key_id: pointer.key_id.clone(),
        schema: pointer.schema,
        spec: pointer.spec,
    }
}

/// The identity recorded when the version at `dir` was last decrypted.
///
/// `Ok(None)` means the file is simply absent — a legitimate first run, with
/// nothing held yet. An existing file that cannot be read or parsed is
/// `Err`, never folded into the same "nothing held" case: doing that would
/// let deleting or corrupting this one file silently defeat the replay
/// check that reads it, by making an old package look like the first one
/// ever seen.
pub fn read_identity(dir: &Path) -> Result<Option<Identity>, CoreError> {
    let path = dir.join(IDENTITY_FILE);
    let text = match std::fs::read_to_string(&path) {
        Ok(text) => text,
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(err) => return Err(CoreError::io("reading the package identity")(err)),
    };
    serde_json::from_str(&text)
        .map(Some)
        .map_err(CoreError::io("the package identity record is corrupt"))
}

pub fn write_identity(dir: &Path, identity: &Identity) -> Result<(), CoreError> {
    let text =
        serde_json::to_string(identity).map_err(CoreError::io("recording the package identity"))?;
    std::fs::write(dir.join(IDENTITY_FILE), text)
        .map_err(CoreError::io("recording the package identity"))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn identity() -> Identity {
        Identity {
            format: 1,
            created_at: 1_781_568_000,
            key_id: "9f2c41ab".into(),
            schema: 6,
            spec: 4,
        }
    }

    #[test]
    fn a_written_identity_reads_back_equal() {
        let dir = tempfile::tempdir().unwrap();
        write_identity(dir.path(), &identity()).unwrap();
        assert_eq!(read_identity(dir.path()).unwrap(), Some(identity()));
    }

    #[test]
    fn no_identity_file_reads_back_as_nothing_held() {
        let dir = tempfile::tempdir().unwrap();
        assert_eq!(read_identity(dir.path()).unwrap(), None);
    }

    /// The case an absent file must never be confused with: corruption is a
    /// refusal, not a silent "nothing held yet".
    #[test]
    fn a_corrupt_identity_file_is_refused_not_treated_as_absent() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::write(dir.path().join(IDENTITY_FILE), b"not json").unwrap();
        assert!(read_identity(dir.path()).is_err());
    }
}
