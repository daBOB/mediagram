//! `set_hash` edges the crate's own unit test doesn't isolate: whitespace and
//! case normalization checked independently of each other, and the digest
//! length holding across input sizes from zero to many.

use mlib_spec::set_hash::set_hash;

#[test]
fn digest_length_is_64_hex_chars_regardless_of_how_many_parts_go_in() {
    assert_eq!(set_hash::<String>(&[]).len(), 64);
    assert_eq!(set_hash(&["abc123"]).len(), 64);
    let many: Vec<String> = (0..100).map(|i| format!("{i:064x}")).collect();
    assert_eq!(set_hash(&many).len(), 64);
}

#[test]
fn leading_and_trailing_whitespace_on_each_hash_is_trimmed_before_hashing() {
    let a = set_hash(&["abc123", "def456"]);
    let b = set_hash(&[" abc123 ", " def456 "]);
    assert_eq!(a, b);
}

#[test]
fn hash_case_is_normalized_before_hashing() {
    let a = set_hash(&["ABC123", "DEF456"]);
    let b = set_hash(&["abc123", "def456"]);
    assert_eq!(a, b);
}
