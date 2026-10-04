//! A grown-up's PIN: four digits, kept as a salted SHA-256 — the same string
//! the web computes for the same salt and PIN (`pin-hash.json` pins the two),
//! which is what lets a PIN set on the television open the profile on the
//! laptop.
//!
//! Not a password hash in any strong sense, and not meant as one: anyone
//! holding the sync document can try all ten thousand. That document lives in
//! the household's own channel, readable only by the account that owns the
//! library, and the people a PIN is for cannot read it — a slow hash would buy
//! nothing against that. It stops a child tapping into a grown-up's profile,
//! not someone with `adb`.

use sha2::{Digest, Sha256};

/// Exactly four ASCII digits; anything else is refused where it arrives.
pub fn valid(pin: &str) -> bool {
    pin.len() == 4 && pin.bytes().all(|b| b.is_ascii_digit())
}

/// Lowercase hex SHA-256 of the UTF-8 bytes of `salt + pin`.
pub fn hash(salt: &str, pin: &str) -> String {
    hex::encode(Sha256::digest(format!("{salt}{pin}")))
}

/// A fresh salt: sixteen random bytes, lowercase hex.
pub(crate) fn new_salt() -> String {
    let mut bytes = [0u8; 16];
    getrandom::fill(&mut bytes).expect("the OS random source is available");
    hex::encode(bytes)
}

/// Whether `pin` is the one stored as `stored` under `salt`. Every byte of
/// the two digests is compared however early they differ, so how long a wrong
/// guess takes says nothing about how close it came; a stored hash of the
/// wrong length cannot be this PIN's.
pub fn matches(stored: &str, salt: &str, pin: &str) -> bool {
    let given = hash(salt, pin);
    let differ = given
        .bytes()
        .zip(stored.bytes())
        .fold(0u8, |acc, (a, b)| acc | (a ^ b));
    given.len() == stored.len() && differ == 0
}

#[cfg(test)]
#[path = "pin_tests.rs"]
mod tests;
