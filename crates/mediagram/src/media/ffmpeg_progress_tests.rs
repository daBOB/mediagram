use super::*;

fn job(index: usize, total: usize, duration: f64) -> Job {
    Job {
        index,
        total,
        name: "S01E03.mkv".to_string(),
        duration,
    }
}

fn at(minutes: f64, bytes: u64) -> Position {
    Position {
        out_time_us: (minutes * 60.0 * 1e6) as u64,
        total_size: bytes,
    }
}

#[test]
fn a_block_is_drawn_only_once_it_is_complete() {
    let mut position = Position::default();
    assert!(!position.feed("frame=125"));
    assert!(!position.feed("out_time_us=4000000"));
    assert!(!position.feed("total_size=1200000000"));
    assert!(position.feed("progress=continue"));
    assert_eq!(position.out_time_us, 4_000_000);
    assert_eq!(position.total_size, 1_200_000_000);
}

#[test]
fn an_unknown_field_leaves_the_last_known_position_standing() {
    let mut position = Position::default();
    position.feed("out_time_us=4000000");
    position.feed("out_time_us=N/A");
    assert_eq!(position.out_time_us, 4_000_000);
}

#[test]
fn one_file_of_a_folder_says_which_one_and_how_far_it_has_to_go() {
    // Four minutes of a 45 minute episode, done in 12 seconds: 20x, so
    // the 41 minutes still to copy take another two.
    let line = render(&job(2, 10, 2700.0), &at(4.0, 1_200_000_000), Duration::from_secs(12));
    assert_eq!(
        line,
        "  [3/10] S01E03.mkv · 4.0 of 45.0 min (9%) · 1.20 GB · 20x · eta 2m03s"
    );
}

#[test]
fn a_single_file_is_not_counted_against_itself() {
    let line = render(&job(0, 1, 2700.0), &at(4.0, 1_200_000_000), Duration::from_secs(12));
    assert!(line.starts_with("  S01E03.mkv · 4.0 of 45.0 min"));
}

#[test]
fn a_source_of_unknown_length_reports_a_position_and_promises_nothing() {
    let line = render(&job(0, 1, 0.0), &at(4.0, 1_200_000_000), Duration::from_secs(12));
    assert_eq!(line, "  S01E03.mkv · 4.0 min · 1.20 GB · 20x");
}

#[test]
fn nothing_written_yet_is_left_off_rather_than_shown_as_zero() {
    let line = render(&job(0, 1, 2700.0), &at(0.0, 0), Duration::from_millis(300));
    assert_eq!(line, "  S01E03.mkv · 0.0 of 45.0 min (0%)");
}
