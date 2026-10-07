use std::cell::Cell;
use std::path::{Path, PathBuf};

use super::*;
use crate::index::set_row::SetRow;
use crate::index::{db, parts};
use crate::test_fakes::channel::FakeChannel;
use crate::test_fakes::session::{FakeLink, config_in};
use crate::test_fakes::upload::{FakeTransport, sample_caption};
use crate::upload::record_document::{Document, record_document_set};

/// A set planned as `add` plans one, its source written beside the index.
/// A document, so completing it starts no ffprobe for subtitles.
fn planned(dir: &Path, id: &str) -> (Item<()>, PathBuf) {
    let mut conn = db::open(dir).unwrap();
    let mut caption = sample_caption(id, 64, 1);
    caption.t = Kind::Doc;
    let tx = conn.transaction().unwrap();
    sets::insert_set(&tx, &SetRow::from_caption(&caption, 1)).unwrap();
    parts::insert_parts(&tx, id, &mlib_spec::plan_parts(64, 1024 * 1024).unwrap()).unwrap();
    tx.commit().unwrap();
    let source = dir.join(format!("{id}.pdf"));
    std::fs::write(&source, [7u8; 64]).unwrap();
    lifecycle::record_source(&conn, id, &source, false).unwrap();
    let item = Item {
        tag: (),
        set: Set::Planned(id.to_string()),
        delete_source: None,
    };
    (item, source)
}

fn handout(dir: &Path) -> Document {
    let file = dir.join("handout.pdf");
    std::fs::write(&file, b"handout").unwrap();
    Document {
        file,
        course: "Rust".to_string(),
        cid: "rust".to_string(),
        chapter: 1,
        chapter_title: None,
        path: None,
        number: 1,
        title: None,
        variant: None,
    }
}

/// Runs one item, counting how often it said it had started.
async fn run_one(
    session: &mut Session<'_, FakeLink<'_>>,
    item: &Item<()>,
) -> (Option<Outcome>, u32) {
    let mut starts = 0;
    let outcome = session
        .run(item, &mut |_: &(), step: Step<'_>| {
            if matches!(step, Step::Start) {
                starts += 1;
            }
        })
        .await;
    (outcome, starts)
}

/// The upload that completed the set may still be reading subtitles from
/// the very file this item was asked to delete; until it is done, the file
/// stays.
#[tokio::test]
async fn a_held_set_whose_original_is_still_being_read_keeps_its_source() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let (mut item, source) = planned(dir.path(), "01J000000000000000000ITEM1");
    item.delete_source = Some(source.clone());
    let conn = db::open(dir.path()).unwrap();
    sets::set_status(&conn, "01J000000000000000000ITEM1", SetStatus::Complete).unwrap();
    lifecycle::record_original(&conn, "01J000000000000000000ITEM1", &source).unwrap();
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();

    let (outcome, starts) = run_one(&mut session, &item).await;

    assert!(matches!(outcome, Some(Outcome::AlreadyHeld)));
    assert_eq!(starts, 0);
    assert!(source.exists());
}

/// When the index cannot say whether the original is still being read, the
/// file is kept: deleting it is the one step that cannot be taken back.
#[tokio::test]
async fn a_held_set_whose_original_cannot_be_checked_keeps_its_source() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let (mut item, source) = planned(dir.path(), "01J000000000000000000ITEM1");
    item.delete_source = Some(source.clone());
    let conn = db::open(dir.path()).unwrap();
    sets::set_status(&conn, "01J000000000000000000ITEM1", SetStatus::Complete).unwrap();
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    session.conn.execute_batch("DROP TABLE meta").unwrap();

    let (outcome, starts) = run_one(&mut session, &item).await;

    assert!(matches!(outcome, Some(Outcome::AlreadyHeld)));
    assert_eq!(starts, 0);
    assert!(source.exists());
}

/// Only a set `add` planned for exactly this file vouches for the file. A
/// walked item found complete was uploaded from some other copy, so the
/// file named here is not deleted on its account.
#[tokio::test]
async fn a_walked_item_found_complete_is_held_and_its_file_kept() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let doc = handout(dir.path());
    let set_id = record_document_set(&cfg, &doc).unwrap();
    sets::set_status(&db::open(dir.path()).unwrap(), &set_id, SetStatus::Complete).unwrap();
    let file = doc.file.clone();
    let item = Item {
        tag: (),
        set: Set::Document(doc),
        delete_source: Some(file.clone()),
    };
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();

    let (outcome, starts) = run_one(&mut session, &item).await;

    assert!(matches!(outcome, Some(Outcome::AlreadyHeld)));
    assert_eq!(starts, 0);
    assert!(file.exists());
    assert_eq!(connects.get(), 0);
}

/// A planned id the index does not hold is that item's failure, not the
/// session's: nothing started, and the next item is still tried.
#[tokio::test]
async fn a_planned_id_missing_from_the_index_fails_that_item_only() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let item = Item {
        tag: (),
        set: Set::Planned("missing".to_string()),
        delete_source: None,
    };
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();

    let (outcome, starts) = run_one(&mut session, &item).await;

    let Some(Outcome::Failed(err)) = outcome else {
        panic!("expected a failed item, got {outcome:?}");
    };
    assert_eq!(err.to_string(), "no set missing in the index");
    assert_eq!(starts, 0);
    assert!(session.stopped.is_none());
}

/// A planned set says it has started, connects, and is sent; completing it
/// forgets the original, which is what lets another upload delete it.
#[tokio::test]
async fn a_planned_set_starts_uploads_and_forgets_its_original() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let (item, source) = planned(dir.path(), "01J000000000000000000ITEM2");
    let conn = db::open(dir.path()).unwrap();
    lifecycle::record_original(&conn, "01J000000000000000000ITEM2", &source).unwrap();
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();

    let (outcome, starts) = run_one(&mut session, &item).await;

    assert!(matches!(outcome, Some(Outcome::Uploaded)));
    assert_eq!(starts, 1);
    assert_eq!(transport.send_count(), 1);
    assert_eq!(
        lifecycle::original_of(&conn, "01J000000000000000000ITEM2").unwrap(),
        None
    );
}

/// The local index failing is not one item's trouble: the session stops,
/// and this item, never begun, is not reported at all.
#[tokio::test]
async fn an_index_that_cannot_be_read_stops_the_session_unreported() {
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let (item, _) = planned(dir.path(), "01J000000000000000000ITEM3");
    let mut session = Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
    session
        .conn
        .execute_batch("PRAGMA foreign_keys = OFF; DROP TABLE sets;")
        .unwrap();

    let (outcome, starts) = run_one(&mut session, &item).await;

    assert!(outcome.is_none());
    assert_eq!(starts, 0);
    assert!(session.stopped.is_some());
}
