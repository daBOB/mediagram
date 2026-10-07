use std::collections::HashMap;

use super::reconcile;
use crate::state::record::{UnwatchedRow, WatchedRow};

fn live(set_id: &str, updated_at: f64) -> (String, WatchedRow) {
    let row = WatchedRow {
        set_id: set_id.into(),
        updated_at,
    };
    (set_id.into(), row)
}

fn removal(set_id: &str, updated_at: f64, last_finished_at: f64) -> (String, UnwatchedRow) {
    let row = UnwatchedRow {
        set_id: set_id.into(),
        updated_at,
        last_finished_at,
    };
    (set_id.into(), row)
}

fn ids<T>(rows: &[T], set_id: impl Fn(&T) -> &str) -> Vec<&str> {
    rows.iter().map(set_id).collect()
}

#[test]
fn a_mark_made_since_the_removal_is_live_again() {
    let out = reconcile(
        HashMap::from([live("01A", 30.0)]),
        HashMap::from([removal("01A", 20.0, 10.0)]),
    );
    assert_eq!(ids(&out.watched, |r| &r.set_id), ["01A"]);
    assert!(out.unwatched.is_empty());
    assert_eq!(out.finished_at["01A"], 30.0);
}

/// A removal supersedes positions only up to the completion it took the
/// mark from, so a rewatch begun after that finish survives.
#[test]
fn a_newer_removal_wins_and_dates_the_finish_it_undid() {
    let out = reconcile(
        HashMap::from([live("01A", 10.0)]),
        HashMap::from([removal("01A", 20.0, 10.0)]),
    );
    assert!(out.watched.is_empty());
    assert_eq!(ids(&out.unwatched, |r| &r.set_id), ["01A"]);
    assert_eq!(out.finished_at["01A"], 10.0);
}

/// Not a device-id tie-break: a live mark stamped the very moment of the
/// removal is no news of a later finish, so the removal stands on every
/// device whichever id sorts higher.
#[test]
fn a_removal_at_the_same_moment_as_the_mark_wins() {
    let out = reconcile(
        HashMap::from([live("01A", 20.0)]),
        HashMap::from([removal("01A", 20.0, 5.0)]),
    );
    assert!(out.watched.is_empty());
    assert_eq!(out.unwatched.len(), 1);
    assert_eq!(out.finished_at["01A"], 5.0);
}

#[test]
fn a_title_known_on_one_side_only_passes_through() {
    let out = reconcile(
        HashMap::from([live("01A", 7.0)]),
        HashMap::from([removal("01B", 9.0, 3.0)]),
    );
    assert_eq!(ids(&out.watched, |r| &r.set_id), ["01A"]);
    assert_eq!(ids(&out.unwatched, |r| &r.set_id), ["01B"]);
    assert_eq!(
        out.finished_at,
        HashMap::from([("01A".to_string(), 7.0), ("01B".to_string(), 3.0)])
    );
}

/// Titles come out in id order, whatever order the maps iterate in, so
/// two devices write the same document for the same state.
#[test]
fn every_title_answers_once_in_id_order() {
    let out = reconcile(
        HashMap::from([live("03C", 1.0), live("01A", 1.0), live("02B", 9.0)]),
        HashMap::from([removal("02B", 5.0, 1.0), removal("04D", 5.0, 1.0)]),
    );
    assert_eq!(ids(&out.watched, |r| &r.set_id), ["01A", "02B", "03C"]);
    assert_eq!(ids(&out.unwatched, |r| &r.set_id), ["04D"]);
    assert_eq!(out.finished_at.len(), 4);
}

#[test]
fn nothing_in_is_nothing_out() {
    let out = reconcile(HashMap::new(), HashMap::new());
    assert!(out.watched.is_empty() && out.unwatched.is_empty() && out.finished_at.is_empty());
}
