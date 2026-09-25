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
