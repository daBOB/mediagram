use std::path::Path;

use sha2::{Digest, Sha256};

use super::super::responses::Scripted;
use super::*;
use crate::api::account::session::fixture::{rpc, Fixture};

#[tokio::test]
async fn a_channel_with_no_pins_has_no_release() {
    let fixture = Fixture::new().await;
    let found = latest_with(
        &fixture.core,
        &fixture.owner,
        -100,
        Scripted(vec![].into_iter()),
    )
    .await;
    assert_eq!(found.unwrap(), None);
}

#[tokio::test]
async fn a_refused_pin_read_is_an_error_not_an_empty_answer() {
    let fixture = Fixture::new().await;
    let found = latest_with(
        &fixture.core,
        &fixture.owner,
        -100,
        Scripted(vec![Err(rpc(500))].into_iter()),
    )
    .await;
    assert!(found.is_err());
}

fn release_of(bytes: &[u8]) -> AppRelease {
    AppRelease {
        version_name: "0.93.0".into(),
        version_code: 93_000,
        bytes: bytes.len() as u64,
        sha256: hex::encode(Sha256::digest(bytes)),
        chat_id: -100,
        message_id: 7,
    }
}

#[tokio::test]
async fn a_matching_download_lands_at_its_path() {
    let fixture = Fixture::new().await;
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("93000.apk");
    let apk = b"a whole apk".to_vec();
    let chunks = Scripted(vec![Ok(apk[..5].to_vec()), Ok(apk[5..].to_vec())].into_iter());
    write_verified(
        &fixture.core,
        &fixture.owner,
        &release_of(&apk),
        &path,
        chunks,
    )
    .await
    .unwrap();
    assert_eq!(std::fs::read(&path).unwrap(), apk);
    assert!(!path.with_extension("part").exists());
}

#[tokio::test]
async fn a_corrupt_or_short_download_leaves_nothing_behind() {
    let apk = b"a whole apk".to_vec();
    for arrived in [b"a whole apX".to_vec(), b"a whole".to_vec()] {
        let fixture = Fixture::new().await;
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("93000.apk");
        let chunks = Scripted(vec![Ok(arrived)].into_iter());
        let result = write_verified(
            &fixture.core,
            &fixture.owner,
            &release_of(&apk),
            &path,
            chunks,
        )
        .await;
        assert!(result.is_err());
        assert!(!path.exists() && !path.with_extension("part").exists());
    }
}

#[tokio::test]
async fn a_download_longer_than_its_caption_is_cut_off() {
    let fixture = Fixture::new().await;
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("93000.apk");
    let chunks = Scripted(vec![Ok(b"far more than eleven bytes".to_vec())].into_iter());
    let result = write_verified(
        &fixture.core,
        &fixture.owner,
        &release_of(b"a whole apk"),
        &path,
        chunks,
    )
    .await;
    assert!(result.is_err());
    assert!(!path.exists() && !path.with_extension("part").exists());
}

#[tokio::test]
async fn a_release_this_build_will_not_fetch_is_refused_before_any_network() {
    let fixture = Fixture::new().await;
    let mut huge = release_of(b"x");
    huge.bytes = MAX_APK_BYTES + 1;
    assert!(
        download(&fixture.core, &huge, Path::new("/nonexistent/93000.apk"))
            .await
            .is_err()
    );
    let mut unshaped = release_of(b"x");
    unshaped.sha256 = "nope".into();
    assert!(download(
        &fixture.core,
        &unshaped,
        Path::new("/nonexistent/93000.apk")
    )
    .await
    .is_err());
}
