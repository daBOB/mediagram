use super::*;

/// The fixture other phases share: three tracks, German (Forced), German,
/// English (SDH), two cues each.
const FIXTURE: &str = include_str!("../tests/fixtures/subtitle-bundle-v1.json");

fn gzip(bytes: &[u8]) -> Vec<u8> {
    let mut encoder = GzEncoder::new(Vec::new(), Compression::fast());
    encoder.write_all(bytes).unwrap();
    encoder.finish().unwrap()
}

fn sample_track(lang: &str) -> BundleTrack {
    BundleTrack {
        lang: lang.to_string(),
        forced: false,
        sdh: false,
        label: lang.to_string(),
        source: "embedded".to_string(),
        codec: "subrip".to_string(),
        vtt: "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi\n".to_string(),
    }
}

#[test]
fn a_bundle_round_trips_through_encode_and_decode() {
    let bundle = Bundle {
        v: BUNDLE_VERSION,
        set: "S1".to_string(),
        tracks: vec![sample_track("de"), sample_track("en")],
    };

    let decoded = decode(&encode(&bundle)).unwrap();

    assert_eq!(decoded, bundle);
}

#[test]
fn the_fixture_decodes_to_three_tracks_and_round_trips() {
    let fixture: Bundle = serde_json::from_str(FIXTURE).unwrap();
    assert_eq!(fixture.v, BUNDLE_VERSION);
    assert_eq!(fixture.tracks.len(), 3);
    assert_eq!(
        fixture
            .tracks
            .iter()
            .map(|t| (t.lang.as_str(), t.forced, t.sdh))
            .collect::<Vec<_>>(),
        [
            ("de", true, false),
            ("de", false, false),
            ("en", false, true)
        ]
    );

    assert_eq!(decode(&encode(&fixture)).unwrap(), fixture);
}

#[test]
fn compressed_bytes_over_the_cap_are_refused_before_decompression() {
    let oversized = vec![0u8; MAX_COMPRESSED_BYTES + 1];
    assert_eq!(
        decode(&oversized),
        Err(BundleError::TooLarge(oversized.len()))
    );
}

#[test]
fn decompressing_past_the_cap_is_a_bomb() {
    // All zeros, so the compressed form stays tiny while the decompressed
    // form alone crosses the cap.
    let oversized = vec![0u8; usize::try_from(MAX_DECOMPRESSED_BYTES).unwrap() + 1];
    let bytes = gzip(&oversized);
    assert!(
        bytes.len() < MAX_COMPRESSED_BYTES,
        "fixture assumption: zeros compress small"
    );

    assert_eq!(decode(&bytes), Err(BundleError::Bomb));
}

#[test]
fn a_track_s_cue_text_over_the_cap_is_refused() {
    let mut track = sample_track("de");
    track.vtt = "a".repeat(MAX_TRACK_BYTES + 1);
    let bundle = Bundle {
        v: BUNDLE_VERSION,
        set: "S1".to_string(),
        tracks: vec![track],
    };

    assert_eq!(
        decode(&encode(&bundle)),
        Err(BundleError::TrackTooLarge(MAX_TRACK_BYTES + 1))
    );
}

#[test]
fn a_newer_bundle_version_is_refused() {
    let bytes = gzip(br#"{"v":2,"set":"S1","tracks":[]}"#);

    assert_eq!(decode(&bytes), Err(BundleError::UnsupportedVersion(2)));
    assert_eq!(
        BundleError::UnsupportedVersion(0).to_string(),
        format!("unsupported bundle version 0 (this reader reads {BUNDLE_VERSION})"),
        "an older version is not called newer"
    );
}

#[test]
fn non_gzip_bytes_are_refused() {
    assert_eq!(decode(b"not a gzip stream"), Err(BundleError::NotGzip));
}

#[test]
fn valid_sha256_accepts_only_lowercase_64_hex() {
    let real = "a".repeat(64);
    assert!(valid_sha256(&real));
    assert!(!valid_sha256(&"A".repeat(64)), "uppercase is refused");
    assert!(!valid_sha256(&"a".repeat(63)), "too short is refused");
    assert!(!valid_sha256(
        "not hex at all, but sixty-four chars long padded out"
    ));
}

#[test]
fn the_caption_prefix_collides_with_neither_other_marker() {
    let caption = render_caption("01JQ8F2K9M4XZ00000000042");

    assert!(caption.starts_with(SUBS_CAPTION_PREFIX));
    assert!(
        !crate::caption_codec::is_mlib(&caption),
        "must not read as a part caption"
    );
    assert!(
        !crate::index_caption::is_index(&caption),
        "must not read as an index snapshot"
    );
}

#[test]
fn the_caption_names_the_set() {
    assert_eq!(render_caption("S1"), "#mlib-subs v=1\n{\"set\":\"S1\"}");
}

#[test]
fn the_bundle_file_name_is_the_set_id_with_a_fixed_suffix() {
    assert_eq!(bundle_file_name("S1"), "S1.subs.json.gz");
}
