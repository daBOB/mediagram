//! Completing the pointer.
//!
//! The draft carries the fields the cipher authenticated. Publishing fills in
//! where the bytes live and what they hash to, and must leave the
//! authenticated fields exactly as the cipher saw them: changing one would
//! make every reader fail with a tag error that looks like an attack.

use mlib_spec::package::LatestPointer;

/// Fills the download fields of a draft produced by [`super::pointer::draft`].
pub fn complete(
    draft: &LatestPointer,
    file_name: &str,
    base_url: &str,
    bytes: u64,
    sha256_hex: &str,
) -> LatestPointer {
    LatestPointer {
        file: file_name.to_string(),
        url: join_url(base_url, file_name),
        bytes,
        sha256: sha256_hex.to_string(),
        ..draft.clone()
    }
}

fn join_url(base: &str, file_name: &str) -> String {
    format!("{}/{}", base.trim_end_matches('/'), file_name)
}
