use super::*;
use crate::media::prepare::plan::PreparePlan;
use crate::media::streams::{Stream, StreamKind};
use std::path::PathBuf;

fn stream(kind: StreamKind, codec: &str) -> Stream {
    Stream {
        index: 0,
        kind,
        language: None,
        bit_rate: None,
        codec: Some(codec.to_string()),
        title: None,
        default: false,
        forced: false,
        hearing_impaired: false,
    }
}

/// A file of `size` bytes `prepare` would bring to `estimated` bytes.
fn candidate(name: &str, size: u64, estimated: u64, verdict: Verdict) -> Candidate {
    Candidate {
        file: PathBuf::from("/media").join(name),
        size,
        duration: 3600.0,
        plan: PreparePlan {
            keep: vec![
                stream(StreamKind::Video, "h264"),
                stream(StreamKind::Audio, "eac3"),
            ],
            dropped_audio: 1,
            dropped_subtitles: 2,
            estimated_bytes: estimated,
            verdict,
        },
    }
}

/// With `video` as the picture's codec.
fn with_video(mut c: Candidate, video: &str) -> Candidate {
    c.plan.keep[0] = stream(StreamKind::Video, video);
    c
}

#[test]
fn each_file_gets_a_row_worded_by_its_verdict() {
    let planned = [
        candidate(
            "fits.mkv",
            1_000_000_000,
            1_000_000_000,
            Verdict::AlreadyFits,
        ),
        candidate(
            "keeps.mkv",
            3_000_000_000,
            3_000_000_000,
            Verdict::NothingToDrop,
        ),
        candidate("drops.mkv", 3_000_000_000, 1_500_000_000, Verdict::Prepare),
        candidate(
            "big.mkv",
            5_000_000_000,
            4_500_000_000,
            Verdict::PrepareStillOversized,
        ),
    ];

    let lines = table(&planned, 2_000_000_000);

    assert_eq!(lines.len(), 6);
    assert!(lines[0].starts_with("file") && lines[0].ends_with("verdict"));
    assert!(lines[1].starts_with("fits.mkv") && lines[1].ends_with("  already fits"));
    assert!(lines[2].ends_with("  nothing to drop"));
    assert!(lines[3].ends_with("  prepare"));
    assert!(lines[4].ends_with("  prepare, still needs 2 parts"));
    assert!(
        lines[3].contains("    3.00G      1     2       1.50G"),
        "{}",
        lines[3]
    );
}

/// Only a file `prepare` will rewrite counts at its estimate: one that
/// already fits is left alone, so it counts at its full size even when it
/// carries tracks the plan would drop.
#[test]
fn the_saving_counts_only_files_that_will_be_rewritten() {
    let planned = [
        candidate("fits.mkv", 1_000_000_000, 500_000_000, Verdict::AlreadyFits),
        candidate(
            "keeps.mkv",
            3_000_000_000,
            2_000_000_000,
            Verdict::NothingToDrop,
        ),
        candidate("drops.mkv", 3_000_000_000, 1_000_000_000, Verdict::Prepare),
        candidate(
            "big.mkv",
            5_000_000_000,
            4_000_000_000,
            Verdict::PrepareStillOversized,
        ),
    ];

    let lines = table(&planned, 2_000_000_000);

    assert_eq!(
        lines.last().unwrap(),
        "\n12.0 GB -> 9.0 GB, saving 3.0 GB. One part is 2.00 GB."
    );
}

/// The table's file column is 44 characters wide, counted in characters
/// rather than bytes so an accented name is not cut mid-letter.
#[test]
fn a_long_name_is_cut_to_the_column_with_an_ellipsis() {
    assert_eq!(truncate("short.mkv", 44), "short.mkv");
    assert_eq!(truncate(&"x".repeat(44), 44), "x".repeat(44));
    let cut = truncate(&"é".repeat(50), 44);
    assert_eq!(cut.chars().count(), 44);
    assert!(cut.ends_with("é…"));

    let long = format!("{}.mkv", "épisode".repeat(8));
    let lines = table(&[candidate(&long, 1, 1, Verdict::AlreadyFits)], 2);
    assert!(lines[1].starts_with(&truncate(&long, 44)));
}

/// A wrapper or an audio track `--mp4` fixes is no reason to warn; a
/// picture it cannot is, named once however many files carry it.
#[test]
fn only_a_picture_the_conversion_cannot_fix_is_warned_about() {
    let fixable = [candidate("a.mkv", 1, 1, Verdict::Prepare)];
    assert_eq!(codec_warning(&fixable), None);

    let planned = [
        with_video(candidate("a.mkv", 1, 1, Verdict::Prepare), "hevc"),
        with_video(candidate("b.mkv", 1, 1, Verdict::Prepare), "hevc"),
        with_video(candidate("c.mkv", 1, 1, Verdict::Prepare), "mpeg2video"),
        candidate("d.mkv", 1, 1, Verdict::Prepare),
    ];
    let warning = codec_warning(&planned).expect("a warning");

    assert!(
        warning.starts_with(
            "\nwarning: 3 file(s) carry hevc video / mpeg2video video, which a browser will not open."
        ),
        "{warning}"
    );
}
