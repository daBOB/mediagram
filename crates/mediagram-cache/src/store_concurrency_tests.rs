use std::fs;

use tempfile::tempdir;

use super::*;

#[test]
fn concurrent_puts_of_the_same_key_never_interleave() {
    let dir = tempdir().expect("temp dir");
    let store =
        std::sync::Arc::new(ChunkStore::open(dir.path().to_path_buf(), 1 << 30).expect("open"));
    let total = rules::CHUNK;
    let a = vec![b'a'; total as usize];
    let b = vec![b'b'; total as usize];

    let store_a = store.clone();
    let a_body = a.clone();
    let t1 = std::thread::spawn(move || store_a.put("set1", 0, total, &a_body));
    let store_b = store.clone();
    let b_body = b.clone();
    let t2 = std::thread::spawn(move || store_b.put("set1", 0, total, &b_body));

    let r1 = t1.join().unwrap().unwrap();
    let r2 = t2.join().unwrap().unwrap();

    // One wins (Created), the other is told it already exists.
    let outcomes = [r1, r2];
    assert_eq!(
        outcomes
            .iter()
            .filter(|o| **o == PutOutcome::Created)
            .count(),
        1
    );
    assert_eq!(
        outcomes
            .iter()
            .filter(|o| **o == PutOutcome::AlreadyHeld)
            .count(),
        1
    );

    // Whichever won, the stored body is intact: entirely `a` or entirely
    // `b`, never a mix of the two.
    let stored = store.get("set1", 0).unwrap().unwrap();
    assert!(stored == a || stored == b);
}

/// Regression for a race where the total-pairing file was visible (via
/// `create_new`) before its content was written: a loser reading it in that
/// window saw an empty file, a parse error, and an `Err` out of `put` —
/// never a panic or a wrong count, but never surfaced to a caller as a
/// clean outcome either. Looped well past what one run reliably catches.
#[test]
fn concurrent_first_puts_with_different_totals_give_one_created_and_one_mismatch() {
    for i in 0..300 {
        let dir = tempdir().expect("temp dir");
        let store =
            std::sync::Arc::new(ChunkStore::open(dir.path().to_path_buf(), 1 << 30).expect("open"));
        let id = format!("set{i}");

        let store_a = store.clone();
        let id_a = id.clone();
        let t1 =
            std::thread::spawn(move || store_a.put(&id_a, 0, 100, b"x".repeat(100).as_slice()));
        let store_b = store.clone();
        let id_b = id.clone();
        let t2 =
            std::thread::spawn(move || store_b.put(&id_b, 0, 200, b"y".repeat(100).as_slice()));

        let r1 = t1.join().unwrap();
        let r2 = t2.join().unwrap();
        assert!(r1.is_ok(), "run {i}: {r1:?}");
        assert!(r2.is_ok(), "run {i}: {r2:?}");
        let outcomes = [r1.unwrap(), r2.unwrap()];

        assert_eq!(
            outcomes
                .iter()
                .filter(|o| **o == PutOutcome::Created)
                .count(),
            1,
            "run {i}: {outcomes:?}"
        );
        assert_eq!(
            outcomes
                .iter()
                .filter(|o| matches!(o, PutOutcome::TotalMismatch { .. }))
                .count(),
            1,
            "run {i}: {outcomes:?}"
        );
    }
}

/// Same race, but both first writers agree on the total: the pairing file
/// still has exactly one creator and one reader of what it wrote, so this
/// must never see an empty or unparseable total either.
#[test]
fn concurrent_first_puts_with_equal_totals_never_error() {
    for i in 0..300 {
        let dir = tempdir().expect("temp dir");
        let store =
            std::sync::Arc::new(ChunkStore::open(dir.path().to_path_buf(), 1 << 30).expect("open"));
        let id = format!("set{i}");

        let store_a = store.clone();
        let id_a = id.clone();
        let t1 =
            std::thread::spawn(move || store_a.put(&id_a, 0, 100, b"x".repeat(100).as_slice()));
        let store_b = store.clone();
        let id_b = id.clone();
        let t2 =
            std::thread::spawn(move || store_b.put(&id_b, 0, 100, b"y".repeat(100).as_slice()));

        let r1 = t1.join().unwrap();
        let r2 = t2.join().unwrap();
        assert!(r1.is_ok(), "run {i}: {r1:?}");
        assert!(r2.is_ok(), "run {i}: {r2:?}");
        let outcomes = [r1.unwrap(), r2.unwrap()];

        // Same chunk, same total: exactly one writer publishes it, the
        // other sees it already held. Never a mismatch, never an error.
        assert_eq!(
            outcomes
                .iter()
                .filter(|o| **o == PutOutcome::Created)
                .count(),
            1,
            "run {i}: {outcomes:?}"
        );
        assert_eq!(
            outcomes
                .iter()
                .filter(|o| **o == PutOutcome::AlreadyHeld)
                .count(),
            1,
            "run {i}: {outcomes:?}"
        );
    }
}

/// Whether `.tmp` holds a fully staged body of `len` bytes.
fn staged_body(root: &std::path::Path, len: u64) -> bool {
    fs::read_dir(root.join(".tmp"))
        .unwrap()
        .any(|entry| entry.unwrap().metadata().unwrap().len() == len)
}

/// Eviction runs under the index lock and removes a set's directory once it
/// holds no chunk file. A PUT that created the directory, recorded the total
/// or linked its chunk outside that lock could have all three removed under
/// it: a 500 for the link, or a 201 for a chunk already gone. Holding the
/// lock here stands in for an eviction in progress; the PUT may stage its
/// body, but must not touch its set until the lock is free.
#[test]
fn a_put_leaves_its_set_alone_while_eviction_holds_the_index_lock() {
    let dir = tempdir().expect("temp dir");
    let root = dir.path().to_path_buf();
    let store = std::sync::Arc::new(ChunkStore::open(root.clone(), 1 << 30).expect("open"));

    let evicting = store.lock_index();
    let writer = {
        let store = store.clone();
        std::thread::spawn(move || store.put("set1", 0, 100, &[b'x'; 100]))
    };
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(10);
    while !root.join("set1").exists() && !staged_body(&root, 100) {
        assert!(
            std::time::Instant::now() < deadline,
            "the PUT never staged its body"
        );
        std::thread::yield_now();
    }
    assert!(
        !root.join("set1").exists(),
        "the PUT wrote into its set while eviction held the lock"
    );
    drop(evicting);

    assert_eq!(writer.join().unwrap().unwrap(), PutOutcome::Created);
    assert_eq!(store.head("set1", 0).unwrap(), Some(100));
}

/// A refused PUT leaves nothing in `.tmp`: the directory is swept only at
/// startup, so a staged body left behind would hold its space until then.
#[test]
fn a_refused_put_leaves_no_staged_body() {
    let dir = tempdir().expect("temp dir");
    let store = ChunkStore::open(dir.path().to_path_buf(), 1 << 30).expect("open");
    store.put("set1", 0, 200, &[b'x'; 100]).unwrap();

    let outcome = store.put("set1", 1, 300, &[b'y'; 100]).unwrap();

    assert_eq!(outcome, PutOutcome::TotalMismatch { held: 200 });
    assert_eq!(fs::read_dir(dir.path().join(".tmp")).unwrap().count(), 0);
}

/// The race end to end: a budget of two chunks, one thread filling set
/// `s` while another fills other sets, so `s` keeps losing its oldest
/// chunk to eviction while its next one is being written.
#[test]
fn puts_racing_eviction_of_their_own_set_never_error() {
    let dir = tempdir().expect("temp dir");
    let store = std::sync::Arc::new(ChunkStore::open(dir.path().to_path_buf(), 200).expect("open"));
    let others = {
        let store = store.clone();
        std::thread::spawn(move || {
            for i in 0..2000 {
                store
                    .put(&format!("other{i}"), 0, 100, &[b'o'; 100])
                    .unwrap();
            }
        })
    };
    for n in 0..2000 {
        let outcome = store.put("s", n, 30_000, &[b's'; 100]);
        assert!(outcome.is_ok(), "chunk {n}: {outcome:?}");
    }
    others.join().unwrap();
}
