//! The files sitting next to a lesson's video.
//!
//! The real course has `Begrüßung.mp4` beside `Begrüßung.vtt`, `.srt` and
//! `.txt`, all produced by the same transcription. Only the `.vtt` travels:
//! it is what a browser's `<track>` wants, and the `.srt` and `.txt` carry
//! the same words in formats nothing here can use. The Whisper `.json` and
//! `.tsv` are working files — 16 MB per course of them — and stay behind.
//!
//! A summary is optional and is not something Whisper produces, so it gets
//! its own suffix rather than competing with the transcript for `.txt`.

use std::fs;

use mediagram::course::sidecars::{Sidecars, find_sidecars};

fn folder(files: &[(&str, &str)]) -> tempfile::TempDir {
    let dir = tempfile::tempdir().unwrap();
    for (name, body) in files {
        fs::write(dir.path().join(name), body).unwrap();
    }
    dir
}

#[test]
fn the_vtt_beside_a_video_is_its_subtitle() {
    let dir = folder(&[
        ("Begrüßung.mp4", "video"),
        ("Begrüßung.vtt", "WEBVTT\n\nhallo"),
    ]);

    let found = find_sidecars(&dir.path().join("Begrüßung.mp4"));

    assert_eq!(found.subtitle.as_deref(), Some("WEBVTT\n\nhallo"));
}

/// Everything else in that folder is the same words in a format nothing here
/// uses, or a working file. None of it should travel.
#[test]
fn transcripts_and_working_files_are_left_behind() {
    let dir = folder(&[
        ("L.mp4", "video"),
        ("L.srt", "1\n00:00 --> 00:01\nhallo"),
        ("L.txt", "hallo"),
        ("L.json", "{}"),
        ("L.tsv", "start\tend\ttext"),
    ]);

    let found = find_sidecars(&dir.path().join("L.mp4"));

    assert_eq!(found, Sidecars::default());
}

#[test]
fn a_summary_is_taken_from_its_own_suffix() {
    for name in ["L.summary.md", "L.summary.txt"] {
        let dir = folder(&[("L.mp4", "video"), (name, "Worum es geht.")]);

        let found = find_sidecars(&dir.path().join("L.mp4"));

        assert_eq!(found.summary.as_deref(), Some("Worum es geht."), "{name}");
    }
}

/// The transcript is also `.txt`; a summary must not be confused with it.
#[test]
fn a_transcript_is_not_mistaken_for_a_summary() {
    let dir = folder(&[("L.mp4", "video"), ("L.txt", "the whole transcript")]);

    assert_eq!(
        find_sidecars(&dir.path().join("L.mp4")).summary,
        None
    );
}

#[test]
fn a_lesson_with_nothing_beside_it_has_no_sidecars() {
    let dir = folder(&[("L.mp4", "video")]);

    assert_eq!(
        find_sidecars(&dir.path().join("L.mp4")),
        Sidecars::default()
    );
}

/// A faststart remux is `name.faststart.mp4`; its sidecars sit under the
/// original stem, and it must still find them.
#[test]
fn a_remuxed_video_still_finds_the_originals_sidecars() {
    let dir = folder(&[
        ("L.faststart.mp4", "video"),
        ("L.vtt", "WEBVTT\n\nhallo"),
        ("L.summary.md", "kurz"),
    ]);

    let found = find_sidecars(&dir.path().join("L.faststart.mp4"));

    assert_eq!(found.subtitle.as_deref(), Some("WEBVTT\n\nhallo"));
    assert_eq!(found.summary.as_deref(), Some("kurz"));
}

/// The index is published in a package with a ceiling; one enormous file
/// should be skipped with a warning rather than refused or silently included.
#[test]
fn an_oversized_sidecar_is_skipped_rather_than_stored() {
    let dir = folder(&[
        ("L.mp4", "video"),
        (
            "L.vtt",
            &"x".repeat(mediagram::index::assets::MAX_ASSET_BYTES + 1),
        ),
    ]);

    let found = find_sidecars(&dir.path().join("L.mp4"));

    assert_eq!(found.subtitle, None, "an oversized subtitle is not carried");
}

#[test]
fn a_file_that_is_not_text_is_not_carried() {
    let dir = tempfile::tempdir().unwrap();
    fs::write(dir.path().join("L.mp4"), "video").unwrap();
    fs::write(dir.path().join("L.vtt"), [0xff, 0xfe, 0x00, 0x01]).unwrap();

    assert_eq!(
        find_sidecars(&dir.path().join("L.mp4")).subtitle,
        None
    );
}
