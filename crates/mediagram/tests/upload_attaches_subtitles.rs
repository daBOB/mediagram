//! What an upload session does about subtitles once a set is complete: reads
//! them from the file the person named, and only then lets that file go.

mod support;

use std::cell::Cell;
use std::path::Path;

use mediagram::index::set_row::SetRow;
use mediagram::index::{db, lifecycle, parts, sets, subtitles};
use mediagram::upload::record_document::Document;
use mediagram::upload::session::{Counts, Item, Session, Set, Step};
use mlib_spec::subtitle_bundle::{SUBS_CAPTION_PREFIX, decode};
use support::channel::FakeChannel;
use support::media::{HALLO, Sub, make_mkv};
use support::session::{FakeLink, config_in};
use support::upload::{FakeTransport, sample_caption};
use tempfile::TempDir;

const SET: &str = "01J0000000000000000000ATT1";

fn quiet<T>(_: &T, _: Step<'_>) {}

fn german_film(dir: &Path) -> Option<std::path::PathBuf> {
    make_mkv(
        dir,
        "film.mkv",
        &[Sub {
            srt: HALLO,
            codec: "subrip",
            lang: "ger",
            title: None,
            forced: false,
        }],
    )
}

/// A set planned as `add` plans one, uploading from `source`.
fn plan(state: &TempDir, source: &Path, remux: bool, original: Option<&Path>) -> Item<String> {
    let bytes = std::fs::metadata(source).unwrap().len();
    let mut conn = db::open(state.path()).unwrap();
    let set = SetRow::from_caption(&sample_caption(SET, bytes, 1), 1);
    let tx = conn.transaction().unwrap();
    sets::insert_set(&tx, &set).unwrap();
    parts::insert_parts(
        &tx,
        SET,
        &mlib_spec::plan_parts(bytes, 1024 * 1024).unwrap(),
    )
    .unwrap();
    lifecycle::record_source(&tx, SET, source, remux).unwrap();
    if let Some(original) = original {
        lifecycle::record_original(&tx, SET, original).unwrap();
    }
    tx.commit().unwrap();
    Item {
        tag: SET.to_string(),
        set: Set::Planned(SET.to_string()),
        delete_source: None,
    }
}

fn bundles_sent(channel: &FakeChannel) -> usize {
    channel.with(|c| {
        c.messages
            .iter()
            .filter(|m| m.caption.starts_with(SUBS_CAPTION_PREFIX))
            .count()
    })
}

async fn run(state: &TempDir, channel: &FakeChannel, item: Item<String>) -> Counts {
    let cfg = config_in(state.path());
    let (transport, connects) = (FakeTransport::new(), Cell::new(0));
    let mut session = Session::new(&cfg, FakeLink::new(&transport, channel, &connects)).unwrap();
    let counts = session.upload([item], quiet).await;
    session.end(true).await.unwrap();
    counts
}

/// The remux is deleted on completion and carries no sidecars; the bundle
/// comes from the file the person named, which `--delete-source` then removes.
#[tokio::test]
async fn a_remuxed_set_gets_its_subtitles_from_the_original_before_it_is_deleted() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let Some(original) = german_film(media.path()) else {
        return;
    };
    let remux = media.path().join("film.faststart.mp4");
    std::fs::write(&remux, vec![7u8; 64]).unwrap();
    let mut item = plan(&state, &remux, true, Some(&original));
    item.delete_source = Some(original.clone());
    let channel = FakeChannel::new();

    let counts = run(&state, &channel, item).await;

    assert_eq!(counts.uploaded, 1);
    assert!(!remux.exists(), "the remux is gone");
    assert!(!original.exists(), "the original is deleted after attach");
    assert_eq!(bundles_sent(&channel), 1);
    let conn = db::open(state.path()).unwrap();
    let message = subtitles::bundle_message(&conn, SET).unwrap().unwrap();
    let bundle = decode(&channel.document(message as i32)).unwrap();
    assert!(bundle.tracks[0].vtt.contains("Hallo Welt"));
    assert_eq!(lifecycle::original_of(&conn, SET).unwrap(), None);
}

/// With two upload slots, a second process can find the set complete while
/// the first still reads the subtitles from the original. It must not
/// delete the file then; once the first is done, it may.
#[tokio::test]
async fn a_source_is_not_deleted_while_another_upload_reads_its_subtitles() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let Some(video) = german_film(media.path()) else {
        return;
    };
    let item = plan(&state, &video, false, Some(&video));
    let conn = db::open(state.path()).unwrap();
    lifecycle::complete(&conn, SET, &"a".repeat(64)).unwrap();
    let again = |delete: &Path| Item {
        tag: SET.to_string(),
        set: Set::Planned(SET.to_string()),
        delete_source: Some(delete.to_path_buf()),
    };
    drop(item);
    let channel = FakeChannel::new();

    run(&state, &channel, again(&video)).await;
    assert!(video.exists(), "kept while the original is still recorded");

    lifecycle::forget_original(&conn, SET).unwrap();
    run(&state, &channel, again(&video)).await;
    assert!(!video.exists());
}

/// A set planned before the original was recorded uploads from it directly.
#[tokio::test]
async fn a_set_planned_without_an_original_falls_back_to_its_source() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let Some(video) = german_film(media.path()) else {
        return;
    };
    let item = plan(&state, &video, false, None);
    let channel = FakeChannel::new();

    run(&state, &channel, item).await;

    assert_eq!(bundles_sent(&channel), 1);
}

#[tokio::test]
async fn a_failing_attach_does_not_fail_the_upload() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let Some(video) = german_film(media.path()) else {
        return;
    };
    let item = plan(&state, &video, false, None);
    let channel = FakeChannel::new();
    channel.with(|c| c.send_fails = true);

    let counts = run(&state, &channel, item).await;

    assert_eq!(counts.uploaded, 1);
    let conn = db::open(state.path()).unwrap();
    assert_eq!(subtitles::bundle_message(&conn, SET).unwrap(), None);
    assert_eq!(
        sets::get_set(&conn, SET).unwrap().unwrap().status.as_str(),
        "complete"
    );
}

/// A document has no streams; even one that happens to be a video file is
/// not read for subtitles.
#[tokio::test]
async fn a_document_set_is_never_probed_for_subtitles() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let Some(file) = german_film(media.path()) else {
        return;
    };
    let item = Item {
        tag: SET.to_string(),
        set: Set::Document(Document {
            file,
            course: "Rust".into(),
            cid: "rust".into(),
            chapter: 1,
            chapter_title: None,
            path: None,
            number: 1,
            title: None,
            variant: None,
        }),
        delete_source: None,
    };
    let channel = FakeChannel::new();

    let counts = run(&state, &channel, item).await;

    assert_eq!(counts.uploaded, 1);
    assert_eq!(bundles_sent(&channel), 0);
}
