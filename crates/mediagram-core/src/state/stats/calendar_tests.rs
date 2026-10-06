use super::*;

#[test]
fn day_numbers_count_from_1970_and_name_back() {
    assert_eq!(day_number("1970-01-01"), Some(0));
    assert_eq!(day_number("2000-03-01"), Some(11_017));
    assert_eq!(day_number("2024-02-29"), Some(19_782));
    assert_eq!(day_number("2026-10-03"), Some(20_729));
    for number in -1_000..30_000 {
        assert_eq!(day_number(&day_name(number)), Some(number));
    }
    for bad in [
        "",
        "2026-13-01",
        "2026-00-10",
        "2026-10-32",
        "2026-1-03",
        "2026/10/03",
    ] {
        assert_eq!(day_number(bad), None, "{bad:?}");
    }
}

#[test]
fn an_iso_week_runs_monday_to_sunday() {
    for day in ["2026-09-28", "2026-10-03", "2026-10-04"] {
        assert_eq!(
            day_name(monday_of(day_number(day).unwrap())),
            "2026-09-28",
            "{day}"
        );
    }
}

#[test]
fn a_leap_day_exists_only_in_a_leap_year() {
    let next = |day: &str| day_name(day_number(day).unwrap() + 1);
    assert_eq!(next("2024-02-28"), "2024-02-29");
    assert_eq!(next("2023-02-28"), "2023-03-01");
    assert_eq!(
        next("2000-02-28"),
        "2000-02-29",
        "every 400 years is a leap year"
    );
    assert_eq!(next("1900-02-28"), "1900-03-01", "a century is not");
    assert_eq!(next("2026-12-31"), "2027-01-01");
}

#[test]
fn days_before_1970_count_back_from_it() {
    assert_eq!(day_number("1969-12-31"), Some(-1));
    assert_eq!(day_name(-1), "1969-12-31");
    assert_eq!(day_name(-719_468), "0000-03-01");
}

/// 1970-01-01 was a Thursday, so the week it fell in began three days
/// before it.
#[test]
fn a_monday_is_its_own_weeks_start_before_1970_too() {
    assert_eq!(monday_of(0), -3);
    assert_eq!(day_name(-3), "1969-12-29");
    for monday in [-3, 4, day_number("2026-09-28").unwrap()] {
        assert_eq!(monday_of(monday), monday);
        assert_eq!(monday_of(monday + 6), monday, "the Sunday after {monday}");
        assert_eq!(
            monday_of(monday - 1),
            monday - 7,
            "the Sunday before {monday}"
        );
    }
}
