//! Deciding what a removal destroys, before it destroys anything.
//!
//! This is the one command that loses data irrecoverably: the bytes live only
//! in the channel, and Telegram has no undelete. So the decision of what to
//! touch is separated from the touching, and is stated in full before
//! anything happens.

use mediagram::index::status::PartStatus;
use mediagram::index::parts::PartRow;
use mediagram::index::sets::SetRow;
use mediagram::remove::plan::{Removal, plan_removal};
use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;

fn row(set_id: &str, title: &str) -> SetRow {
    let caption = Caption {
        t: Kind::Ep,
        ids: ProviderIds {
            tmdb: Some(1),
            tvdb: None,
            imdb: None,
        },
        cid: None,
        show: Some("A Show".into()),
        chap: None,
        path: None,
        title: Some(title.into()),
        year: Some(2025),
        s: Some(1),
        e: Some(Episode::Single(1)),
        abs: None,
        q: None,
        hdr: None,
        container: "mkv".into(),
        vcodec: Some("h264".into()),
        acodec: Some("aac".into()),
        alang: vec![],
        slang: vec![],
        dur: Some(60),
        variant: None,
        set: set_id.into(),
        part: Part {
            i: 0,
            n: 2,
            off: 0,
            len: 10,
            sha256: "a".repeat(64),
        },
        total: 30,
    };
    SetRow::from_caption(&caption, 1).unwrap()
}

fn part(idx: u32, message: Option<i64>, len: u64) -> PartRow {
    PartRow {
        set_id: "01SET0000000000000000001".into(),
        idx,
        byte_offset: 0,
        byte_length: len,
        chat_id: message.map(|_| -1001),
        message_id: message,
        doc_id: message,
        sha256: Some("a".repeat(64)),
        status: if message.is_some() {
            PartStatus::Done
        } else {
            PartStatus::Pending
        },
        verified_at: None,
    }
}

#[test]
fn every_uploaded_message_is_listed_for_deletion() {
    let removal = plan_removal(
        &row("01SET0000000000000000001", "One"),
        &[part(0, Some(100), 10), part(1, Some(101), 20)],
    );

    assert_eq!(removal.message_ids, vec![100, 101]);
    assert_eq!(removal.bytes, 30);
}

/// A part never uploaded has nothing in the channel to delete; its row still
/// goes, but it must not be counted as reclaimed space.
#[test]
fn a_part_that_was_never_uploaded_contributes_no_message() {
    let removal = plan_removal(
        &row("01SET0000000000000000001", "One"),
        &[part(0, Some(100), 10), part(1, None, 20)],
    );

    assert_eq!(removal.message_ids, vec![100]);
    assert_eq!(removal.bytes, 10, "only what is actually in the channel");
}

#[test]
fn a_set_with_nothing_uploaded_deletes_no_messages() {
    let removal = plan_removal(
        &row("01SET0000000000000000001", "One"),
        &[part(0, None, 10)],
    );

    assert!(removal.message_ids.is_empty());
    assert_eq!(removal.bytes, 0);
}

/// What the operator is shown before confirming. A set id alone is not enough
/// to recognise what is about to be destroyed.
#[test]
fn the_summary_names_what_will_be_lost() {
    let removal = plan_removal(
        &row("01SET0000000000000000001", "Dominus"),
        &[part(0, Some(100), 10)],
    );

    let described = removal.describe();
    assert!(described.contains("Dominus"), "{described}");
    assert!(described.contains("A Show"), "{described}");
    assert!(
        described.contains("01SET0000000000000000001"),
        "{described}"
    );
    assert!(described.contains('1'), "the message count: {described}");
}

#[test]
fn message_ids_are_deduplicated_and_ordered() {
    let removal = Removal {
        set_id: "s".into(),
        label: "x".into(),
        message_ids: vec![101, 100, 101],
        bytes: 0,
    }
    .normalized();

    assert_eq!(removal.message_ids, vec![100, 101]);
}
