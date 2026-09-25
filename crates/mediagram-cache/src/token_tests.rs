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

/// A crash or a full disk between creating the token file and writing its
/// content — the old, non-atomic `create_new` + `write_all` — could leave
/// it empty forever after. An empty key would still make a valid (wrong)
/// HMAC for every request, so this must refuse to start rather than run
/// with it.
#[test]
fn ensure_refuses_an_empty_token_file() {
    let dir = tempdir().expect("temp dir");
    std::fs::write(dir.path().join("token"), "").unwrap();
    assert!(ensure(dir.path()).is_err());
}

#[test]
fn ensure_refuses_a_token_file_of_the_wrong_length() {
    let dir = tempdir().expect("temp dir");
    std::fs::write(dir.path().join("token"), "abc123").unwrap();
    assert!(ensure(dir.path()).is_err());
}

#[test]
fn ensure_refuses_a_token_file_with_uppercase_hex() {
    let dir = tempdir().expect("temp dir");
    std::fs::write(dir.path().join("token"), VECTOR_TOKEN.to_uppercase()).unwrap();
    assert!(ensure(dir.path()).is_err());
}

/// The token is staged in a temp file and published with a no-overwrite
/// link, the same publish scheme chunk bodies and a set's total use, so a
/// crash mid-write leaves no half-written `token` file — and no stray temp
/// file sitting next to it either.
#[test]
fn ensure_leaves_no_temp_file_behind_after_creating_the_token() {
    let dir = tempdir().expect("temp dir");
    ensure(dir.path()).expect("ensure");
    let leftovers: Vec<_> = std::fs::read_dir(dir.path())
        .unwrap()
        .filter_map(|e| e.ok())
        .filter(|e| e.file_name() != "token")
        .collect();
    assert!(leftovers.is_empty(), "{leftovers:?}");
}

// The vector published in `docs/running-the-player.md` under "Home cache
// server", so the Android client can assert the same numbers against its
// own HMAC implementation without either side trusting the other's code.
// The key is the 64 ASCII bytes of this string itself, never hex-decoded —
// see `sign`'s doc comment.
const VECTOR_TOKEN: &str = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
const VECTOR_METHOD: &str = "PUT";
const VECTOR_PATH: &str = "/v1/sets/abc123/chunks/0";
const VECTOR_TOTAL: u64 = 5;
const VECTOR_BODY: &[u8] = b"hello";
const VECTOR_SIGNATURE: &str = "c1ac37f41c76c7c8434460c94458f58a817bf0859f9be493209d23a8ab9a4d85";

#[test]
fn the_vector_token_is_exactly_64_lowercase_hex_characters() {
    // A token this test vector, the docs and Android all key off of is
    // worth pinning its own shape down: a hand-edited 62 or 66 character
    // string here would still look plausible at a glance.
    assert_eq!(VECTOR_TOKEN.len(), 64);
    assert!(is_valid_token(VECTOR_TOKEN));
}

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
    let other_token = "f0112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
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
