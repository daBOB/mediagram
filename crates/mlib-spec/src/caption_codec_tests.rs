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
