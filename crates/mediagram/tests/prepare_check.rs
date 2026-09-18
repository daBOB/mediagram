//! Each check that stands between ffmpeg's exit code and overwriting an
//! original. They exist because ffmpeg can exit 0 having produced something
//! that is not a usable replacement, and the replacement is irreversible.

use mediagram::media::prepare_check::{Rejection, check_prepared};
use mediagram::media::prepare_plan::{Stream, StreamKind};

fn stream(index: u32, kind: StreamKind, lang: Option<&str>) -> Stream {
    Stream {
        index,
        kind,
        language: lang.map(String::from),
        bit_rate: None,
        codec: None,
    }
}

fn good() -> Vec<Stream> {
    vec![
        stream(0, StreamKind::Video, Some("eng")),
        stream(1, StreamKind::Audio, Some("eng")),
        stream(2, StreamKind::Audio, Some("ger")),
    ]
}

fn expected() -> Vec<String> {
    vec!["eng".to_string(), "ger".to_string()]
}

#[test]
fn a_good_prepared_file_is_accepted() {
    assert_eq!(
        check_prepared(
            &good(),
            3_570_000_000,
            2547.1,
            4_426_249_518,
            2547.136,
            &expected(),
            false
        ),
        Ok(())
    );
}

#[test]
fn an_empty_output_is_rejected() {
    assert_eq!(
        check_prepared(
            &good(),
            0,
            2547.1,
            4_426_249_518,
            2547.136,
            &expected(),
            false
        ),
        Err(Rejection::Empty)
    );
    assert_eq!(
        check_prepared(
            &[],
            3_570_000_000,
            2547.1,
            4_426_249_518,
            2547.136,
            &expected(),
            false
        ),
        Err(Rejection::Empty)
    );
}

/// A bad `-map` can drop the picture and still exit 0.
#[test]
fn an_output_with_no_video_is_rejected() {
    let audio_only = vec![
        stream(1, StreamKind::Audio, Some("eng")),
        stream(2, StreamKind::Audio, Some("ger")),
    ];
    assert_eq!(
        check_prepared(
            &audio_only,
            100,
            2547.1,
            4_426_249_518,
            2547.136,
            &expected(),
            false
        ),
        Err(Rejection::NoVideo)
    );
}

#[test]
fn an_output_missing_a_language_we_meant_to_keep_is_rejected() {
    let no_german = vec![
        stream(0, StreamKind::Video, Some("eng")),
        stream(1, StreamKind::Audio, Some("eng")),
    ];
    assert_eq!(
        check_prepared(
            &no_german,
            100,
            2547.1,
            4_426_249_518,
            2547.136,
            &expected(),
            false
        ),
        Err(Rejection::MissingLanguage("ger".into()))
    );
}

/// The likeliest silent failure: a copy that stopped early.
#[test]
fn a_truncated_output_is_rejected() {
    let err = check_prepared(
        &good(),
        1_000_000,
        1200.0,
        4_426_249_518,
        2547.136,
        &expected(),
        false,
    );
    assert!(
        matches!(err, Err(Rejection::DurationChanged { .. })),
        "{err:?}"
    );
}

#[test]
fn a_sub_second_duration_difference_is_tolerated() {
    assert_eq!(
        check_prepared(
            &good(),
            3_570_000_000,
            2547.9,
            4_426_249_518,
            2547.136,
            &expected(),
            false
        ),
        Ok(())
    );
}

#[test]
fn an_output_that_grew_is_rejected() {
    let err = check_prepared(
        &good(),
        5_000_000_000,
        2547.1,
        4_426_249_518,
        2547.136,
        &expected(),
        false,
    );
    assert!(matches!(err, Err(Rejection::NotSmaller { .. })), "{err:?}");
}

#[test]
fn language_matching_is_case_insensitive() {
    let upper = vec![
        stream(0, StreamKind::Video, None),
        stream(1, StreamKind::Audio, Some("ENG")),
        stream(2, StreamKind::Audio, Some("GER")),
    ];
    assert_eq!(
        check_prepared(
            &upper,
            100,
            2547.1,
            4_426_249_518,
            2547.136,
            &expected(),
            false
        ),
        Ok(())
    );
}
