use super::*;

#[test]
fn only_an_output_past_the_bundle_limit_counts_as_too_large() {
    let dir = tempfile::tempdir().unwrap();
    std::fs::write(dir.path().join("2.vtt"), vec![b'a'; MAX_TRACK_BYTES + 1]).unwrap();
    std::fs::write(dir.path().join("3.vtt"), b"WEBVTT\n").unwrap();

    assert!(too_large(dir.path(), 2));
    assert!(!too_large(dir.path(), 3));
    // A track that never came out was cut short, not oversized.
    assert!(!too_large(dir.path(), 4));
}
