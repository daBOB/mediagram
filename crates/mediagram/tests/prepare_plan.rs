//! Deciding what to drop. Pure, because the dry-run table and the real run
//! must agree exactly: the real run replaces the original, and there is no
//! undo for a file whose tracks were dropped on a different basis than the
//! table showed.

use mediagram::media::prepare_plan::{Stream, StreamKind, Verdict, plan_prepare};

fn video() -> Stream {
    Stream {
        index: 0,
        kind: StreamKind::Video,
        language: Some("eng".into()),
        bit_rate: None,
        codec: None,
    }
}
fn audio(index: u32, lang: &str, bit_rate: u64) -> Stream {
    Stream {
        index,
        kind: StreamKind::Audio,
        language: Some(lang.into()),
        bit_rate: Some(bit_rate),
        codec: None,
    }
}
fn subtitle(index: u32, lang: &str) -> Stream {
    Stream {
        index,
        kind: StreamKind::Subtitle,
        language: Some(lang.into()),
        bit_rate: None,
        codec: None,
    }
}

const KEEP: &[&str] = &["ger", "deu", "eng"];
const LIMIT: u64 = 3_758_096_384;

/// The measured episode: nine audio tracks, seven of them unwanted.
fn real_episode() -> Vec<Stream> {
    let mut s = vec![video(), audio(1, "eng", 768_000), audio(2, "ger", 384_000)];
    for (i, lang) in ["spa", "spa", "fre", "ita", "por", "jpn", "kor"]
        .iter()
        .enumerate()
    {
        s.push(audio(3 + i as u32, lang, 384_000));
    }
    for i in 0..42 {
        s.push(subtitle(10 + i, if i % 2 == 0 { "eng" } else { "spa" }));
    }
    s
}

#[test]
fn unwanted_audio_is_dropped_and_wanted_audio_is_kept() {
    let plan = plan_prepare(&real_episode(), 4_426_249_518, 2547.136, KEEP, KEEP, LIMIT);

    let kept: Vec<u32> = plan.keep.iter().map(|s| s.index).collect();
    assert!(kept.contains(&0), "the video stream is always kept");
    assert!(
        kept.contains(&1) && kept.contains(&2),
        "eng and ger audio kept"
    );
    assert!(!kept.contains(&3), "spanish dropped");
    assert_eq!(plan.dropped_audio, 7);
}

#[test]
fn the_estimate_matches_the_measured_saving() {
    let plan = plan_prepare(&real_episode(), 4_426_249_518, 2547.136, KEEP, KEEP, LIMIT);

    // 7 tracks x 384 kbps over 2547s is about 0.86 GB.
    let saved = 4_426_249_518 - plan.estimated_bytes;
    assert!(
        (820_000_000..=900_000_000).contains(&saved),
        "saving looks wrong: {saved}"
    );
    assert!(plan.estimated_bytes < LIMIT, "should fit one part");
    assert_eq!(plan.verdict, Verdict::Prepare);
}

/// The episode that stays too big even after pruning. Reported, not degraded.
#[test]
fn a_file_that_stays_over_the_limit_is_still_worth_preparing_but_says_so() {
    let plan = plan_prepare(&real_episode(), 5_010_000_000, 2547.136, KEEP, KEEP, LIMIT);
    assert_eq!(plan.verdict, Verdict::PrepareStillOversized);
    assert!(plan.estimated_bytes > LIMIT);
}

/// Nothing is destroyed without cause.
#[test]
fn a_file_already_under_the_limit_is_left_alone() {
    let plan = plan_prepare(&real_episode(), 2_000_000_000, 2547.136, KEEP, KEEP, LIMIT);
    assert_eq!(plan.verdict, Verdict::AlreadyFits);
}

#[test]
fn a_file_with_nothing_to_drop_is_left_alone() {
    let streams = vec![video(), audio(1, "eng", 768_000), audio(2, "ger", 384_000)];
    let plan = plan_prepare(&streams, 4_400_000_000, 2547.136, KEEP, KEEP, LIMIT);
    assert_eq!(plan.verdict, Verdict::NothingToDrop);
}

/// An untagged track is kept: dropping something we cannot identify is how a
/// library loses its only audio track.
#[test]
fn a_track_with_no_language_tag_is_kept() {
    let streams = vec![
        video(),
        Stream {
            index: 1,
            kind: StreamKind::Audio,
            language: None,
            bit_rate: Some(768_000),
            codec: None,
        },
        audio(2, "spa", 384_000),
    ];
    let plan = plan_prepare(&streams, 4_400_000_000, 2547.136, KEEP, KEEP, LIMIT);
    assert!(
        plan.keep.iter().any(|s| s.index == 1),
        "untagged audio kept"
    );
    assert_eq!(plan.dropped_audio, 1);
}

#[test]
fn language_matching_ignores_case_and_accepts_both_german_codes() {
    let streams = vec![
        video(),
        audio(1, "GER", 384_000),
        audio(2, "deu", 384_000),
        audio(3, "spa", 384_000),
    ];
    let plan = plan_prepare(&streams, 4_400_000_000, 2547.136, KEEP, KEEP, LIMIT);
    assert_eq!(plan.dropped_audio, 1);
    assert_eq!(
        plan.keep
            .iter()
            .filter(|s| s.kind == StreamKind::Audio)
            .count(),
        2
    );
}

#[test]
fn subtitles_are_filtered_by_their_own_keep_list() {
    let plan = plan_prepare(
        &real_episode(),
        4_426_249_518,
        2547.136,
        KEEP,
        &["ger"],
        LIMIT,
    );
    let kept_subs = plan
        .keep
        .iter()
        .filter(|s| s.kind == StreamKind::Subtitle)
        .count();
    assert_eq!(
        kept_subs, 0,
        "no german subs in the fixture, so all are dropped"
    );
    assert_eq!(plan.dropped_subtitles, 42);
}

/// A track with no bitrate cannot be estimated; assuming zero is honest and
/// conservative, since it only ever makes the estimate pessimistic.
#[test]
fn a_dropped_track_with_no_bitrate_contributes_nothing_to_the_estimate() {
    let streams = vec![
        video(),
        audio(1, "eng", 768_000),
        Stream {
            index: 2,
            kind: StreamKind::Audio,
            language: Some("spa".into()),
            bit_rate: None,
            codec: None,
        },
    ];
    let plan = plan_prepare(&streams, 4_400_000_000, 2547.136, KEEP, KEEP, LIMIT);
    assert_eq!(plan.estimated_bytes, 4_400_000_000);
    assert_eq!(plan.dropped_audio, 1);
}

#[test]
fn a_zero_duration_file_does_not_divide_by_it() {
    let plan = plan_prepare(&real_episode(), 4_400_000_000, 0.0, KEEP, KEEP, LIMIT);
    assert_eq!(plan.estimated_bytes, 4_400_000_000);
}

/// The ffmpeg argument list is derived from the same plan the table showed.
#[test]
fn the_map_arguments_name_exactly_the_kept_streams() {
    let plan = plan_prepare(&real_episode(), 4_426_249_518, 2547.136, KEEP, KEEP, LIMIT);
    let args = plan.map_args();
    let maps: Vec<&String> = args.iter().filter(|a| a.starts_with("0:")).collect();
    assert_eq!(maps.len(), plan.keep.len());
    assert!(maps.iter().any(|m| *m == "0:0"), "video mapped: {maps:?}");
}
