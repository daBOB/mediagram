//! The portable session format the player backend reads.
//!
//! A `StringSession` is how teleproto carries an authorization: version byte,
//! then base64 of the datacentre id, its address, its port and the 256-byte
//! auth key. Producing one from the uploader's own session is what lets a
//! player run on another machine without a second login.
//!
//! The layout is not ours and is not negotiable, so it is pinned here rather
//! than described in a comment.

use mediagram::telegram::string_session::{SessionError, encode_string_session};

use base64::Engine;

const KEY: [u8; 256] = [7u8; 256];

fn decode(session: &str) -> Vec<u8> {
    assert_eq!(&session[..1], "1", "the format version byte");
    base64::engine::general_purpose::STANDARD
        .decode(&session[1..])
        .expect("the body is base64")
}

#[test]
fn the_layout_is_version_dc_address_port_and_key() {
    let session = encode_string_session(4, "149.154.167.92", 443, &KEY).unwrap();
    let blob = decode(&session);

    let host = b"149.154.167.92";
    assert_eq!(blob[0], 4, "datacentre id, one byte");
    assert_eq!(
        i16::from_be_bytes([blob[1], blob[2]]) as usize,
        host.len(),
        "address length, big-endian i16"
    );
    assert_eq!(&blob[3..3 + host.len()], host, "the address itself");

    let after = 3 + host.len();
    assert_eq!(
        i16::from_be_bytes([blob[after], blob[after + 1]]),
        443,
        "port, big-endian i16"
    );
    assert_eq!(&blob[after + 2..], &KEY[..], "the auth key, verbatim");
    assert_eq!(blob.len(), 3 + host.len() + 2 + 256);
}

/// The string the player is given must round-trip to the same auth key, or
/// the player authorizes as nobody.
#[test]
fn the_key_survives_encoding_unchanged() {
    let mut key = [0u8; 256];
    for (i, byte) in key.iter_mut().enumerate() {
        *byte = (i % 251) as u8;
    }
    let blob = decode(&encode_string_session(2, "149.154.167.41", 443, &key).unwrap());

    assert_eq!(&blob[blob.len() - 256..], &key[..]);
}

#[test]
fn a_key_of_the_wrong_length_is_refused() {
    assert!(matches!(
        encode_string_session(4, "host", 443, &[0u8; 255]),
        Err(SessionError::KeyLength(255))
    ));
    assert!(matches!(
        encode_string_session(4, "host", 443, &[]),
        Err(SessionError::KeyLength(0))
    ));
}

#[test]
fn an_address_too_long_to_describe_is_refused() {
    let host = "a".repeat(40_000);
    assert!(matches!(
        encode_string_session(4, &host, 443, &KEY),
        Err(SessionError::AddressLength(40_000))
    ));
}

/// Two accounts, or two datacentres, must never produce the same string.
#[test]
fn different_inputs_give_different_sessions() {
    let a = encode_string_session(4, "149.154.167.92", 443, &KEY).unwrap();
    let b = encode_string_session(2, "149.154.167.92", 443, &KEY).unwrap();
    let c = encode_string_session(4, "149.154.167.92", 443, &[9u8; 256]).unwrap();

    assert_ne!(a, b);
    assert_ne!(a, c);
}

/// The one shape the player will actually be handed: teleproto rejects a
/// string whose first character is not the current version.
#[test]
fn the_session_is_a_single_line_of_printable_text() {
    let session = encode_string_session(4, "149.154.167.92", 443, &KEY).unwrap();

    assert!(!session.contains('\n'));
    assert!(session.chars().all(|c| c.is_ascii_graphic()));
    assert_eq!(session.len(), 1 + 4 * ((3 + 14 + 2 + 256) + 2) / 3 / 4 * 4);
}
