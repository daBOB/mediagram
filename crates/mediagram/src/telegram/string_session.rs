//! Exporting an authorization as a portable string.
//!
//! The player backend runs teleproto, which carries an authorization as a
//! `StringSession`: a version byte followed by base64 of the datacentre id,
//! its address, its port and the 256-byte auth key. Building one from the
//! uploader's own session means a player on another machine needs no second
//! login.
//!
//! The layout belongs to teleproto, not to us. It is reproduced exactly;
//! `tests/string_session.rs` pins every field.

use base64::Engine;

/// teleproto refuses a string whose first character is not this.
const VERSION: &str = "1";

const AUTH_KEY_LEN: usize = 256;

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum SessionError {
    #[error("an auth key is {AUTH_KEY_LEN} bytes, got {0}")]
    KeyLength(usize),
    #[error("a datacentre address of {0} bytes does not fit the format")]
    AddressLength(usize),
}

/// Encodes one datacentre's authorization as a teleproto `StringSession`.
///
/// The result is the account. It belongs on a terminal or in an environment
/// variable, never in a file the repository can see or a log a reader can.
pub fn encode_string_session(
    dc_id: u8,
    address: &str,
    port: u16,
    auth_key: &[u8],
) -> Result<String, SessionError> {
    if auth_key.len() != AUTH_KEY_LEN {
        return Err(SessionError::KeyLength(auth_key.len()));
    }
    let address = address.as_bytes();
    let address_len =
        i16::try_from(address.len()).map_err(|_| SessionError::AddressLength(address.len()))?;

    let mut blob = Vec::with_capacity(1 + 2 + address.len() + 2 + AUTH_KEY_LEN);
    blob.push(dc_id);
    blob.extend_from_slice(&address_len.to_be_bytes());
    blob.extend_from_slice(address);
    blob.extend_from_slice(&(port as i16).to_be_bytes());
    blob.extend_from_slice(auth_key);

    Ok(format!(
        "{VERSION}{}",
        base64::engine::general_purpose::STANDARD.encode(&blob)
    ))
}
