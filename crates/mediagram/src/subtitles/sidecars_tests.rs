use super::*;

const VTT: &str = "WEBVTT\n\n00:00.000 --> 00:01.000\nhallo\n";
const SRT: &[u8] = b"1\n00:00:00,000 --> 00:00:01,000\nhallo\n";

fn folder(files: &[(&str, &[u8])]) -> tempfile::TempDir {
    let dir = tempfile::tempdir().unwrap();
    for (name, body) in files {
        std::fs::write(dir.path().join(name), body).unwrap();
    }
    dir
}

fn found(dir: &tempfile::TempDir, video: &str, audio: Option<&str>) -> Vec<Sidecar> {
    discover(&dir.path().join(video), audio)
}

fn shapes(found: &[Sidecar]) -> Vec<(&str, bool, bool)> {
    found
        .iter()
        .map(|s| (s.lang.as_str(), s.forced, s.sdh))
        .collect()
}

#[test]
fn a_plain_vtt_is_in_the_audio_language() {
    let dir = folder(&[("x.mp4", b"v"), ("x.vtt", VTT.as_bytes())]);

    let found = found(&dir, "x.mp4", Some("de"));

    assert_eq!(shapes(&found), [("de", false, false)]);
    assert_eq!(found[0].payload, Payload::Vtt(VTT.to_string()));
}

#[test]
fn a_plain_srt_with_no_audio_language_is_und() {
    let dir = folder(&[("x.mp4", b"v"), ("x.srt", SRT)]);

    let found = found(&dir, "x.mp4", None);

    assert_eq!(shapes(&found), [("und", false, false)]);
    assert!(matches!(found[0].payload, Payload::Srt(_)));
}

#[test]
fn a_vtt_and_an_srt_of_one_track_keep_only_the_vtt() {
    let dir = folder(&[("x.mp4", b"v"), ("x.vtt", VTT.as_bytes()), ("x.srt", SRT)]);

    let found = found(&dir, "x.mp4", Some("de"));

    assert_eq!(found.len(), 1);
    assert!(matches!(found[0].payload, Payload::Vtt(_)));
}

#[test]
fn names_carry_language_and_flags() {
    let dir = folder(&[
        ("x.mkv", b"v"),
        ("x.de.srt", SRT),
        ("x.en.forced.srt", SRT),
        ("x German.srt", SRT),
        ("x.English.SDH.srt", SRT),
    ]);

    let all = found(&dir, "x.mkv", None);
    let mut got = shapes(&all);
    got.sort();

    // "x German.srt" and "x.de.srt" are the same track.
    assert_eq!(
        got,
        [
            ("de", false, false),
            ("en", false, true),
            ("en", true, false)
        ]
    );
}

#[test]
fn a_language_that_is_not_kept_is_skipped() {
    let dir = folder(&[("x.mp4", b"v"), ("x.fr.srt", SRT)]);
    assert!(found(&dir, "x.mp4", None).is_empty());

    // No language in the name: the audio's, which is not kept either.
    let dir = folder(&[("x.mp4", b"v"), ("x.srt", SRT)]);
    assert!(found(&dir, "x.mp4", Some("fr")).is_empty());
}

#[test]
fn a_numbered_neighbour_is_another_videos() {
    let dir = folder(&[
        ("x 1.mp4", b"v"),
        ("x 10.srt", SRT),
        ("x 1 - Recap.srt", SRT),
        ("x 1.srt", SRT),
    ]);

    let found = found(&dir, "x 1.mp4", Some("en"));

    assert_eq!(found.len(), 1);
    assert_eq!(found[0].payload, Payload::Srt(dir.path().join("x 1.srt")));
}

#[test]
fn a_remux_finds_the_originals_sidecars() {
    let dir = folder(&[("x.faststart.mp4", b"v"), ("x.en.vtt", VTT.as_bytes())]);

    assert_eq!(
        shapes(&found(&dir, "x.faststart.mp4", None)),
        [("en", false, false)]
    );
}

#[test]
fn a_vtt_that_is_not_webvtt_is_ignored_and_the_srt_takes_its_place() {
    let dir = folder(&[
        ("x.mp4", b"v"),
        ("x.vtt", b"not a subtitle"),
        ("x.srt", SRT),
    ]);

    let found = found(&dir, "x.mp4", Some("de"));

    assert_eq!(found.len(), 1);
    assert!(matches!(found[0].payload, Payload::Srt(_)));
}

#[test]
fn a_vtt_that_is_not_utf8_is_ignored() {
    let dir = folder(&[("x.mp4", b"v"), ("x.vtt", &[0x57, 0x45, 0xfc])]);

    assert!(found(&dir, "x.mp4", Some("de")).is_empty());
}
