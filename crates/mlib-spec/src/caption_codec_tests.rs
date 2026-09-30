use super::*;

#[test]
fn rejects_other_versions_and_garbage() {
    assert!(matches!(
        parse("#mlib v=1\n{}"),
        Err(CaptionError::UnsupportedVersion(_))
    ));
    assert!(matches!(parse("hello"), Err(CaptionError::NoMarker)));
    assert!(matches!(
        parse("#mlib v=2\n"),
        Err(CaptionError::MissingJson)
    ));
    assert!(is_mlib("#mlib v=3\n{}"));
    assert!(!is_mlib("#mlib-index v=2"));
}
/// The caption marker and the package's spec number describe one wire version.
#[test]
fn the_marker_names_the_spec_version() {
    assert_eq!(super::MARKER, format!("#mlib v={}", crate::SPEC_VERSION));
}

#[test]
fn unread_set_reads_the_set_id_from_a_version_this_build_refuses_to_parse() {
    assert!(matches!(
        parse("#mlib v=99\n{\"set\":\"ABC\"}"),
        Err(CaptionError::UnsupportedVersion(_))
    ));
    assert_eq!(
        unread_set("#mlib v=99\n{\"set\":\"ABC\"}"),
        Some("ABC".to_string())
    );
}

#[test]
fn unread_set_is_none_for_text_with_no_set_field_or_no_json_line() {
    assert_eq!(unread_set("#mlib v=99\n{}"), None);
    assert_eq!(unread_set("#mlib v=99"), None);
    assert_eq!(unread_set("not a caption at all"), None);
}
