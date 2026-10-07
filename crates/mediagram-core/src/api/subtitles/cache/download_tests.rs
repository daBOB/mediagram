use super::*;

fn sha256_of(bytes: &[u8]) -> String {
    hex::encode(Sha256::digest(bytes))
}

#[test]
fn a_download_matching_its_declared_checksum_is_kept() {
    let data = tempfile::tempdir().unwrap();
    let path = data.path().join("bundle.tmp");
    std::fs::write(&path, b"bundle bytes").unwrap();

    verify_and_sync(&path, &sha256_of(b"bundle bytes")).unwrap();
}

/// What the index declared is the only proof the bytes are the bundle it
/// names; anything else — a truncated or swapped document — is refused
/// before it can be published under that sha.
#[test]
fn a_download_that_does_not_match_the_index_is_refused() {
    let data = tempfile::tempdir().unwrap();
    let path = data.path().join("bundle.tmp");
    std::fs::write(&path, b"bundle byte").unwrap();

    let err = verify_and_sync(&path, &sha256_of(b"bundle bytes")).unwrap_err();

    assert!(matches!(err, CoreError::Io(message)
        if message == "a subtitle bundle's checksum did not match the index"));
}

/// A name no other download shares — another process's, or one a crash
/// left behind — inside the cache, where a trim skips it.
#[test]
fn every_download_writes_to_a_temporary_file_of_its_own() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::at(data.path());
    let sha = "a".repeat(64);

    let (first, second) = (tmp_path(&core, &sha), tmp_path(&core, &sha));

    assert_ne!(first, second);
    for path in [first, second] {
        assert_eq!(path.parent(), Some(dir(&core).as_path()));
        assert_eq!(path.extension().unwrap(), "tmp");
    }
}

/// A channel this device holds no library entry for cannot be reached, and
/// finding that out writes nothing: no cache directory, no partial file.
#[tokio::test]
async fn a_bundle_in_a_channel_this_device_cannot_reach_fails_before_writing() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::at(data.path());
    let bundle = BundleRef {
        chat_id: -1_000_000_000_123,
        message_id: 7,
        bytes: 10,
        sha256: "a".repeat(64),
    };

    let err = fetch_into(&core, &bundle, &dir(&core).join("final.json.gz"))
        .await
        .unwrap_err();

    assert!(matches!(err, CoreError::NotFound(_)));
    assert!(!dir(&core).exists());
}
