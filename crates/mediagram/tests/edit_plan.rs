//! Rewriting a set's captions after its metadata is corrected.
//!
//! Every part of a set carries the whole record and differs only in `part`,
//! so correcting a title means rewriting one message per part. What must not
//! change is anything describing the bytes: the set id, the part geometry and
//! the hashes are what `verify` checks and what a player seeks with. An edit
//! that touched them would turn a correction into corruption.

use mediagram::edit::plan::{Edits, apply, captions};
use mediagram::index::parts::PartRow;
use mediagram::index::sets::SetRow;
use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;

fn episode_caption() -> Caption {
    Caption {
        t: Kind::Ep,
        ids: ProviderIds {
            tmdb: Some(240459),
            tvdb: None,
            imdb: None,
        },
        cid: None,
        show: Some("Spartacus: House of Ashur".into()),
        chap: None,
        path: None,
        title: Some("Forsaken".into()),
        year: Some(2025),
        s: Some(1),
        e: Some(Episode::Single(2)),
        abs: None,
        q: Some("1080p".into()),
        hdr: None,
        container: "mkv".into(),
        vcodec: Some("h264".into()),
        acodec: Some("aac".into()),
        alang: vec!["deu".into()],
        slang: vec![],
        dur: Some(3000),
        variant: None,
        set: "01SET0000000000000000001".into(),
        part: Part {
            i: 0,
            n: 2,
            off: 0,
            len: 1000,
            sha256: "a".repeat(64),
        },
        total: 3000,
    }
}

fn row() -> SetRow {
    SetRow::from_caption(&episode_caption(), 1_700_000_000).unwrap()
}

fn two_parts() -> Vec<PartRow> {
    vec![
        PartRow {
            set_id: "01SET0000000000000000001".into(),
            idx: 0,
            byte_offset: 0,
            byte_length: 1000,
            chat_id: Some(-1001),
            message_id: Some(100),
            doc_id: Some(900),
            sha256: Some("a".repeat(64)),
            status: "done".into(),
            verified_at: None,
        },
        PartRow {
            set_id: "01SET0000000000000000001".into(),
            idx: 1,
            byte_offset: 1000,
            byte_length: 2000,
            chat_id: Some(-1001),
            message_id: Some(101),
            doc_id: Some(901),
            sha256: Some("b".repeat(64)),
            status: "done".into(),
            verified_at: None,
        },
    ]
}

#[test]
fn a_corrected_title_reaches_the_row() {
    let edited = apply(
        &row(),
        &Edits {
            title: Some("Verlassen".into()),
            ..Edits::default()
        },
    );

    assert_eq!(edited.title.as_deref(), Some("Verlassen"));
    assert_eq!(
        edited.show.as_deref(),
        Some("Spartacus: House of Ashur"),
        "untouched"
    );
}

#[test]
fn an_edit_that_changes_nothing_leaves_the_row_alone() {
    assert_eq!(apply(&row(), &Edits::default()), row());
}

#[test]
fn every_field_a_person_would_want_to_fix_can_be_fixed() {
    let edited = apply(
        &row(),
        &Edits {
            title: Some("Verlassen".into()),
            show: Some("Spartacus: Das Haus Ashur".into()),
            year: Some(2026),
            season: Some(2),
            episode: Some(7),
            chap: Some("Kapitel".into()),
            path: Some("Staffel 1".into()),
        },
    );

    assert_eq!(edited.title.as_deref(), Some("Verlassen"));
    assert_eq!(edited.show.as_deref(), Some("Spartacus: Das Haus Ashur"));
    assert_eq!(edited.year, Some(2026));
    assert_eq!(edited.season, Some(2));
    assert_eq!(edited.episode.as_deref(), Some("7"));
    assert_eq!(edited.chap.as_deref(), Some("Kapitel"));
    assert_eq!(edited.path.as_deref(), Some("Staffel 1"));
}

/// The bytes are not up for editing. These identify them.
#[test]
fn nothing_that_describes_the_bytes_can_be_edited() {
    let before = row();
    let after = apply(
        &before,
        &Edits {
            title: Some("whatever".into()),
            ..Edits::default()
        },
    );

    assert_eq!(after.set_id, before.set_id);
    assert_eq!(after.total, before.total);
    assert_eq!(after.part_count, before.part_count);
    assert_eq!(after.set_hash, before.set_hash);
    assert_eq!(after.container, before.container);
}

#[test]
fn one_caption_is_produced_for_each_part_that_has_a_message() {
    let edited = apply(
        &row(),
        &Edits {
            title: Some("Verlassen".into()),
            ..Edits::default()
        },
    );

    let written = captions(&edited, &two_parts()).unwrap();

    assert_eq!(written.len(), 2);
    assert_eq!(written[0].message_id, 100);
    assert_eq!(written[1].message_id, 101);
    assert!(written.iter().all(|w| w.text.contains("Verlassen")));
}

/// Each part's caption describes that part. Copying part 0's record onto
/// part 1 would make `verify` reject bytes that are perfectly good.
#[test]
fn each_caption_keeps_its_own_parts_geometry() {
    let written = captions(&row(), &two_parts()).unwrap();

    let first = mlib_spec::caption_codec::parse(&written[0].text).unwrap();
    let second = mlib_spec::caption_codec::parse(&written[1].text).unwrap();

    assert_eq!((first.part.i, first.part.off, first.part.len), (0, 0, 1000));
    assert_eq!(
        (second.part.i, second.part.off, second.part.len),
        (1, 1000, 2000)
    );
    assert_eq!(first.part.sha256, "a".repeat(64));
    assert_eq!(second.part.sha256, "b".repeat(64));
    assert_eq!(first.part.n, 2);
}

/// A part never uploaded has no message to edit; it is not an error, it is
/// simply not part of this job.
#[test]
fn a_part_with_no_message_is_skipped() {
    let mut parts = two_parts();
    parts[1].message_id = None;

    let written = captions(&row(), &parts).unwrap();

    assert_eq!(written.len(), 1);
    assert_eq!(written[0].message_id, 100);
}

/// A caption has a hard budget. An edit that overflows it must fail before
/// anything is written, not halfway through a set.
#[test]
fn an_edit_that_would_overflow_the_caption_budget_is_refused() {
    let edited = apply(
        &row(),
        &Edits {
            title: Some("x".repeat(2000)),
            ..Edits::default()
        },
    );

    assert!(captions(&edited, &two_parts()).is_err());
}
