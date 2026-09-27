//! The checks that stand between a planned set and sending bytes for it. A
//! resumed upload must be of the file the set was planned against, byte for
//! byte in size, or every part it sends is wrong — so a set whose source is
//! missing or changed is blocked, and nothing of it is sent.

use std::cell::Cell;

use mediagram::index::db;
use mediagram::upload::session::{Item, Outcome, Session, Set, Step};

mod support;
use support::channel::FakeChannel;
use support::session::{FakeLink, config_in};
use support::upload::{FakeTransport, sample_caption, seeded_index};

const SIZE: usize = 3 * 1024 * 1024;

/// Finishes `set_id` in a session of its own, returning why it was blocked,
/// or `None` when it was not.
async fn finish(dir: &std::path::Path, transport: &FakeTransport, set_id: &str) -> Option<String> {
    let cfg = config_in(dir);
    let (channel, connects) = (FakeChannel::new(), Cell::new(0));
    let item = Item {
        tag: (),
        set: Set::Planned(set_id.into()),
        delete_source: None,
    };
    let mut blocked = None;
    let mut session = Session::new(&cfg, FakeLink::new(transport, &channel, &connects)).unwrap();
    session
        .upload([item], |(), step| {
            if let Step::End(Outcome::Blocked(err)) = step {
                blocked = Some(format!("{err:#}"));
            }
        })
        .await;
    session.end(true).await.unwrap();
    blocked
}

#[tokio::test]
async fn a_set_with_no_recorded_source_is_refused_before_sending() {
    let caption = sample_caption("01J0000000000000000000FIN1", SIZE as u64, 3);
    let (dir, _conn, set, _) = seeded_index(&caption).await;
    let transport = FakeTransport::new();

    let refused = finish(dir.path(), &transport, &set.set_id).await.unwrap();

    assert!(refused.contains("no recorded source path"), "{refused}");
    assert_eq!(transport.send_count(), 0);
}

#[tokio::test]
async fn a_source_that_is_gone_is_refused_before_sending() {
    let caption = sample_caption("01J0000000000000000000FIN2", SIZE as u64, 3);
    let (dir, conn, set, _) = seeded_index(&caption).await;
    let gone = dir.path().join("moved-away.mkv");
    db::set_meta(&conn, &db::source_key(&set.set_id), gone.to_str().unwrap()).unwrap();
    let transport = FakeTransport::new();

    let refused = finish(dir.path(), &transport, &set.set_id).await.unwrap();

    assert!(refused.contains("unavailable"), "{refused}");
    assert_eq!(transport.send_count(), 0);
}

#[tokio::test]
async fn a_source_that_changed_size_is_refused_before_sending() {
    let caption = sample_caption("01J0000000000000000000FIN3", SIZE as u64, 3);
    let (dir, conn, set, _) = seeded_index(&caption).await;
    let source = dir.path().join("movie.mkv");
    std::fs::write(&source, vec![0u8; SIZE - 1]).unwrap();
    db::set_meta(
        &conn,
        &db::source_key(&set.set_id),
        source.to_str().unwrap(),
    )
    .unwrap();
    let transport = FakeTransport::new();

    let refused = finish(dir.path(), &transport, &set.set_id).await.unwrap();

    assert!(refused.contains("changed since add"), "{refused}");
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

    assert_eq!(finish(dir.path(), &transport, &set.set_id).await, None);

    assert_eq!(transport.send_count(), 3);
    assert_eq!(db::get_meta(&conn, &key).unwrap(), None);
}
