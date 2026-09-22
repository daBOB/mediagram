//! Pins `media::mp4_atoms::needs_faststart`'s box-scanning edge cases:
//! malformed or truncated boxes, extension-based short-circuiting, and
//! mdat/moov ordering — using hand-built byte layouts, not real media
//! files. Tests against real ffmpeg-built fixtures live in
//! `media_inspect.rs`.

use std::path::Path;

use mediagram::media;
use tempfile::NamedTempFile;

#[test]
fn empty_mp4_file_returns_false_without_panicking() {
    // A 0-byte file with a recognized MP4 extension: no boxes to find, so
    // no remux is needed — and the scan must not panic on an empty file.
    let temp = NamedTempFile::with_suffix(".mp4").unwrap();
    let result = media::mp4_atoms::needs_faststart(temp.path());
    assert!(result.is_ok());
    assert!(!result.unwrap());
}

#[test]
fn file_shorter_than_box_header_returns_false_without_panicking() {
    // A file shorter than the 8-byte box header: the scan must stop
    // cleanly rather than reading past the end of the file.
    let temp = NamedTempFile::with_suffix(".mp4").unwrap();
    std::fs::write(temp.path(), b"short").unwrap();

    let result = media::mp4_atoms::needs_faststart(temp.path());
    assert!(result.is_ok());
    assert!(!result.unwrap());
}

#[test]
fn uppercase_mp4_extension_is_recognized() {
    // `.MP4` (uppercase) is still MP4-family; a moov-before-mdat layout
    // needs no remux.
    let temp = NamedTempFile::with_suffix(".MP4").unwrap();
    let minimal_mp4 = vec![
        // moov box at offset 0: size=8, type=moov
        0x00, 0x00, 0x00, 0x08, b'm', b'o', b'o', b'v',
        // mdat box at offset 8: size=8, type=mdat
        0x00, 0x00, 0x00, 0x08, b'm', b'd', b'a', b't',
    ];
    std::fs::write(temp.path(), minimal_mp4).unwrap();

    let result = media::mp4_atoms::needs_faststart(temp.path());
    assert!(result.is_ok());
    assert!(!result.unwrap());
}

#[test]
fn webm_extension_short_circuits_without_reading_file() {
    // A non-MP4-family extension returns false without ever opening the
    // file — the path here doesn't exist, which would fail if it tried.
    let path = Path::new("/nonexistent/fake.webm");
    let result = media::mp4_atoms::needs_faststart(path);
    assert!(result.is_ok());
    assert!(!result.unwrap());
}

#[test]
fn atom_claiming_size_larger_than_file_resolves_to_false() {
    // A moov box claiming 1000 bytes in an 8-byte file: the scan runs past
    // the box, finds no mdat before EOF, and falls back to "no remux
    // needed" rather than panicking or erroring.
    let temp = NamedTempFile::with_suffix(".mp4").unwrap();
    let truncated = vec![
        0x00, 0x00, 0x03, 0xe8, // size = 1000 in big-endian
        b'm', b'o', b'o', b'v', // type = moov
    ];
    std::fs::write(temp.path(), truncated).unwrap();

    let result = media::mp4_atoms::needs_faststart(temp.path());
    assert!(result.is_ok());
    assert!(!result.unwrap());
}

#[test]
fn largesize_box_missing_its_64_bit_length_errors() {
    // size=1 promises a 64-bit largesize field right after the type, but
    // the file is truncated before it: this must error, not read garbage.
    let temp = NamedTempFile::with_suffix(".mp4").unwrap();
    let truncated = vec![
        0x00, 0x00, 0x00, 0x01, // size = 1 (largesize follows)
        b'm', b'o', b'o', b'v', // type = moov (largesize itself is missing)
    ];
    std::fs::write(temp.path(), truncated).unwrap();

    let result = media::mp4_atoms::needs_faststart(temp.path());
    assert!(result.is_err());
}

#[test]
fn mdat_before_moov_needs_remux() {
    // mdat ahead of moov is exactly the layout that needs a faststart
    // remux.
    let temp = NamedTempFile::with_suffix(".mp4").unwrap();
    let mdat_then_moov = vec![
        // mdat box at offset 0
        0x00, 0x00, 0x00, 0x08, b'm', b'd', b'a', b't', // moov box at offset 8
        0x00, 0x00, 0x00, 0x08, b'm', b'o', b'o', b'v',
    ];
    std::fs::write(temp.path(), mdat_then_moov).unwrap();

    let result = media::mp4_atoms::needs_faststart(temp.path());
    assert!(result.is_ok());
    assert!(result.unwrap());
}
