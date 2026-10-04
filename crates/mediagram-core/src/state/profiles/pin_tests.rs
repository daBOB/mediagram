use super::*;

#[test]
fn only_exactly_four_ascii_digits_are_a_pin() {
    assert!(valid("0000") && valid("1234"));
    for bad in ["", "123", "12345", "12a4", " 1234", "1234\n", "١٢٣٤"] {
        assert!(!valid(bad), "{bad:?}");
    }
}

#[test]
fn a_new_salt_is_sixteen_random_bytes_in_lowercase_hex() {
    let (a, b) = (new_salt(), new_salt());
    assert_eq!(a.len(), 32);
    assert!(a.bytes().all(|c| matches!(c, b'0'..=b'9' | b'a'..=b'f')));
    assert_ne!(a, b);
}

#[test]
fn a_pin_matches_only_the_hash_made_from_it() {
    let salt = new_salt();
    let stored = hash(&salt, "1234");
    assert!(matches(&stored, &salt, "1234"));
    assert!(!matches(&stored, &salt, "1235"));
    assert!(!matches(&stored, &new_salt(), "1234"));
}

/// A stored hash of the wrong length cannot be this PIN's, whatever it
/// starts with — the web says so before `timingSafeEqual`, which would throw.
#[test]
fn a_stored_hash_of_the_wrong_length_never_matches() {
    let salt = new_salt();
    let stored = hash(&salt, "1234");
    assert!(!matches(&stored[..63], &salt, "1234"));
    assert!(!matches(&format!("{stored}0"), &salt, "1234"));
    assert!(!matches("", &salt, "1234"));
}
