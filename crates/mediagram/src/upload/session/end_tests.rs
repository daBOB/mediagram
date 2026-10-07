use std::cell::Cell;

use super::*;
use crate::index::db;
use crate::test_fakes::channel::FakeChannel;
use crate::test_fakes::session::{FakeLink, config_in};
use crate::test_fakes::upload::FakeTransport;

fn owed(dir: &std::path::Path) -> bool {
    pins::publish_owed(&db::open(dir).unwrap())
        .unwrap()
        .is_some()
}

#[tokio::test]
async fn a_session_that_owes_nothing_never_connects() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();

    end(session, false).await.unwrap();

    assert_eq!(connects.get(), 0);
}

/// A debt left by an earlier session is paid by this one, even though this
/// one completed nothing.
#[tokio::test]
async fn a_publish_owed_is_paid_once() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    pins::owe_publish(&session.conn).unwrap();

    end(session, false).await.unwrap();

    assert_eq!(channel.with(|c| c.sends), 1);
    assert!(!owed(dir.path()));
}

/// What stopped the session is the error, and its message says how many
/// items were never reached; what completed before it is still published.
#[tokio::test]
async fn a_stopped_session_publishes_what_completed_then_says_why_it_stopped() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    pins::owe_publish(&session.conn).unwrap();
    session.stopped = Some(anyhow::anyhow!("the line went down"));
    session.unreached = 2;

    let err = end(session, false).await.unwrap_err();

    assert_eq!(err.to_string(), "the upload stopped; 2 item(s) not reached");
    assert_eq!(err.root_cause().to_string(), "the line went down");
    assert_eq!(channel.with(|c| c.sends), 1);
}

/// Both failures are told: the stop as the cause, the publish beside it
/// with how to make up for it. The debt survives for that publish.
#[tokio::test]
async fn a_stopped_session_whose_publish_fails_says_both() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let mut link = FakeLink::new(&transport, &channel, &connects);
    link.down = true;
    let mut session = Session::new(&cfg, link).unwrap();
    pins::owe_publish(&session.conn).unwrap();
    session.stopped = Some(anyhow::anyhow!("the line went down"));

    let err = end(session, false).await.unwrap_err();

    assert_eq!(
        err.to_string(),
        "the upload stopped, and the index was not pushed; run `mediagram push-index`: \
         the line is down"
    );
    assert_eq!(err.root_cause().to_string(), "the line went down");
    assert!(owed(dir.path()));
}

/// With more than one slot, an upload in any slot defers the publish, not
/// only one in slot 0: two sessions publishing side by side pin twice.
#[tokio::test]
async fn another_upload_in_a_later_slot_defers_the_publish() {
    let dir = tempfile::tempdir().unwrap();
    let mut cfg = config_in(dir.path());
    cfg.upload_slots = 2;
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    let _held = lock::acquire_file(&lock::slot_path(dir.path(), 1), || {})
        .await
        .unwrap();
    pins::owe_publish(&session.conn).unwrap();

    end(session, false).await.unwrap();

    assert_eq!(connects.get(), 0);
    assert!(owed(dir.path()));
}
