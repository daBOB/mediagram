use std::time::{Duration, SystemTime};

use super::*;

fn key(n: u32) -> ChunkKey {
    ("set1".to_string(), n)
}

/// A GET can read a chunk's bytes off disk successfully and then race an
/// eviction that drops its index entry before the GET gets to `refresh`
/// under the lock. `refresh` must not manufacture an entry for a key it
/// does not already know: that phantom would count toward `chunk_count`
/// with nothing added to `held_bytes`, and a later `forget` of it would
/// subtract a size that was never added.
#[test]
fn refreshing_an_absent_key_is_a_no_op() {
    let mut index = Index::from_scan(Vec::new());
    let k = key(0);

    index.refresh(&k, 100, SystemTime::now());

    assert_eq!(index.chunk_count(), 0, "no phantom entry");
    assert_eq!(index.held_bytes(), 0);

    // The absent-key no-op must hold even once something else exists, so
    // this is not merely "empty index short-circuits everything".
    index.insert(key(1), 50, SystemTime::now());
    index.refresh(&k, 100, SystemTime::now());
    assert_eq!(index.chunk_count(), 1);
    assert_eq!(index.held_bytes(), 50);
}

/// A phantom entry, if one were created, must never be able to underflow
/// `held_bytes` on a later `forget` — checked directly in case `refresh`
/// regresses back to creating one.
#[test]
fn forgetting_a_key_refresh_never_added_does_not_underflow() {
    let mut index = Index::from_scan(Vec::new());
    let k = key(0);

    index.refresh(&k, 100, SystemTime::now());
    index.forget(&k);

    assert_eq!(index.held_bytes(), 0);
    assert_eq!(index.chunk_count(), 0);
}

/// A chunk can be re-PUT after someone deletes it by hand without going
/// through the store: the index still has a (now stale) entry for it, and
/// `insert` must replace that entry rather than add to it — otherwise
/// `held_bytes` counts the old size twice, and the old entry's order key is
/// orphaned in `order` with nothing in `meta` to match it.
#[test]
fn inserting_an_already_present_key_replaces_it_without_double_counting() {
    let mut index = Index::from_scan(Vec::new());
    let k = key(0);
    let t1 = SystemTime::now();
    let t2 = t1 + Duration::from_secs(1);

    index.insert(k.clone(), 100, t1);
    index.insert(k.clone(), 100, t2);

    assert_eq!(index.held_bytes(), 100, "must not double count the replace");
    assert_eq!(index.chunk_count(), 1);

    // No orphaned order key left over from the first insert: evicting
    // everything must report this key exactly once.
    let evicted = index.evict_over_budget(0);
    assert_eq!(evicted, vec![k]);
    assert_eq!(index.held_bytes(), 0);
    assert_eq!(index.chunk_count(), 0);
}

/// The replacement can also change size (a re-PUT is always the same body
/// for the same total in practice, but the bookkeeping should not assume
/// it): `held_bytes` must reflect only the latest size.
#[test]
fn inserting_an_already_present_key_with_a_different_size_updates_held_bytes() {
    let mut index = Index::from_scan(Vec::new());
    let k = key(0);

    index.insert(k.clone(), 100, SystemTime::now());
    index.insert(k.clone(), 40, SystemTime::now());

    assert_eq!(index.held_bytes(), 40);
    assert_eq!(index.chunk_count(), 1);
}
