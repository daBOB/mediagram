use super::*;

#[test]
fn a_short_alphanumeric_id_is_valid() {
    assert!(valid_id("abc123"));
}

#[test]
fn the_longest_allowed_id_is_valid() {
    assert!(valid_id(&"a".repeat(64)));
}

#[test]
fn one_id_char_is_valid() {
    assert!(valid_id("a"));
}

#[test]
fn an_empty_id_is_invalid() {
    assert!(!valid_id(""));
}

#[test]
fn an_id_one_char_over_the_limit_is_invalid() {
    assert!(!valid_id(&"a".repeat(65)));
}

#[test]
fn a_traversal_attempt_is_invalid() {
    assert!(!valid_id(".."));
    assert!(!valid_id("../etc/passwd"));
}

#[test]
fn an_id_with_a_slash_is_invalid() {
    assert!(!valid_id("a/b"));
}

#[test]
fn an_id_with_punctuation_is_invalid() {
    assert!(!valid_id("abc-123"));
    assert!(!valid_id("abc_123"));
    assert!(!valid_id("abc.123"));
    assert!(!valid_id("abc 123"));
}

#[test]
fn a_full_chunk_is_a_valid_length() {
    // Chunk 0 of 3, each full CHUNK bytes.
    assert!(check_length(0, CHUNK, CHUNK * 3).is_ok());
}

#[test]
fn the_final_short_chunk_is_a_valid_length() {
    let total = CHUNK * 2 + 100;
    assert!(check_length(2, 100, total).is_ok());
}

#[test]
fn a_final_chunk_that_happens_to_be_a_full_chunk_is_valid() {
    let total = CHUNK * 3;
    assert!(check_length(2, CHUNK, total).is_ok());
}

#[test]
fn an_oversize_chunk_is_rejected() {
    let total = CHUNK * 3;
    assert_eq!(
        check_length(0, CHUNK + 1, total),
        Err(LengthError::BadLength)
    );
}

#[test]
fn an_undersize_non_final_chunk_is_rejected() {
    let total = CHUNK * 3;
    assert_eq!(
        check_length(0, CHUNK - 1, total),
        Err(LengthError::BadLength)
    );
}

#[test]
fn a_chunk_starting_at_or_past_total_is_rejected() {
    let total = CHUNK * 2;
    assert_eq!(check_length(2, CHUNK, total), Err(LengthError::PastTotal));
    assert_eq!(check_length(3, 1, total), Err(LengthError::PastTotal));
}

#[test]
fn a_zero_length_chunk_past_a_zero_total_is_rejected_as_past_total() {
    assert_eq!(check_length(0, 0, 0), Err(LengthError::PastTotal));
}

#[test]
fn parsing_a_decimal_chunk_number_succeeds() {
    assert_eq!(parse_chunk_num("0"), Some(0));
    assert_eq!(parse_chunk_num("42"), Some(42));
    assert_eq!(parse_chunk_num(&u32::MAX.to_string()), Some(u32::MAX));
}

#[test]
fn parsing_a_non_numeric_chunk_number_fails() {
    assert_eq!(parse_chunk_num("abc"), None);
    assert_eq!(parse_chunk_num(""), None);
    assert_eq!(parse_chunk_num("-1"), None);
    assert_eq!(parse_chunk_num("1.5"), None);
    // A leading zero is not how axum-side player URLs are built, but a
    // well-formed decimal is still accepted: only the shape of the id is a
    // security boundary, not the chunk number's spelling.
    assert_eq!(parse_chunk_num("007"), Some(7));
}
