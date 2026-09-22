//! What `mediagram status` reports, and how it reads.
//!
//! The report is inferred from the index — nothing records that a bulk add is
//! running — so the queries are the whole of the answer and each one is worth
//! pinning.

use mediagram::commands::status::{ago, count, episodes_of, heading, label, progress_of};
use mediagram::index::progress::{ShowProgress, Unfinished, library, shows, unfinished};
use rusqlite::{Connection, params};

fn db() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for statement in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute_batch(statement).unwrap();
    }
    conn
}

/// A set with `parts` parts, of which the first `done` reached the channel.
fn set(conn: &Connection, id: &str, status: &str, parts: u32, done: u32, each: i64) {
    conn.execute(
        "INSERT INTO sets(set_id, kind, tmdb, show, title, season, episode, container,
                          total, part_count, status, created_at, spec_version)
         VALUES (?1, 'ep', 252107, 'Star City', 'Plow Deep', 1, '7', 'mp4', ?2, ?3, ?4, 1000, 4)",
        params![id, each * parts as i64, parts, status],
    )
    .unwrap();
    for idx in 0..parts {
        conn.execute(
            "INSERT INTO parts(set_id, idx, byte_offset, byte_length, status)
             VALUES (?1, ?2, 0, ?3, ?4)",
            params![id, idx, each, if idx < done { "done" } else { "pending" }],
        )
        .unwrap();
    }
}

#[test]
fn an_upload_in_flight_reports_how_far_it_has_got() {
    let conn = db();
    set(&conn, "A", "pending", 2, 1, 3_000_000_000);

    let found = unfinished(&conn).unwrap();

    assert_eq!(found.len(), 1);
    assert_eq!((found[0].parts_done, found[0].parts_total), (1, 2));
    assert_eq!(found[0].bytes_done, 3_000_000_000);
    assert_eq!(found[0].bytes_total, 6_000_000_000);
    assert!(found[0].started());
}

/// A set nothing has been sent for is waiting, not in flight — `resume`
/// treats the two alike but a reader should not have to guess which it is.
#[test]
fn a_set_with_no_parts_sent_is_not_reported_as_started() {
    let conn = db();
    set(&conn, "A", "pending", 2, 0, 1_000_000_000);

    assert!(!unfinished(&conn).unwrap()[0].started());
}

#[test]
fn a_finished_set_is_not_unfinished() {
    let conn = db();
    set(&conn, "A", "complete", 2, 2, 1_000_000_000);

    assert!(unfinished(&conn).unwrap().is_empty());
}

#[test]
fn the_library_counts_only_what_is_wholly_in_the_channel() {
    let conn = db();
    set(&conn, "A", "complete", 1, 1, 2_000_000_000);
    set(&conn, "B", "pending", 1, 0, 9_000_000_000);

    assert_eq!(library(&conn).unwrap(), (1, 2_000_000_000));
}

#[test]
fn a_show_is_counted_against_what_the_provider_says_exists() {
    let conn = db();
    set(&conn, "A", "complete", 1, 1, 1);
    conn.execute(
        "INSERT INTO shows(source, kind, id, lang, total_episodes) VALUES ('tmdb','tv',252107,'de-DE',8)",
        [],
    )
    .unwrap();

    assert_eq!(
        shows(&conn).unwrap(),
        vec![ShowProgress {
            show: "Star City".into(),
            held: 1,
            total: Some(8)
        }]
    );
}

/// A library that has never run `metadata` still gets a count, just without
/// a denominator — no answer at all would be worse.
#[test]
fn a_show_nobody_described_is_counted_without_a_total() {
    let conn = db();
    set(&conn, "A", "complete", 1, 1, 1);

    assert_eq!(shows(&conn).unwrap()[0].total, None);
    assert_eq!(episodes_of(&shows(&conn).unwrap()[0]), "1 episode");
}

#[test]
fn an_incomplete_show_says_so_and_a_complete_one_does_not() {
    let partial = ShowProgress {
        show: "S".into(),
        held: 6,
        total: Some(8),
    };
    let whole = ShowProgress {
        show: "S".into(),
        held: 8,
        total: Some(8),
    };
    // Specials outnumber a provider's count often enough that holding more
    // must not read as a shortfall.
    let extra = ShowProgress {
        show: "S".into(),
        held: 9,
        total: Some(8),
    };

    assert_eq!(episodes_of(&partial), "6 of 8 episodes");
    assert_eq!(episodes_of(&whole), "8 episodes");
    assert_eq!(episodes_of(&extra), "9 episodes");
    let one = ShowProgress {
        show: "S".into(),
        held: 1,
        total: Some(8),
    };
    assert_eq!(episodes_of(&one), "1 of 8 episodes");
}

fn waiting(show: Option<&str>, title: Option<&str>) -> Unfinished {
    Unfinished {
        set_id: "01ABC".into(),
        kind: "ep".into(),
        show: show.map(str::to_string),
        title: title.map(str::to_string),
        season: Some(1),
        episode: Some("7".into()),
        parts_done: 1,
        parts_total: 2,
        bytes_done: 3_500_000_000,
        bytes_total: 6_280_000_000,
        created_at: 1000,
    }
}

#[test]
fn a_set_is_named_the_way_the_shelf_names_it() {
    assert_eq!(
        label(&waiting(Some("Star City"), Some("Plow Deep"))),
        "Star City  S01E07  Plow Deep"
    );
}

/// A set resolved badly has no show and no title, and the id is the only
/// thing left to call it — better than an empty line.
#[test]
fn a_set_with_no_name_falls_back_to_its_id() {
    let mut bare = waiting(None, None);
    bare.season = None;
    bare.episode = None;

    assert_eq!(label(&bare), "01ABC");
}

/// A file holding two episodes, and a lesson, are named by the codes the
/// rest of the library uses rather than by the index's raw JSON.
#[test]
fn a_double_episode_and_a_lesson_read_the_way_the_shelf_names_them() {
    let mut double = waiting(Some("Star City"), None);
    double.episode = Some("[1,2]".into());
    assert_eq!(label(&double), "Star City  S01E01-E02");

    let mut lesson = waiting(Some("Rust"), Some("Ownership"));
    lesson.kind = "tut".into();
    lesson.season = Some(2);
    lesson.episode = Some("3".into());
    assert_eq!(label(&lesson), "Rust  C02L03  Ownership");
}

#[test]
fn progress_states_parts_bytes_and_age() {
    let mut half = waiting(Some("S"), Some("T"));
    half.parts_done = 1;

    let line = progress_of(&half, 1000 + 1380);

    assert_eq!(line, "1 of 2 parts sent · 3.50 of 6.28 GB · added 23 min ago");
}

#[test]
fn an_age_is_given_in_the_largest_unit_that_still_says_something() {
    assert_eq!(ago(5), "5 sec ago");
    assert_eq!(ago(89), "89 sec ago");
    assert_eq!(ago(90), "2 min ago");
    assert_eq!(ago(1380), "23 min ago");
    assert_eq!(ago(5400), "2 hours ago");
    assert_eq!(ago(172_800), "2 days ago");
    // A clock that moved backwards must not print a negative age.
    assert_eq!(ago(-10), "just now");
}

/// The report is read at a glance, so a number and its noun have to agree —
/// "1 set(s)" made a reader stop and ask what was meant.
#[test]
fn a_number_and_its_noun_agree() {
    assert_eq!(count(1, "set"), "1 set");
    assert_eq!(count(2, "set"), "2 sets");
    assert_eq!(count(0, "set"), "0 sets");
    assert_eq!(count(1, "part"), "1 part");
    assert_eq!(count(177, "set"), "177 sets");
}

/// A set nobody has picked up must not claim a part is in flight: three
/// queued files each saying "part 1 of N" read as three uploads at once,
/// which is the one thing the upload lock exists to prevent.
#[test]
fn a_set_with_nothing_sent_claims_no_part_in_flight() {
    let mut fresh = waiting(Some("S"), Some("T"));
    fresh.parts_done = 0;
    fresh.bytes_done = 0;

    assert_eq!(
        progress_of(&fresh, 1000),
        "nothing sent yet · 6.28 GB in 2 parts · added 0 sec ago"
    );
}

/// A single-part set says "1 part", not "1 parts".
#[test]
fn a_one_part_set_says_part_once() {
    let mut single = waiting(Some("S"), Some("T"));
    single.parts_done = 0;
    single.bytes_done = 0;
    single.parts_total = 1;

    assert!(progress_of(&single, 1000).contains("6.28 GB in 1 part ·"));
}

/// Only the set the uploader is actually sending is uploading. The others
/// are queued behind it while it runs, and nothing at all is happening to
/// them once it stops.
#[test]
fn only_the_set_on_the_wire_is_called_uploading() {
    assert_eq!(heading(true, true), "uploading");
    assert_eq!(heading(false, true), "waiting");
    assert_eq!(heading(false, false), "unfinished");
}
