use tempfile::tempdir;

use super::*;

#[test]
fn ensure_creates_a_token_file_with_mode_0600() {
    use std::os::unix::fs::PermissionsExt;

    let dir = tempdir().expect("temp dir");
    let token = ensure(dir.path()).expect("ensure");
    assert_eq!(token.len(), 64, "32 bytes, hex-encoded");

    let path = dir.path().join("token");
    let mode = std::fs::metadata(&path).unwrap().permissions().mode() & 0o777;
    assert_eq!(mode, 0o600);
}

#[test]
fn ensure_is_stable_across_calls() {
    let dir = tempdir().expect("temp dir");
    let first = ensure(dir.path()).expect("first ensure");
    let second = ensure(dir.path()).expect("second ensure");
    assert_eq!(first, second);
}

// The vector published in `docs/running-the-player.md` under "Home cache
// server", so the Android client can assert the same numbers against its
// own HMAC implementation without either side trusting the other's code.
const VECTOR_TOKEN: &str = "00112233445566778899aabbccddeeff00112233445566778899aabbccddee";
const VECTOR_METHOD: &str = "PUT";
const VECTOR_PATH: &str = "/v1/sets/abc123/chunks/0";
const VECTOR_TOTAL: u64 = 5;
const VECTOR_BODY: &[u8] = b"hello";
const VECTOR_SIGNATURE: &str = "5b6d16159fbd287ed1da02570ef266f83790c52de8ec1eb0d2a1500ed34aef18";

#[test]
fn the_shared_test_vector_signs_to_the_published_signature() {
    let sig = sign(
        VECTOR_TOKEN,
        VECTOR_METHOD,
        VECTOR_PATH,
        VECTOR_TOTAL,
        VECTOR_BODY,
    );
    assert_eq!(sig, VECTOR_SIGNATURE);
}

#[test]
fn the_shared_test_vector_verifies() {
    assert!(verify(
        VECTOR_TOKEN,
        VECTOR_SIGNATURE,
        VECTOR_METHOD,
        VECTOR_PATH,
        VECTOR_TOTAL,
        VECTOR_BODY,
    ));
}

#[test]
fn a_changed_body_fails_verification() {
    assert!(!verify(
        VECTOR_TOKEN,
        VECTOR_SIGNATURE,
        VECTOR_METHOD,
        VECTOR_PATH,
        VECTOR_TOTAL,
        b"world",
    ));
}

#[test]
fn a_changed_path_fails_verification() {
    assert!(!verify(
        VECTOR_TOKEN,
        VECTOR_SIGNATURE,
        VECTOR_METHOD,
        "/v1/sets/abc123/chunks/1",
        VECTOR_TOTAL,
        VECTOR_BODY,
    ));
}

#[test]
fn a_changed_total_fails_verification() {
    assert!(!verify(
        VECTOR_TOKEN,
        VECTOR_SIGNATURE,
        VECTOR_METHOD,
        VECTOR_PATH,
        6,
        VECTOR_BODY,
    ));
}

#[test]
fn a_wrong_key_fails_verification() {
    let other_token = "ff112233445566778899aabbccddeeff00112233445566778899aabbccddee";
    assert!(!verify(
        other_token,
        VECTOR_SIGNATURE,
        VECTOR_METHOD,
        VECTOR_PATH,
        VECTOR_TOTAL,
        VECTOR_BODY,
    ));
}

#[test]
fn a_malformed_hex_signature_fails_rather_than_panics() {
    assert!(!verify(
        VECTOR_TOKEN,
        "not hex",
        VECTOR_METHOD,
        VECTOR_PATH,
        VECTOR_TOTAL,
        VECTOR_BODY,
    ));
}
