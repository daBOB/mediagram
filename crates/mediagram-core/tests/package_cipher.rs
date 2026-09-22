//! The package cipher: AES-256-GCM over the whole archive, with the
//! pointer's identifying fields as associated data so a replayed archive
//! under an edited pointer fails its tag instead of being accepted.

use mediagram_core::package::cipher::{EncryptError, open, parse_key, seal};

const AAD: &[u8] =
    br#"{"format":1,"created_at":1781568000,"key_id":"9f2c41ab","schema":1,"spec":2}"#;

fn key() -> [u8; 32] {
    [7u8; 32]
}

#[test]
fn a_sealed_archive_opens_back_to_the_exact_bytes() {
    let plaintext = b"an archive's worth of bytes".to_vec();
    let sealed = seal(&key(), &plaintext, AAD).unwrap();
    assert_eq!(open(&key(), &sealed, AAD).unwrap(), plaintext);
}

#[test]
fn an_empty_payload_round_trips() {
    let sealed = seal(&key(), b"", AAD).unwrap();
    assert_eq!(open(&key(), &sealed, AAD).unwrap(), Vec::<u8>::new());
}

#[test]
fn the_wrong_key_fails_instead_of_returning_garbage() {
    let sealed = seal(&key(), b"secret", AAD).unwrap();
    let other = [8u8; 32];
    assert!(matches!(open(&other, &sealed, AAD), Err(EncryptError::Tag)));
}

/// The whole point of the associated data: an attacker who rewrites the
/// pointer cannot make an otherwise-valid archive decrypt under it.
#[test]
fn an_edited_pointer_fails_the_tag() {
    let sealed = seal(&key(), b"catalog", AAD).unwrap();
    let edited = br#"{"format":1,"created_at":1999999999,"key_id":"9f2c41ab","schema":1,"spec":2}"#;
    assert!(matches!(
        open(&key(), &sealed, edited),
        Err(EncryptError::Tag)
    ));
}

#[test]
fn a_flipped_bit_anywhere_fails_rather_than_partially_succeeding() {
    let plaintext = vec![0x5au8; 4096];
    let sealed = seal(&key(), &plaintext, AAD).unwrap();
    for at in [0, 5, 12, 100, 2000, sealed.len() - 1] {
        let mut corrupt = sealed.clone();
        corrupt[at] ^= 0x01;
        assert!(
            matches!(open(&key(), &corrupt, AAD), Err(EncryptError::Tag)),
            "a flip at byte {at} must fail"
        );
    }
}

#[test]
fn truncation_fails_rather_than_returning_a_prefix() {
    let sealed = seal(&key(), &vec![1u8; 1024], AAD).unwrap();
    let truncated = &sealed[..sealed.len() - 1];
    assert!(open(&key(), truncated, AAD).is_err());
}

#[test]
fn a_file_too_short_to_hold_a_nonce_is_refused() {
    assert!(matches!(
        open(&key(), &[0u8; 8], AAD),
        Err(EncryptError::TooShort)
    ));
}

/// Nonce reuse under one key destroys GCM's guarantees, so two seals of the
/// same plaintext must not produce the same bytes.
#[test]
fn two_seals_of_the_same_payload_use_different_nonces() {
    let a = seal(&key(), b"same", AAD).unwrap();
    let b = seal(&key(), b"same", AAD).unwrap();
    assert_ne!(a[..12], b[..12], "nonce must be fresh per seal");
    assert_ne!(a, b);
}

#[test]
fn a_key_is_thirty_two_bytes_of_base64() {
    let encoded = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    let parsed = parse_key(encoded).unwrap();
    assert_eq!(parsed.len(), 32);
    assert_eq!(parsed[0], 0);
    assert_eq!(parsed[31], 31);
}

#[test]
fn a_key_of_the_wrong_length_is_refused_by_length_not_by_panic() {
    let short = "AAECAwQFBgcICQoLDA0ODw==";
    assert!(matches!(parse_key(short), Err(EncryptError::KeyLength(16))));
}

#[test]
fn a_key_that_is_not_base64_is_refused() {
    assert!(matches!(
        parse_key("not base64!!"),
        Err(EncryptError::KeyEncoding)
    ));
}

/// The key must never reach a log or an error message.
#[test]
fn errors_never_contain_key_material() {
    let secret = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    let rendered = format!("{}", parse_key("AAECAwQFBgcICQoLDA0ODw==").unwrap_err());
    assert!(!rendered.contains(secret) && !rendered.contains("AAEC"));
}
