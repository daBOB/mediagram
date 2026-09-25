use super::*;
use crate::state::StateDb;

#[test]
fn device_identity_survives_reopening_and_is_distinct_from_another_install() {
    let first_dir = tempfile::tempdir().unwrap();
    let second_dir = tempfile::tempdir().unwrap();
    let first = StateDb::new(first_dir.path().to_path_buf())
        .with(device_id)
        .unwrap();
    let reopened = StateDb::new(first_dir.path().to_path_buf())
        .with(device_id)
        .unwrap();
    let second = StateDb::new(second_dir.path().to_path_buf())
        .with(device_id)
        .unwrap();
    assert_eq!(first, reopened);
    assert_ne!(first, second);
    assert_eq!(first.len(), 32);
    assert!(
        first
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
    );
}

#[test]
fn independent_connections_converge_on_one_persisted_identity() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("state.db");
    StateDb::new(dir.path().to_path_buf())
        .with(|_| Ok(()))
        .unwrap();
    let ready = std::sync::Arc::new(std::sync::Barrier::new(3));
    let mut workers = Vec::new();
    for _ in 0..2 {
        let path = path.clone();
        let ready = ready.clone();
        workers.push(std::thread::spawn(move || {
            let conn = Connection::open(path).unwrap();
            ready.wait();
            device_id(&conn).unwrap()
        }));
    }
    ready.wait();
    let ids: Vec<_> = workers.into_iter().map(|w| w.join().unwrap()).collect();
    assert_eq!(ids[0], ids[1]);
    let stored = device_id(&Connection::open(path).unwrap()).unwrap();
    assert_eq!(ids[0], stored);
}
