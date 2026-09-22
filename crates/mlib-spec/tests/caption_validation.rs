//! Captions come off a Telegram channel, so `parse` is a trust boundary.
//! Anyone who can post a message can choose these values, and `rescan` writes
//! them into the index, where `set` becomes a primary key and, in the package
//! export, part of a file name.

use mlib_spec::caption_codec::{CaptionError, parse};

fn caption_with(set: &str) -> String {
    format!(
        "#mlib v=2\n{{\"t\":\"movie\",\"ids\":{{\"tmdb\":1,\"tvdb\":null,\"imdb\":null}},\
\"show\":null,\"title\":\"T\",\"year\":2024,\"s\":null,\"e\":null,\"abs\":null,\"q\":null,\
\"hdr\":null,\"container\":\"mkv\",\"vcodec\":null,\"acodec\":null,\"alang\":[],\"slang\":[],\
\"dur\":null,\"variant\":null,\"set\":\"{set}\",\
\"part\":{{\"i\":0,\"n\":1,\"off\":0,\"len\":10,\"sha256\":\"\"}},\"total\":10}}"
    )
}

#[test]
fn a_normal_set_id_parses() {
    let caption = parse(&caption_with("01JQ8F2K9M4XZ00000000001")).unwrap();
    assert_eq!(caption.set, "01JQ8F2K9M4XZ00000000001");
}

#[test]
fn a_set_id_that_could_reach_a_path_is_refused() {
    // These are well-formed JSON, so they reach the validator.
    for hostile in [
        "../../../../etc/passwd",
        "..",
        "a/b",
        "a\\b",
        "/absolute",
        "with space",
        "",
    ] {
        assert!(
            matches!(
                parse(&caption_with(hostile)),
                Err(CaptionError::InvalidField(_))
            ),
            "set `{hostile}` must be refused by validation"
        );
    }
}

/// A raw control character truncates or breaks the JSON line before the
/// validator sees it. Rejected either way, which is what matters; this pins
/// that neither path lets one through.
#[test]
fn a_set_id_carrying_a_control_character_never_parses() {
    for hostile in ["with\nnewline", "with\0null", "with\ttab"] {
        assert!(
            parse(&caption_with(hostile)).is_err(),
            "set `{}` must be refused",
            hostile.escape_debug()
        );
    }
}

#[test]
fn an_absurdly_long_set_id_is_refused() {
    let long = "A".repeat(200);
    assert!(matches!(
        parse(&caption_with(&long)),
        Err(CaptionError::InvalidField(_))
    ));
}

/// The index stores lengths and offsets as signed 64-bit integers, so a
/// caption claiming more than that round-trips to nonsense.
#[test]
fn a_part_length_beyond_the_index_range_is_refused() {
    let caption = caption_with("01JQ8F2K9M4XZ00000000001")
        .replace("\"len\":10", "\"len\":18446744073709551615");
    assert!(matches!(
        parse(&caption),
        Err(CaptionError::InvalidField(_))
    ));
}

#[test]
fn a_part_index_outside_its_own_count_is_refused() {
    let caption =
        caption_with("01JQ8F2K9M4XZ00000000001").replace("\"i\":0,\"n\":1", "\"i\":5,\"n\":2");
    assert!(matches!(
        parse(&caption),
        Err(CaptionError::InvalidField(_))
    ));
}

#[test]
fn a_zero_part_count_is_refused() {
    let caption = caption_with("01JQ8F2K9M4XZ00000000001").replace("\"n\":1", "\"n\":0");
    assert!(matches!(
        parse(&caption),
        Err(CaptionError::InvalidField(_))
    ));
}

/// A malformed hash is deliberately allowed through: it cannot reach a path
/// or a key, it only ever gets compared, and refusing it would make `rescan`
/// drop a part it could otherwise recover. `verify` is where a wrong hash
/// should surface.
#[test]
fn a_malformed_part_hash_still_parses_so_recovery_keeps_the_part() {
    let caption =
        caption_with("01JQ8F2K9M4XZ00000000001").replace("\"sha256\":\"\"", "\"sha256\":\"zz\"");
    assert_eq!(parse(&caption).unwrap().part.sha256, "zz");
}

#[test]
fn a_valid_part_hash_parses() {
    let caption = caption_with("01JQ8F2K9M4XZ00000000001").replace(
        "\"sha256\":\"\"",
        &format!("\"sha256\":\"{}\"", "ab".repeat(32)),
    );
    assert!(parse(&caption).is_ok());
}

/// A BOM is not ASCII whitespace, so `trim_start` leaves it in place and the
/// marker check never sees `#mlib` at the start of the text.
#[test]
fn a_byte_order_mark_before_the_marker_is_not_tolerated() {
    let text = format!("\u{FEFF}{}", caption_with("01JQ8F2K9M4XZ00000000001"));
    assert!(matches!(parse(&text), Err(CaptionError::NoMarker)));
}

/// A future writer can add fields to the JSON line; serde ignores what it
/// does not know about instead of rejecting the caption.
#[test]
fn unknown_json_fields_are_ignored_for_forward_compatibility() {
    let text = "#mlib v=2\n{\"t\":\"movie\",\"ids\":{},\"show\":null,\"title\":\"Dune\",\"year\":2021,\
\"s\":null,\"e\":null,\"abs\":null,\"q\":null,\"hdr\":null,\"container\":\"mkv\",\"vcodec\":null,\
\"acodec\":null,\"alang\":[],\"slang\":[],\"dur\":null,\"variant\":null,\"set\":\"01ABC\",\
\"part\":{\"i\":0,\"n\":1,\"off\":0,\"len\":10,\"sha256\":\"\"},\"total\":10,\
\"future_feature\":\"unused\",\"another_unknown\":42}";
    let caption = parse(text).unwrap();
    assert_eq!(caption.title.as_deref(), Some("Dune"));
}
