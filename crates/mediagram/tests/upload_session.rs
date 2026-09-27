//! The upload session every uploading command walks its items through,
//! against an in-memory transport and channel.
//!
//! Documents stand in for every new item — they plan with a stat and an
//! insert, no ffprobe and no TMDB — and `Set::Planned` for what `add` and
//! `resume` hand over.

mod support;

use std::cell::Cell;

use mediagram::config::Config;
use mediagram::index::set_row::SetRow;
use mediagram::index::status::SetStatus;
use mediagram::index::{db, lifecycle, parts, pins, sets};
use mediagram::upload::lock;
use mediagram::upload::new_set::{LessonOf, NewSet};
use mediagram::upload::record_document::Document;
use mediagram::upload::session::{Counts, Item, Outcome, Session, Set, Step};
use mlib_spec::caption::{Episode, Kind};
use support::channel::{FakeChannel, sets_in};
use support::session::{FakeLink, config_in};
use support::upload::{FakeTransport, sample_caption};
use tempfile::TempDir;

fn config(dir: &TempDir) -> Config {
    config_in(dir.path())
}

/// Document `number` of one collection, its file written beside the index.
fn document(dir: &TempDir, number: u32) -> Item<u32> {
    let file = dir.path().join(format!("handout-{number}.pdf"));
    if !file.exists() {
        std::fs::write(&file, format!("handout {number}")).unwrap();
    }
    Item {
        tag: number,
        set: Set::Document(Document {
            file,
            course: "Rust".into(),
            cid: "rust".into(),
            chapter: 1,
            chapter_title: None,
            path: None,
            number,
            title: None,
            variant: None,
        }),
        delete_source: None,
    }
}

/// A set planned as `add` plans one, its source `bytes` long on disk.
fn planned(dir: &TempDir, id: &str, bytes: u64) -> Item<String> {
    let mut conn = db::open(dir.path()).unwrap();
    let set = SetRow::from_caption(&sample_caption(id, bytes, 1), 1);
    let tx = conn.transaction().unwrap();
    sets::insert_set(&tx, &set).unwrap();
    parts::insert_parts(&tx, id, &mlib_spec::plan_parts(bytes, 1024 * 1024).unwrap()).unwrap();
    tx.commit().unwrap();
    let source = dir.path().join(format!("{id}.mkv"));
    std::fs::write(&source, vec![7u8; bytes as usize]).unwrap();
    lifecycle::record_source(&conn, id, &source, false).unwrap();
    Item {
        tag: id.to_string(),
        set: Set::Planned(id.to_string()),
        delete_source: None,
    }
}

fn quiet<T>(_: &T, _: Step<'_>) {}

fn sends(channel: &FakeChannel) -> usize {
    channel.with(|c| c.sends)
}

fn published(channel: &FakeChannel) -> Vec<String> {
    let id = *channel
        .with(|c| c.pinned_indexes())
        .last()
        .expect("a published index");
    sets_in(&channel.document(id))
}

#[tokio::test]
async fn uploads_then_publishes_once() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();

    let counts = session
        .upload([document(&dir, 1), document(&dir, 2)], quiet)
        .await;
    session.end(false).await.unwrap();

    assert_eq!(
        counts,
        Counts {
            uploaded: 2,
            ..Counts::default()
        }
    );
    assert_eq!(connects.get(), 1);
    assert_eq!(sends(&channel), 1);
    assert_eq!(published(&channel).len(), 2);
}

#[tokio::test]
async fn a_re_run_that_finds_everything_held_never_connects() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel) = (FakeTransport::new(), FakeChannel::new());
    let first = Cell::new(0);
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &first)).unwrap();
    session.upload([document(&dir, 1)], quiet).await;
    session.end(false).await.unwrap();

    let again = Cell::new(0);
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &again)).unwrap();
    let counts = session.upload([document(&dir, 1)], quiet).await;
    session.end(false).await.unwrap();

    assert_eq!(
        counts,
        Counts {
            held: 1,
            ..Counts::default()
        }
    );
    assert_eq!(again.get(), 0);
    assert_eq!(sends(&channel), 1);
}

/// `--no-push` publishes nothing now; the next session that may publish
/// does, even with nothing of its own to upload.
#[tokio::test]
async fn a_publish_skipped_by_no_push_is_owed_and_paid_by_the_next_session() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    session.upload([document(&dir, 1)], quiet).await;
    session.end(true).await.unwrap();
    assert_eq!(sends(&channel), 0);

    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    session.upload([document(&dir, 1)], quiet).await;
    session.end(false).await.unwrap();

    assert_eq!(sends(&channel), 1);
    assert_eq!(published(&channel).len(), 1);
    assert_eq!(
        pins::publish_owed(&db::open(dir.path()).unwrap()).unwrap(),
        None
    );
}

/// Another upload is running: it publishes when it ends, so this one does
/// not add a pin — and owes the publish in case that one completes nothing.
#[tokio::test]
async fn another_upload_running_defers_the_publish_and_owes_it() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    session.upload([document(&dir, 1)], quiet).await;

    let running = lock::acquire(dir.path(), || {}).await.unwrap();
    session.end(false).await.unwrap();
    drop(running);

    assert_eq!(sends(&channel), 0);
    assert!(
        pins::publish_owed(&db::open(dir.path()).unwrap())
            .unwrap()
            .is_some()
    );
}

/// The line going down is not one item's trouble: every later item would
/// fail the same way. The walk stops, what completed is published, and
/// `end` says what happened.
#[tokio::test]
async fn a_failure_while_sending_stops_the_walk_and_publishes_what_completed() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    let mut ended = Vec::new();

    let counts = session
        .upload(
            [document(&dir, 1), document(&dir, 2), document(&dir, 3)],
            |n, step| {
                if let Step::End(outcome) = step {
                    ended.push(*n);
                    if matches!(outcome, Outcome::Uploaded) {
                        transport.set_fail_on_part(0);
                    }
                }
            },
        )
        .await;
    let err = session.end(false).await.unwrap_err();

    assert_eq!(
        counts,
        Counts {
            uploaded: 1,
            pending: 1,
            ..Counts::default()
        }
    );
    assert_eq!(ended, [1, 2]);
    assert_eq!(sends(&channel), 1);
    let said = format!("{err:#}");
    assert!(
        said.contains("injected failure") && said.contains("1 item(s) not reached"),
        "{said}"
    );
}

/// A missing source is that set's trouble alone: the next one still goes,
/// and a run where every source is missing never connects.
#[tokio::test]
async fn a_missing_source_blocks_its_set_and_the_walk_goes_on() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let gone = planned(&dir, "01J0000000000000000000SES1", 64);
    std::fs::remove_file(dir.path().join("01J0000000000000000000SES1.mkv")).unwrap();

    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    let counts = session.upload([gone], quiet).await;
    assert_eq!(connects.get(), 0);
    let fine = planned(&dir, "01J0000000000000000000SES2", 64);
    let more = session.upload([fine], quiet).await;
    session.end(false).await.unwrap();

    assert_eq!(
        counts,
        Counts {
            blocked: 1,
            ..Counts::default()
        }
    );
    assert_eq!(
        more,
        Counts {
            uploaded: 1,
            ..Counts::default()
        }
    );
}

#[tokio::test]
async fn a_source_is_deleted_only_once_its_set_is_complete() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let mut kept = document(&dir, 1);
    let kept_file = dir.path().join("handout-1.pdf");
    kept.delete_source = Some(kept_file.clone());
    transport.set_fail_on_part(0);
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    session.upload([kept], quiet).await;
    let _ = session.end(true).await;
    assert!(kept_file.exists());

    let fresh = FakeTransport::new();
    let mut gone = document(&dir, 2);
    let gone_file = dir.path().join("handout-2.pdf");
    gone.delete_source = Some(gone_file.clone());
    let mut session = Session::new(&cfg, FakeLink::new(&fresh, &channel, &connects)).unwrap();
    session.upload([gone], quiet).await;
    session.end(true).await.unwrap();
    assert!(!gone_file.exists());
}

/// A walked item another run started is `resume`'s, not planned again.
#[tokio::test]
async fn a_walked_item_started_elsewhere_is_left_pending() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let Set::Document(started) = document(&dir, 1).set else {
        unreachable!()
    };
    mediagram::upload::record_document::record_document_set(&cfg, &started).unwrap();

    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    let counts = session.upload([document(&dir, 1)], quiet).await;
    session.end(false).await.unwrap();

    assert_eq!(
        counts,
        Counts {
            pending: 1,
            ..Counts::default()
        }
    );
    assert_eq!(
        sets::list_pending(&db::open(dir.path()).unwrap())
            .unwrap()
            .len(),
        1
    );
    assert_eq!(transport.send_count(), 0);
}

/// What `add` planned and `resume` finds: a pending set this session owns.
#[tokio::test]
async fn a_planned_set_is_finished() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let item = planned(&dir, "01J0000000000000000000SES3", 64);

    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    let counts = session.upload([item], quiet).await;
    session.end(false).await.unwrap();

    assert_eq!(
        counts,
        Counts {
            uploaded: 1,
            ..Counts::default()
        }
    );
    assert_eq!(published(&channel), ["01J0000000000000000000SES3"]);
}

/// Completing a set is one transaction: when it cannot commit, the set is
/// still pending — nothing is owed, nothing published — and the session
/// stops, since the index is what failed.
#[tokio::test]
async fn a_set_whose_completion_cannot_commit_stays_pending() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let item = planned(&dir, "01J0000000000000000000SES4", 4);
    db::open(dir.path())
        .unwrap()
        .execute_batch(
            "CREATE TRIGGER refuse_source_cleanup BEFORE DELETE ON meta
             WHEN OLD.key LIKE 'source:%' BEGIN SELECT RAISE(ABORT, 'cleanup failed'); END;",
        )
        .unwrap();

    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    let counts = session.upload([item], quiet).await;
    let err = session.end(false).await.unwrap_err();

    assert_eq!(
        counts,
        Counts {
            pending: 1,
            ..Counts::default()
        }
    );
    assert!(format!("{err:#}").contains("cleanup failed"), "{err:#}");
    assert_eq!(sends(&channel), 0);
    let row = sets::get_set(&db::open(dir.path()).unwrap(), "01J0000000000000000000SES4").unwrap();
    assert_eq!(row.unwrap().status, SetStatus::Pending);
}

#[tokio::test]
async fn a_connection_that_will_not_open_stops_the_session() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let mut link = FakeLink::new(&transport, &channel, &connects);
    link.down = true;

    let mut session = Session::new(&cfg, link).unwrap();
    let counts = session
        .upload([document(&dir, 1), document(&dir, 2)], quiet)
        .await;
    let err = session.end(false).await.unwrap_err();

    assert_eq!(
        counts,
        Counts {
            pending: 1,
            ..Counts::default()
        }
    );
    assert!(format!("{err:#}").contains("the line is down"));
}

/// A documentary collection records its episodes as `docu`; found by the
/// data it is planned with, a re-run holds one rather than uploading it
/// again — without planning, so without reading the file.
#[tokio::test]
async fn a_documentary_collection_re_run_finds_its_episodes_held() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let conn = db::open(dir.path()).unwrap();
    let mut caption = sample_caption("01J0000000000000000000SES5", 10, 1);
    caption.t = Kind::Docu;
    caption.cid = Some("nature".into());
    caption.s = Some(1);
    caption.e = Some(Episode::Single(3));
    sets::insert_set(&conn, &SetRow::from_caption(&caption, 1)).unwrap();
    sets::set_status(&conn, "01J0000000000000000000SES5", SetStatus::Complete).unwrap();
    let episode = Item {
        tag: (),
        set: Set::File(NewSet {
            file: dir.path().join("never-read.mkv"),
            lesson: Some(LessonOf {
                course: "Nature".into(),
                cid: "nature".into(),
                chapter: Some(1),
                chapter_title: None,
                path: None,
                number: Some(3),
                kind: Kind::Docu,
            }),
            docu: true,
            ..NewSet::default()
        }),
        delete_source: None,
    };

    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    let counts = session.upload([episode], quiet).await;
    session.end(false).await.unwrap();

    assert_eq!(
        counts,
        Counts {
            held: 1,
            ..Counts::default()
        }
    );
    assert_eq!(connects.get(), 0);
}

/// A debt recorded while a publish is under way — a set completing after
/// that publish took its snapshot — is for sets the snapshot may lack, so it
/// outlives the publish.
#[tokio::test]
async fn a_publish_owed_after_the_snapshot_is_still_owed_after_the_publish() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let data_dir = dir.path().to_path_buf();
    // The second look is the one just before sending, after the snapshot.
    channel.on_look(2, move |_| {
        pins::owe_publish(&db::open(&data_dir).unwrap()).unwrap()
    });

    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    session.upload([document(&dir, 1)], quiet).await;
    session.end(false).await.unwrap();

    assert_eq!(sends(&channel), 1);
    assert!(
        pins::publish_owed(&db::open(dir.path()).unwrap())
            .unwrap()
            .is_some()
    );
}

#[tokio::test]
async fn a_failed_publish_stays_owed_and_says_how_to_publish() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    channel.with(|c| c.send_fails = true);

    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    session.upload([document(&dir, 1)], quiet).await;
    let err = session.end(false).await.unwrap_err();

    assert!(
        format!("{err:#}").contains("mediagram push-index"),
        "{err:#}"
    );
    assert!(
        pins::publish_owed(&db::open(dir.path()).unwrap())
            .unwrap()
            .is_some()
    );
}

/// The set a background `add` was handed, finished meanwhile by `resume`:
/// found complete once the lock is held, so nothing is sent twice and
/// nothing connects — and the file `add` was asked to delete goes.
#[tokio::test]
async fn a_planned_set_finished_by_another_upload_is_held_not_sent_again() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config(&dir);
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let mut item = planned(&dir, "01J0000000000000000000SES6", 64);
    let source = dir.path().join("01J0000000000000000000SES6.mkv");
    item.delete_source = Some(source.clone());
    let conn = db::open(dir.path()).unwrap();
    sets::set_status(&conn, "01J0000000000000000000SES6", SetStatus::Complete).unwrap();

    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    let counts = session.upload([item], quiet).await;
    session.end(false).await.unwrap();

    assert_eq!(
        counts,
        Counts {
            held: 1,
            ..Counts::default()
        }
    );
    assert_eq!(connects.get(), 0);
    assert_eq!(transport.send_count(), 0);
    assert!(!source.exists());
}
