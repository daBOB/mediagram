use super::*;
use crate::media::streams::Stream;
use crate::test_fakes::upload::sample_caption;
use mlib_spec::caption::{Episode, Kind};
use std::path::PathBuf;

const GIB: u64 = 1 << 30;

fn stream(kind: StreamKind, codec: &str, language: Option<&str>) -> Stream {
    Stream {
        index: 0,
        kind,
        language: language.map(str::to_string),
        bit_rate: None,
        codec: Some(codec.to_string()),
        title: None,
        default: false,
        forced: false,
        hearing_impaired: false,
    }
}

fn probed(streams: Vec<Stream>) -> Probed {
    Probed {
        streams,
        duration: 3600.0,
        size: None,
    }
}

/// A set `set_id` of `kind` titled `title`, subtitled in `slang`.
fn set(set_id: &str, kind: Kind, title: &str, slang: &[&str]) -> SetRow {
    let mut row = SetRow::from_caption(&sample_caption("setA", 10, 1), 0);
    row.set_id = set_id.to_string();
    row.kind = kind;
    row.title = Some(title.to_string());
    row.slang = slang.iter().map(|l| l.to_string()).collect();
    row
}

fn episode(set_id: &str, show: &str, season: u32, episode: Episode) -> SetRow {
    let mut row = set(set_id, Kind::Ep, "Pilot", &["en"]);
    row.container = "mp4".to_string();
    row.show = Some(show.to_string());
    row.season = Some(season);
    row.episode = Some(episode);
    row
}

fn file(name: &str, size: u64) -> SourceFile {
    SourceFile {
        path: PathBuf::from("/m").join(name),
        size,
        is_mp4: name.ends_with(".mp4"),
        duration: None,
        guess: None,
    }
}

fn verdict(name: &str, verdict: Verdict) -> Match {
    Match {
        path: PathBuf::from("/m").join(name),
        verdict,
    }
}

/// Picture subtitles count whatever their language, since none can become
/// text; text counts only in German or English, however it is tagged.
#[test]
fn subtitle_tracks_are_counted_as_de_en_text_or_picture_only() {
    let file = probed(vec![
        stream(StreamKind::Video, "h264", Some("deu")),
        stream(StreamKind::Audio, "aac", Some("ger")),
        stream(StreamKind::Subtitle, "subrip", Some("ger")),
        stream(StreamKind::Subtitle, "ass", Some("en")),
        stream(StreamKind::Subtitle, "subrip", Some("fra")),
        stream(StreamKind::Subtitle, "subrip", Some("und")),
        stream(StreamKind::Subtitle, "hdmv_pgs_subtitle", Some("deu")),
        stream(StreamKind::Subtitle, "dvd_subtitle", None),
    ]);

    assert_eq!(count_subtitle_tracks(&file), (2, 2));
}

#[test]
fn a_numbered_set_is_titled_by_show_and_position_code_anything_else_by_its_title() {
    let single = episode("e1", "Dark", 1, Episode::Single(3));
    assert_eq!(display_title(&single), "Dark S01E03");
    let double = episode("e2", "Dark", 2, Episode::Range([4, 5]));
    assert_eq!(display_title(&double), "Dark S02E04-E05");
    let mut lesson = episode("t", "Rust Course", 1, Episode::Single(2));
    lesson.kind = Kind::Tut;
    assert_eq!(display_title(&lesson), "Rust Course C01L02");

    assert_eq!(display_title(&set("m", Kind::Movie, "Heat", &[])), "Heat");
    let mut untitled = set("s", Kind::Ep, "x", &[]);
    untitled.title = None;
    untitled.show = Some("Dark".to_string());
    assert_eq!(display_title(&untitled), "Dark");
    untitled.show = None;
    assert_eq!(display_title(&untitled), "");
}

/// A row per file, totals per kind/container and per verdict, then the
/// de/en-subtitled sets no file was matched to — a set two files fought
/// over among them, since neither file is used.
#[test]
fn the_report_lists_each_file_then_totals_then_unmatched_sets() {
    let sets = [
        set("A", Kind::Movie, "Heat", &["de"]),
        episode("B", "Dark", 1, Episode::Single(2)),
        set("C", Kind::Movie, "Ronin", &["de", "fr"]),
        set("D", Kind::Movie, "Amélie", &["fr"]),
        set("E", Kind::Movie, "Alien", &["en"]),
    ];
    let files = [
        file("f1.mkv", 2 * GIB),
        file("f2.mp4", GIB),
        file("f3.mkv", GIB / 2),
        file("f4.mkv", GIB),
        file("f5.mkv", GIB),
        file("f6.mkv", 0),
    ];
    let german_text = probed(vec![stream(StreamKind::Subtitle, "subrip", Some("deu"))]);
    let probes = [
        Some(german_text.clone()),
        None,
        Some(german_text.clone()),
        None,
        None,
        None,
    ];
    let matches = [
        verdict("f1.mkv", Verdict::Matched("A".to_string())),
        verdict("f2.mp4", Verdict::Fallback("B".to_string())),
        verdict("f3.mkv", Verdict::Ambiguous),
        verdict("f4.mkv", Verdict::Conflict("E".to_string())),
        verdict("f5.mkv", Verdict::Conflict("E".to_string())),
        verdict("f6.mkv", Verdict::Unmatched),
    ];

    let lines = report_lines(&files, &probes, &matches, &sets);

    assert_eq!(
        lines,
        [
            "file\tverdict\tset_id\tkind\ttitle\tde_en_text\tpicture_only",
            "/m/f1.mkv\tmatched\tA\tmovie\tHeat\t1\t0",
            "/m/f2.mp4\tfallback\tB\tep\tDark S01E02\t\t",
            "/m/f3.mkv\tambiguous\t\t\t\t\t",
            "/m/f4.mkv\tconflict\tE\tmovie\tAlien\t\t",
            "/m/f5.mkv\tconflict\tE\tmovie\tAlien\t\t",
            "/m/f6.mkv\tunmatched\t\t\t\t\t",
            "#\n# matched sets by kind/container (count, with de/en text, bytes):",
            "#   movie/mkv: 1 sets, 1 with de/en text, 2.00 GB",
            "# fallback candidates by kind/container (listed only, never sent without --accept-fallback):",
            "#   ep/mp4: 1 candidates, 0 with de/en text, 1.00 GB",
            "# ambiguous: 1 files, 0.50 GB",
            "# conflicts: 2 files across 1 sets, 2.00 GB",
            "# unmatched: 1 files, 0.00 GB",
            "#\n# de/en-subtitled sets no file matched:",
            "#   C\tmovie\tRonin",
            "#   E\tmovie\tAlien",
        ]
    );
}
