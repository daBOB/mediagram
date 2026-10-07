use std::collections::HashMap;

use super::{Held, keep, keep_pin, keep_ranked};
use crate::state::merge::roles::kids_mark_rank;
use crate::state::record::{ListRow, PinRecord, WatchedRow};

fn watched(updated_at: f64) -> WatchedRow {
    WatchedRow {
        set_id: "01A".into(),
        updated_at,
    }
}

fn mark(updated_at: f64, age: Option<u8>) -> ListRow {
    ListRow {
        set_id: "01A".into(),
        updated_at,
        removed: false,
        age,
    }
}

fn pin(hash: &str, updated_at: f64, proven: bool) -> PinRecord {
    PinRecord {
        hash: hash.into(),
        salt: "salt".into(),
        updated_at,
        proven,
    }
}

/// Every row offered under one key, in `order`; answers the winning device.
fn winner<T>(
    offers: &[(&str, T)],
    order: &[usize],
    take: impl Fn(&mut HashMap<String, Held<T>>, T, &str),
) -> String
where
    T: Clone,
{
    let mut into = HashMap::new();
    for &i in order {
        let (device, row) = &offers[i];
        take(&mut into, row.clone(), device);
    }
    into.remove("k").unwrap().device
}

fn kept(into: &mut HashMap<String, Held<WatchedRow>>, row: WatchedRow, device: &str) {
    keep(into, "k".into(), row, device);
}

fn ranked(into: &mut HashMap<String, Held<ListRow>>, row: ListRow, device: &str) {
    keep_ranked(into, "k".into(), row, device, kids_mark_rank);
}

fn pinned(into: &mut HashMap<String, Held<PinRecord>>, row: PinRecord, device: &str) {
    keep_pin(into, "k".into(), row, device);
}

#[test]
fn the_newer_row_wins_whichever_arrives_first() {
    let offers = [("a", watched(20.0)), ("b", watched(10.0))];
    for order in [[0, 1], [1, 0]] {
        assert_eq!(winner(&offers, &order, kept), "a", "order {order:?}");
    }
}

/// Two machines merging the same pair must reach the same answer, or they
/// push their disagreement back and forth for ever.
#[test]
fn a_tie_goes_to_the_greater_device_id_in_either_order() {
    let offers = [("laptop", watched(10.0)), ("phone", watched(10.0))];
    for order in [[0, 1], [1, 0]] {
        assert_eq!(winner(&offers, &order, kept), "phone", "order {order:?}");
    }
}

#[test]
fn rows_under_different_keys_never_compete() {
    let mut into = HashMap::new();
    keep(&mut into, "01A".into(), watched(20.0), "a");
    keep(&mut into, "01B".into(), watched(10.0), "a");
    assert_eq!(into.len(), 2);
    assert_eq!(into["01B"].row.updated_at, 10.0);
}

/// A Kids mark "from 6" outranks a "from 12" echo of the same moment even
/// when the echo's device id sorts higher.
#[test]
fn at_an_equal_time_the_higher_rank_wins_before_the_device_id() {
    let offers = [("a", mark(10.0, Some(6))), ("z", mark(10.0, None))];
    for order in [[0, 1], [1, 0]] {
        assert_eq!(winner(&offers, &order, ranked), "a", "order {order:?}");
    }
}

#[test]
fn rank_never_outweighs_a_newer_time() {
    let offers = [("a", mark(10.0, Some(6))), ("b", mark(11.0, None))];
    for order in [[0, 1], [1, 0]] {
        assert_eq!(winner(&offers, &order, ranked), "b", "order {order:?}");
    }
}

#[test]
fn equal_rank_and_time_fall_back_to_the_device_id() {
    let offers = [("a", mark(10.0, Some(6))), ("b", mark(10.0, Some(6)))];
    for order in [[0, 1], [1, 0]] {
        assert_eq!(winner(&offers, &order, ranked), "b", "order {order:?}");
    }
}

#[test]
fn a_proven_pin_beats_any_first_pin_however_new() {
    let offers = [
        ("a", pin("proven", 10.0, true)),
        ("b", pin("first", 99.0, false)),
    ];
    for order in [[0, 1], [1, 0]] {
        assert_eq!(winner(&offers, &order, pinned), "a", "order {order:?}");
    }
}

#[test]
fn between_proven_pins_the_newest_wins() {
    let offers = [("a", pin("old", 10.0, true)), ("b", pin("new", 20.0, true))];
    for order in [[0, 1], [1, 0]] {
        assert_eq!(winner(&offers, &order, pinned), "b", "order {order:?}");
    }
}

/// A kid's offline tablet that never heard of the PIN set earlier cannot
/// replace it with a first PIN of its own set later.
#[test]
fn between_first_pins_the_oldest_wins() {
    let offers = [
        ("a", pin("earlier", 10.0, false)),
        ("b", pin("later", 20.0, false)),
    ];
    for order in [[0, 1], [1, 0]] {
        assert_eq!(winner(&offers, &order, pinned), "a", "order {order:?}");
    }
}

#[test]
fn pins_of_one_kind_and_time_tie_on_the_device_id() {
    for proven in [true, false] {
        let offers = [("a", pin("x", 10.0, proven)), ("b", pin("y", 10.0, proven))];
        for order in [[0, 1], [1, 0]] {
            assert_eq!(winner(&offers, &order, pinned), "b", "{proven} {order:?}");
        }
    }
}
