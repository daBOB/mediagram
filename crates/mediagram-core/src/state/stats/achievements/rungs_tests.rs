use super::*;

const HOUR: f64 = 3600.0;

/// UTC noon of a day: the readers here sit at no offset.
fn noon(day: i64) -> i64 {
    day * DAY_MS + DAY_MS / 2
}

fn title(set_id: &str, kind: &str, genres: &[&str]) -> LibraryTitle {
    LibraryTitle {
        set_id: set_id.into(),
        kind: kind.into(),
        genres: genres.iter().map(|g| g.to_string()).collect(),
        collection: None,
    }
}

fn collection(id: &str, set_ids: &[&str]) -> LibraryCollection {
    LibraryCollection {
        id: id.into(),
        set_ids: set_ids.iter().map(|s| s.to_string()).collect(),
    }
}

fn days(seconds: &[(i64, f64)]) -> Vec<DayTotal> {
    seconds
        .iter()
        .map(|&(day, seconds)| DayTotal { day, seconds })
        .collect()
}

/// Each rung as `(id, earned_at, have, need)`.
fn read(rungs: &[Rung]) -> Vec<(&str, Option<i64>, u32, u32)> {
    rungs
        .iter()
        .map(|r| (r.id.as_str(), r.earned_at, r.have, r.need))
        .collect()
}

#[test]
fn rung_n_of_a_counting_ladder_is_earned_by_the_nth_time() {
    assert_eq!(
        read(&counted("films", &[1, 2, 5], &[100, 200, 300])),
        [
            ("films-1", Some(100), 3, 1),
            ("films-2", Some(200), 3, 2),
            ("films-5", None, 3, 5),
        ]
    );
    assert_eq!(
        read(&counted("docs", &[10], &[])),
        [("docs-10", None, 0, 10)]
    );
}

/// One finish bringing two new genres earns two arrivals at once; a genre
/// seen before, or a title with none, brings nothing.
#[test]
fn a_genre_arrives_with_the_first_finish_that_brings_it() {
    let (both, again, other, none) = (
        title("a", "movie", &["Drama", "Action"]),
        title("b", "movie", &["Drama"]),
        title("c", "movie", &["Comedy"]),
        title("d", "movie", &[]),
    );
    let finishes = [
        Finish {
            title: &both,
            at: 10,
        },
        Finish {
            title: &again,
            at: 20,
        },
        Finish {
            title: &other,
            at: 30,
        },
        Finish {
            title: &none,
            at: 40,
        },
    ];
    assert_eq!(genre_arrivals(&finishes), [10, 10, 30]);
}

/// Cumulative across days, earned on the day the total reaches the rung —
/// exactly reaching it counts — with whole hours as the progress.
#[test]
fn an_hours_rung_is_earned_on_the_day_the_running_total_reaches_it() {
    let watched = days(&[(0, 3.0 * HOUR), (1, 6.0 * HOUR), (2, 1.0 * HOUR)]);
    assert_eq!(
        read(&hours(&[5, 10, 100], &watched, noon)),
        [
            ("hours-5", Some(noon(1)), 10, 5),
            ("hours-10", Some(noon(2)), 10, 10),
            ("hours-100", None, 10, 100),
        ]
    );
    let partial = days(&[(0, 2.5 * HOUR)]);
    assert_eq!(read(&hours(&[10], &partial, noon))[0].2, 2);
}

/// A day with no watching breaks a run as surely as a missing day; the
/// progress is the longest run there has been.
#[test]
fn a_streak_rung_is_earned_on_the_day_completing_the_first_run_that_long() {
    let watched = days(&[
        (0, 60.0),
        (1, 60.0),
        (2, 60.0),
        (3, 0.0),
        (4, 60.0),
        (5, 60.0),
        (6, 60.0),
        (7, 60.0),
        (9, 60.0),
    ]);
    assert_eq!(
        read(&streak(&[3, 4, 7], &watched, noon)),
        [
            ("streak-3", Some(noon(2)), 4, 3),
            ("streak-4", Some(noon(7)), 4, 4),
            ("streak-7", None, 4, 7),
        ]
    );
}

/// Episodes only, counted per local day at the reader's offset, earned on
/// the first day to reach the need even when a later day goes further.
#[test]
fn a_binge_counts_episodes_finished_on_one_local_day() {
    let (ep, film) = (title("e", "ep", &[]), title("m", "movie", &[]));
    let at = |day: i64, hour: i64| day * DAY_MS + hour * 3_600_000;
    let mut finishes: Vec<Finish<'_>> = (1..=4)
        .map(|h| Finish {
            title: &ep,
            at: at(10, h),
        })
        .collect();
    finishes.push(Finish {
        title: &ep,
        at: at(10, 23),
    });
    finishes.extend((0..6).map(|h| Finish {
        title: &ep,
        at: at(12, h),
    }));
    finishes.extend((0..9).map(|h| Finish {
        title: &film,
        at: at(14, h),
    }));

    let utc = binge(5, &finishes, 0, noon);
    assert_eq!(
        (utc.id.as_str(), utc.earned_at, utc.have, utc.need),
        ("binge-5", Some(noon(10)), 6, 5)
    );

    // Two hours east, the 23:00 finish is the next local day's, so day 10
    // holds four and the first day to reach five is day 12.
    let east = binge(5, &finishes, 7_200_000, noon);
    assert_eq!((east.earned_at, east.have), (Some(noon(12)), 6));
}

#[test]
fn a_library_with_no_collections_has_no_whole_show_rung() {
    assert!(whole_show(&[], &HashMap::new()).is_empty());
    let empty = [collection("nothing", &[])];
    assert!(whole_show(&empty, &HashMap::new()).is_empty());
}

/// Earned when the first collection was completed — the earliest of the
/// completed ones' last finishes — however the library orders them.
#[test]
fn the_whole_show_rung_is_earned_by_the_first_collection_completed() {
    let collections = [
        collection("a", &["a1", "a2"]),
        collection("b", &["b1", "b2", "b3"]),
    ];
    let finished_at = HashMap::from([("a1", 50), ("a2", 70), ("b1", 10), ("b2", 30), ("b3", 20)]);
    assert_eq!(
        read(&whole_show(&collections, &finished_at)),
        [("whole-show", Some(30), 3, 3)]
    );
}

/// Until then, the progress of the collection closest to done, by share;
/// an equal share goes to the one with more finished.
#[test]
fn until_earned_the_rung_shows_the_collection_closest_to_done() {
    let collections = [
        collection("half", &["h1", "h2"]),
        collection("two-thirds", &["t1", "t2", "t3"]),
        collection("half-again", &["g1", "g2", "g3", "g4"]),
    ];
    let finished_at = HashMap::from([("h1", 1), ("t1", 2), ("t2", 3), ("g1", 4), ("g2", 5)]);
    assert_eq!(
        read(&whole_show(&collections, &finished_at)),
        [("whole-show", None, 2, 3)]
    );
    let finished_at = HashMap::from([("h1", 1), ("g1", 4), ("g2", 5)]);
    assert_eq!(
        read(&whole_show(&collections, &finished_at)),
        [("whole-show", None, 2, 4)]
    );
}
