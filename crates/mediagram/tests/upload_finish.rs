//! `finish_one`: the checks that stand between a planned set and sending
//! bytes for it. A resumed upload must be of the file the set was planned
//! against, byte for byte in size, or every part it sends is wrong.

use mediagram::index::db;
use mediagram::upload::finish::finish_one;

mod support;
use support::upload::{FakeTransport, sample_caption, seeded_index};

const SIZE: usize = 3 * 1024 * 1024;

#[tokio::test]
async fn a_set_with_no_recorded_source_is_refused_before_sending() {
    let caption = sample_caption("01J0000000000000000000FIN1", SIZE as u64, 3);
    let (dir, conn, set, _) = seeded_index(&caption).await;
    let transport = FakeTransport::new();

    let refused = finish_one(&conn, &transport, 0, &set, dir.path()).await.unwrap_err();

    assert!(format!("{refused:#}").contains("no recorded source path"));
    assert_eq!(transport.send_count(), 0);
}

#[tokio::test]
async fn a_source_that_is_gone_is_refused_before_sending() {
    let caption = sample_caption("01J0000000000000000000FIN2", SIZE as u64, 3);
    let (dir, conn, set, _) = seeded_index(&caption).await;
    let gone = dir.path().join("moved-away.mkv");
    db::set_meta(&conn, &db::source_key(&set.set_id), gone.to_str().unwrap()).unwrap();
    let transport = FakeTransport::new();

    let refused = finish_one(&conn, &transport, 0, &set, dir.path()).await.unwrap_err();

    assert!(format!("{refused:#}").contains("unavailable"));
    assert_eq!(transport.send_count(), 0);
}

#[tokio::test]
async fn a_source_that_changed_size_is_refused_before_sending() {
    let caption = sample_caption("01J0000000000000000000FIN3", SIZE as u64, 3);
    let (dir, conn, set, _) = seeded_index(&caption).await;
    let source = dir.path().join("movie.mkv");
    std::fs::write(&source, vec![0u8; SIZE - 1]).unwrap();
    db::set_meta(&conn, &db::source_key(&set.set_id), source.to_str().unwrap()).unwrap();
    let transport = FakeTransport::new();

    let refused = finish_one(&conn, &transport, 0, &set, dir.path()).await.unwrap_err();

    assert!(format!("{refused:#}").contains("changed since add"));
    assert_eq!(transport.send_count(), 0);
}

#[tokio::test]
async fn a_finished_set_forgets_its_source() {
    let caption = sample_caption("01J0000000000000000000FIN4", SIZE as u64, 3);
    let (dir, conn, set, _) = seeded_index(&caption).await;
    let source = dir.path().join("movie.mkv");
    std::fs::write(&source, vec![7u8; SIZE]).unwrap();
    let key = db::source_key(&set.set_id);
    db::set_meta(&conn, &key, source.to_str().unwrap()).unwrap();
    let transport = FakeTransport::new();

    let complete = finish_one(&conn, &transport, 0, &set, dir.path()).await.unwrap();

    assert!(complete);
    assert_eq!(transport.send_count(), 3);
    assert_eq!(db::get_meta(&conn, &key).unwrap(), None);
}
