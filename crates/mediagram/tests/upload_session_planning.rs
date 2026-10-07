//! The upload session's planning round trip, in a process of its own.
//!
//! Planning runs ffprobe, and a spawned child holds a copy of every file the
//! test process has open until it execs — upload locks included. A sibling
//! test that released its lock in that instant would read it as still held
//! and, rightly, defer its publish to "the other upload". Kept apart so the
//! session tests beside it never share a process with a spawn.

mod support;

use std::cell::Cell;

use mediagram::upload::new_set::{LessonOf, NewSet};
use mediagram::upload::session::{Counts, Item, Session, Set, Step};
use mlib_spec::caption::Kind;
use support::channel::FakeChannel;
use support::session::{FakeLink, config_in};
use support::upload::FakeTransport;

fn quiet<T>(_: &T, _: Step<'_>) {}

/// Who an item is must be what its planning records, or a re-run uploads it
/// again: a lesson and a collection's episode are planned for real (ffprobe),
/// then found held by the same item.
#[tokio::test]
async fn a_planned_video_is_found_again_by_what_its_planning_recorded() {
    if !support::media::ffmpeg_required("upload session identity round trip") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let cfg = config_in(dir.path());
    let (transport, channel, connects) = (FakeTransport::new(), FakeChannel::new(), Cell::new(0));
    let video = support::media::make_faststart_mp4(dir.path());
    let item = |kind: Kind| Item {
        tag: (),
        set: Set::File(NewSet {
            file: video.clone(),
            no_remux: true,
            lesson: Some(LessonOf {
                course: "Walk".into(),
                cid: "walk".into(),
                chapter: Some(1),
                chapter_title: None,
                path: None,
                number: Some(1),
                kind,
            }),
            ..NewSet::default()
        }),
        delete_source: None,
    };

    for kind in [Kind::Tut, Kind::Docu] {
        let mut session =
            Session::new(&cfg, FakeLink::new(&transport, &channel, &connects)).unwrap();
        let first = session.upload([item(kind)], quiet).await;
        let again = session.upload([item(kind)], quiet).await;
        session.end(true).await.unwrap();

        assert_eq!(
            first,
            Counts {
                uploaded: 1,
                ..Counts::default()
            },
            "{kind}"
        );
        assert_eq!(
            again,
            Counts {
                held: 1,
                ..Counts::default()
            },
            "{kind}"
        );
    }
}
