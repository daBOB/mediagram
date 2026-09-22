//! The network half of a refresh: deriving the package URL, downloading it
//! under a hard ceiling, and cross-checking the manifest it carries against
//! the pointer that named it.

use std::path::Path;

use mlib_spec::package::LatestPointer;

use crate::versions;
use crate::api::CoreError;

/// Derives the package's download URL from the same base `pointer_url` was
/// fetched from — never from the pointer's own `url` field, which is not
/// part of the authenticated data and therefore not trustworthy as a
/// destination to fetch: a phone making requests to an address it does not
/// control, chosen by whoever last wrote the pointer, is a probe with its
/// credentials.
///
/// Resolved with `Url::join`, not string slicing: a URL's authority and its
/// path are structurally separate fields to a real URL parser, so a
/// path-less `pointer_url` (`http://host`, no trailing slash) still joins
/// onto the path and can never collapse into replacing the host — which is
/// exactly what slicing at the last `/` did, since `http://host` contains
/// one in `//`.
pub(in crate::api) fn package_url(pointer_url: &str, file: &str) -> Result<String, CoreError> {
    if !safe_file_name(file) {
        return Err(CoreError::Cipher("the pointer names an unsafe file".into()));
    }
    let base = url::Url::parse(pointer_url)
        .map_err(CoreError::network("pointer_url is not a valid URL"))?;
    let joined = base
        .join(file)
        .map_err(CoreError::network("could not build the package URL"))?;
    Ok(joined.to_string())
}

/// A package file name and nothing else: it is joined to a URL and, inside
/// `read_package`, becomes part of a file system path, so it must not be
/// able to carry a path, a query, or a host into either.
fn safe_file_name(name: &str) -> bool {
    !name.is_empty()
        && name.len() <= 128
        && name
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-'))
}

pub(in crate::api) async fn fetch(
    client: &reqwest::Client,
    url: &str,
) -> Result<reqwest::Response, CoreError> {
    client
        .get(url)
        .send()
        .await
        .and_then(reqwest::Response::error_for_status)
        .map_err(|cause| {
            tracing::warn!(%cause, "request to {url} failed");
            CoreError::Network(format!("request to {url} failed"))
        })
}

/// Fetches `url`, refusing once the actual bytes received exceed `cap` —
/// checked against the response body itself, not only against a
/// `Content-Length` header, which is exactly the kind of claim a host that
/// wants to overrun a reader's memory would lie about.
pub(in crate::api) async fn fetch_capped(
    client: &reqwest::Client,
    url: &str,
    cap: u64,
) -> Result<Vec<u8>, CoreError> {
    let mut response = fetch(client, url).await?;
    if response.content_length().is_some_and(|len| len > cap) {
        return Err(CoreError::Network(
            "the package is larger than its pointer promised".into(),
        ));
    }
    let mut out = Vec::new();
    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(CoreError::network("the package download ended before it finished"))?
    {
        out.extend_from_slice(&chunk);
        if out.len() as u64 > cap {
            return Err(CoreError::Network(
                "the package is larger than its pointer promised".into(),
            ));
        }
    }
    Ok(out)
}

/// The manifest lives inside the ciphertext; it and the pointer must agree
/// about what they describe, or a package could carry index bytes for a
/// build other than the one its pointer claims.
pub(in crate::api) fn check_manifest(dir: &Path, pointer: &LatestPointer) -> Result<(), CoreError> {
    let text = std::fs::read_to_string(dir.join(versions::MANIFEST_FILE))
        .map_err(CoreError::Cipher("the package has no manifest".into()).logged())?;
    let manifest: serde_json::Value = serde_json::from_str(&text)
        .map_err(CoreError::Cipher("the package manifest is not JSON".into()).logged())?;
    if manifest.get("created_at").and_then(serde_json::Value::as_i64) != Some(pointer.created_at) {
        return Err(CoreError::Cipher(
            "the package manifest disagrees with the pointer about when it was built".into(),
        ));
    }
    if manifest.get("schema").and_then(serde_json::Value::as_i64) != Some(pointer.schema) {
        return Err(CoreError::Cipher(
            "the package manifest disagrees with the pointer about its schema".into(),
        ));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The whole point of `package_url` taking `pointer_url` rather than a
    /// pointer: there is no `url` field in scope for it to reach for, even
    /// by accident.
    #[test]
    fn the_package_url_is_the_pointer_url_s_base_joined_to_the_file() {
        let url = package_url(
            "https://cdn.example.com/library/latest.json",
            "prebuilt_mediagram_db_2026-01-01_ab12cd34.tar.gz.enc",
        )
        .unwrap();
        assert_eq!(
            url,
            "https://cdn.example.com/library/prebuilt_mediagram_db_2026-01-01_ab12cd34.tar.gz.enc"
        );
    }

    #[test]
    fn a_file_name_that_could_escape_the_base_is_refused() {
        assert!(package_url("https://cdn.example.com/latest.json", "../secrets").is_err());
        assert!(package_url("https://cdn.example.com/latest.json", "a/b").is_err());
        assert!(package_url("https://cdn.example.com/latest.json", "").is_err());
    }

    /// A pointer URL with no path at all must still join onto a path, never
    /// onto the host: `http://host` contains a `/` (inside `//`), which is
    /// exactly what made naive slicing at the last `/` collapse the base
    /// down to the scheme and let the unauthenticated `file` field pick the
    /// destination host instead.
    #[test]
    fn a_host_only_pointer_url_still_joins_onto_a_path_not_the_host() {
        let url = package_url("http://cdn.example.com", "archive.tar.gz.enc").unwrap();
        assert_eq!(url, "http://cdn.example.com/archive.tar.gz.enc");
    }

    fn pointer(created_at: i64, schema: i64) -> LatestPointer {
        LatestPointer {
            format: 1,
            created_at,
            file: "f".into(),
            url: "https://example.invalid/f".into(),
            bytes: 1,
            sha256: "0".repeat(64),
            cipher: "aes-256-gcm".into(),
            key_id: "0".repeat(8),
            schema,
            spec: 1,
        }
    }

    #[test]
    fn a_manifest_agreeing_with_the_pointer_passes() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::write(
            dir.path().join(versions::MANIFEST_FILE),
            r#"{"created_at":100,"schema":6}"#,
        )
        .unwrap();
        check_manifest(dir.path(), &pointer(100, 6)).unwrap();
    }

    #[test]
    fn a_manifest_naming_a_different_build_is_refused() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::write(
            dir.path().join(versions::MANIFEST_FILE),
            r#"{"created_at":999,"schema":6}"#,
        )
        .unwrap();
        assert!(check_manifest(dir.path(), &pointer(100, 6)).is_err());
    }

    #[test]
    fn a_missing_manifest_is_refused() {
        let dir = tempfile::tempdir().unwrap();
        assert!(check_manifest(dir.path(), &pointer(100, 6)).is_err());
    }
}
