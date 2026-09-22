//! Covers `once`/`take`/`give` against a channel in memory: when a device
//! sends, what it takes back, and what a broken or refusing channel costs.
//! A port of `web/test/state-sync.test.ts` and `web/test/
//! state-two-machines.test.ts`; only the grammers adapter is left to a live
//! account, which nothing here touches.

use std::cell::{Cell, RefCell};
use std::sync::atomic::{AtomicBool, Ordering};

use super::*;
use crate::state::{profiles, rows};

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn profile(db: &StateDb) -> String {
    db.with(|conn| profiles::create(conn, "André")).unwrap().unwrap().id
}

/// A channel in memory, counting what it was asked to do — the Rust twin of
/// the web test's `fakeChannel`.
struct FakeChannel {
    next_id: Cell<i32>,
    held: RefCell<Vec<ChannelDocument>>,
    puts: RefCell<Vec<(String, Option<i32>)>>,
}

impl FakeChannel {
    fn new(held: Vec<ChannelDocument>) -> Self {
        FakeChannel { next_id: Cell::new(100), held: RefCell::new(held), puts: RefCell::new(Vec::new()) }
    }
}

impl StateChannel for FakeChannel {
    type Error = String;

    async fn list(&self) -> Result<Vec<ChannelDocument>, String> {
        Ok(self.held.borrow().clone())
    }

    async fn put(&self, body: String, message_id: Option<i32>) -> Result<i32, String> {
        self.puts.borrow_mut().push((body.clone(), message_id));
        if let Some(id) = message_id {
            if let Some(held) = self.held.borrow_mut().iter_mut().find(|d| d.message_id == id) {
                held.text = body;
            }
            return Ok(id);
        }
        let made = self.next_id.get() + 1;
        self.next_id.set(made);
        let device = parse_record(&body).map(|record| record.device).unwrap_or_default();
        self.held.borrow_mut().push(ChannelDocument { message_id: made, device, text: body });
        Ok(made)
    }
}

/// A channel whose `list` never answers.
struct BrokenList;
impl StateChannel for BrokenList {
    type Error = String;
    async fn list(&self) -> Result<Vec<ChannelDocument>, String> {
        Err("no network".into())
    }
    async fn put(&self, _body: String, _message_id: Option<i32>) -> Result<i32, String> {
        Ok(0)
    }
}

/// A channel that lists fine but always refuses a send.
struct RefusedPut;
impl StateChannel for RefusedPut {
    type Error = String;
    async fn list(&self) -> Result<Vec<ChannelDocument>, String> {
        Ok(Vec::new())
    }
    async fn put(&self, _body: String, _message_id: Option<i32>) -> Result<i32, String> {
        Err("upload refused".into())
    }
}

/// Refuses exactly once, then behaves like [`FakeChannel`].
struct FlakyPut {
    refused: Cell<bool>,
    inner: FakeChannel,
}
impl StateChannel for FlakyPut {
    type Error = String;
    async fn list(&self) -> Result<Vec<ChannelDocument>, String> {
        self.inner.list().await
    }
    async fn put(&self, body: String, message_id: Option<i32>) -> Result<i32, String> {
        if self.refused.replace(false) {
            return Err("upload refused".into());
        }
        self.inner.put(body, message_id).await
    }
}

/// Gates its first `list` call until released, so two rounds asked for at
/// once can be made to overlap in a test rather than trusted to by luck.
struct GatedFirstList {
    gated: AtomicBool,
    gate: tokio::sync::Notify,
    inner: FakeChannel,
}
impl StateChannel for GatedFirstList {
    type Error = String;
    async fn list(&self) -> Result<Vec<ChannelDocument>, String> {
        if !self.gated.swap(true, Ordering::SeqCst) {
            self.gate.notified().await;
        }
        self.inner.list().await
    }
    async fn put(&self, body: String, message_id: Option<i32>) -> Result<i32, String> {
        self.inner.put(body, message_id).await
    }
}

mod a_round_of_sync {
    use super::*;

    #[tokio::test]
    async fn sends_this_devices_document_when_it_has_never_written_one() {
        let (_dir, db) = db();
        let id = profile(&db);
        db.with(|conn| rows::set_progress(conn, &id, "01A", 742.0, None)).unwrap();
        let channel = FakeChannel::new(Vec::new());
        let mut memo = SyncMemo::default();

        let outcome = once(&db, &channel, "laptop", memo.entry("handle")).await;

        assert!(outcome.pushed);
        let puts = channel.puts.borrow();
        assert_eq!(puts[0].1, None);
        assert_eq!(parse_record(&puts[0].0).unwrap().device, "laptop");
    }

    #[tokio::test]
    async fn edits_its_own_message_rather_than_sending_a_second() {
        // A device writes one message, for ever. A second would be a second
        // opinion nobody asked for and nothing would ever clean it up.
        let (_dir, db) = db();
        let id = profile(&db);
        let channel = FakeChannel::new(Vec::new());
        let mut memo = SyncMemo::default();

        db.with(|conn| rows::set_progress(conn, &id, "01A", 100.0, None)).unwrap();
        once(&db, &channel, "laptop", memo.entry("handle")).await;
        db.with(|conn| rows::set_progress(conn, &id, "01A", 200.0, None)).unwrap();
        once(&db, &channel, "laptop", memo.entry("handle")).await;

        let puts = channel.puts.borrow();
        assert_eq!(puts.len(), 2);
        assert_eq!(puts[0].1, None);
        assert_eq!(puts[1].1, Some(101));
    }

    #[tokio::test]
    async fn finds_its_own_message_again_after_a_restart() {
        // The id lives on the channel, not in this process — a fresh
        // `SyncMemo` stands in for a restarted app.
        let (_dir, db) = db();
        let id = profile(&db);
        db.with(|conn| rows::set_progress(conn, &id, "01A", 100.0, None)).unwrap();
        let channel = FakeChannel::new(Vec::new());
        once(&db, &channel, "laptop", SyncMemo::default().entry("handle")).await;

        db.with(|conn| rows::set_progress(conn, &id, "01A", 300.0, None)).unwrap();
        once(&db, &channel, "laptop", SyncMemo::default().entry("handle")).await;

        assert_eq!(channel.puts.borrow()[1].1, Some(101));
    }
}

mod not_sending_the_same_thing_twice {
    use super::*;

    #[tokio::test]
    async fn a_second_round_with_nothing_new_sends_nothing() {
        let (_dir, db) = db();
        let id = profile(&db);
        db.with(|conn| rows::set_progress(conn, &id, "01A", 742.0, None)).unwrap();
        let channel = FakeChannel::new(Vec::new());
        let mut memo = SyncMemo::default();

        once(&db, &channel, "laptop", memo.entry("handle")).await;
        let again = once(&db, &channel, "laptop", memo.entry("handle")).await;

        assert!(!again.pushed);
        assert_eq!(channel.puts.borrow().len(), 1);
    }

    #[tokio::test]
    async fn even_though_every_export_carries_a_fresh_written_at() {
        // Left out of the comparison on purpose, or a player left running
        // uploads an identical document every round for ever.
        let (_dir, db) = db();
        let _id = profile(&db);
        let channel = FakeChannel::new(Vec::new());
        let mut memo = SyncMemo::default();

        once(&db, &channel, "laptop", memo.entry("handle")).await;
        once(&db, &channel, "laptop", memo.entry("handle")).await;
        once(&db, &channel, "laptop", memo.entry("handle")).await;

        assert_eq!(channel.puts.borrow().len(), 1);
    }
}

mod what_comes_back {
    use super::*;

    #[tokio::test]
    async fn another_machines_position_arrives() {
        let (_odir, other) = db();
        let other_id = profile(&other);
        other.with(|conn| rows::set_progress(conn, &other_id, "01FILM", 900.0, None)).unwrap();
        let other_record = other.with(|conn| exchange::export_record(conn, "desktop")).unwrap();
        let channel = FakeChannel::new(vec![ChannelDocument {
            message_id: 7,
            device: "desktop".into(),
            text: serde_json::to_string(&other_record).unwrap(),
        }]);

        let (_dir, here) = db();
        let here_id = profile(&here);
        let outcome = once(&here, &channel, "laptop", SyncMemo::default().entry("handle")).await;

        assert!(outcome.pulled > 0);
        let progress = here.with(|conn| rows::progress_for(conn, &here_id)).unwrap();
        assert_eq!(progress[0].set_id, "01FILM");
        assert_eq!(progress[0].at, 900.0);
    }

    #[tokio::test]
    async fn a_document_that_cannot_be_read_is_skipped_not_fatal() {
        let (_gdir, good) = db();
        let good_id = profile(&good);
        good.with(|conn| rows::set_progress(conn, &good_id, "01FILM", 900.0, None)).unwrap();
        let good_record = good.with(|conn| exchange::export_record(conn, "desktop")).unwrap();
        let channel = FakeChannel::new(vec![
            ChannelDocument { message_id: 6, device: "junk".into(), text: "{{{ not json".into() },
            ChannelDocument {
                message_id: 7,
                device: "desktop".into(),
                text: serde_json::to_string(&good_record).unwrap(),
            },
        ]);

        let (_dir, here) = db();
        let here_id = profile(&here);
        once(&here, &channel, "laptop", SyncMemo::default().entry("handle")).await;

        let progress = here.with(|conn| rows::progress_for(conn, &here_id)).unwrap();
        assert_eq!(progress[0].set_id, "01FILM");
    }

    #[tokio::test]
    async fn this_devices_own_document_is_not_merged_back_in_as_a_strangers() {
        // It is already in the merge, from the local database. Parsing it
        // again would be harmless but would hide a device id that never
        // matched anything real.
        let (_dir, db) = db();
        let id = profile(&db);
        db.with(|conn| rows::set_progress(conn, &id, "01A", 742.0, None)).unwrap();
        let channel = FakeChannel::new(Vec::new());
        let mut memo = SyncMemo::default();
        once(&db, &channel, "laptop", memo.entry("handle")).await;

        let second = once(&db, &channel, "laptop", memo.entry("handle")).await;
        assert_eq!(second.pulled, 0);
    }
}

mod a_channel_that_cannot_be_reached {
    use super::*;

    #[tokio::test]
    async fn costs_a_message_and_nothing_else() {
        let (_dir, db) = db();
        let id = profile(&db);
        db.with(|conn| rows::set_progress(conn, &id, "01A", 742.0, None)).unwrap();

        let outcome = once(&db, &BrokenList, "laptop", SyncMemo::default().entry("handle")).await;

        assert_eq!(outcome.failed.as_deref(), Some("no network"));
        assert_eq!(outcome.pulled, 0);
        // The player still knows everything it knew.
        let progress = db.with(|conn| rows::progress_for(conn, &id)).unwrap();
        assert_eq!(progress[0].at, 742.0);
    }

    #[tokio::test]
    async fn a_send_that_fails_does_not_pretend_to_have_worked() {
        let (_dir, db) = db();
        let id = profile(&db);
        db.with(|conn| rows::set_progress(conn, &id, "01A", 742.0, None)).unwrap();

        let outcome = once(&db, &RefusedPut, "laptop", SyncMemo::default().entry("handle")).await;

        assert!(!outcome.pushed);
        assert_eq!(outcome.failed.as_deref(), Some("upload refused"));
    }

    #[tokio::test]
    async fn so_the_next_round_tries_again_rather_than_believing_it_is_in_sync() {
        let (_dir, db) = db();
        let id = profile(&db);
        db.with(|conn| rows::set_progress(conn, &id, "01A", 742.0, None)).unwrap();
        let channel = FlakyPut { refused: Cell::new(true), inner: FakeChannel::new(Vec::new()) };
        let mut memo = SyncMemo::default();

        once(&db, &channel, "laptop", memo.entry("handle")).await;
        let second = once(&db, &channel, "laptop", memo.entry("handle")).await;

        assert!(second.pushed);
        assert_eq!(channel.inner.puts.borrow().len(), 1);
    }
}

/// Rounds never overlap in production because `Core` holds one
/// `tokio::sync::Mutex<SyncMemo>` for the whole round. This proves the
/// pattern itself: two rounds racing for the same mutex-guarded memo run one
/// after the other, so a first send happens exactly once even when the
/// channel is slow to answer.
#[tokio::test]
async fn two_rounds_guarded_by_one_mutex_run_one_after_the_other() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_progress(conn, &id, "01A", 742.0, None)).unwrap();
    let channel =
        GatedFirstList { gated: AtomicBool::new(false), gate: tokio::sync::Notify::new(), inner: FakeChannel::new(Vec::new()) };
    let memo = tokio::sync::Mutex::new(SyncMemo::default());

    let first = async {
        let mut guard = memo.lock().await;
        once(&db, &channel, "laptop", guard.entry("handle")).await
    };
    let second = async {
        let mut guard = memo.lock().await;
        once(&db, &channel, "laptop", guard.entry("handle")).await
    };
    let release = async {
        tokio::time::sleep(std::time::Duration::from_millis(20)).await;
        channel.gate.notify_waiters();
    };

    tokio::join!(first, second, release);

    let sent: Vec<Option<i32>> = channel.inner.puts.borrow().iter().map(|(_, id)| *id).collect();
    assert_eq!(sent, vec![None], "a first send must happen exactly once");
}

/// Two machines, one channel — the shape of `state-two-machines.test.ts`,
/// ported onto `once` instead of calling `mergeStates` by hand: the whole
/// point is that transport is the only thing `once` adds on top of it.
mod two_machines_one_channel {
    use super::*;

    #[tokio::test]
    async fn a_position_set_on_one_machine_shows_up_on_the_other() {
        let (_ldir, laptop) = db();
        let laptop_id = profile(&laptop);
        let (_ddir, desktop) = db();
        let desktop_id = profile(&desktop);
        let channel = FakeChannel::new(Vec::new());

        laptop.with(|conn| rows::set_progress(conn, &laptop_id, "01FILM", 742.0, None)).unwrap();
        once(&laptop, &channel, "laptop", SyncMemo::default().entry("h")).await;
        once(&desktop, &channel, "desktop", SyncMemo::default().entry("h")).await;

        let progress = desktop.with(|conn| rows::progress_for(conn, &desktop_id)).unwrap();
        assert_eq!(progress[0].set_id, "01FILM");
        assert_eq!(progress[0].at, 742.0);
    }

    #[tokio::test]
    async fn two_titles_on_two_machines_both_survive_on_both() {
        let (_ldir, laptop) = db();
        let laptop_id = profile(&laptop);
        let (_pdir, phone) = db();
        let phone_id = profile(&phone);
        let channel = FakeChannel::new(Vec::new());

        laptop.with(|conn| rows::set_progress(conn, &laptop_id, "01E9", 700.0, None)).unwrap();
        once(&laptop, &channel, "laptop", SyncMemo::default().entry("h")).await;
        phone.with(|conn| rows::set_progress(conn, &phone_id, "01E4", 300.0, None)).unwrap();
        once(&phone, &channel, "phone", SyncMemo::default().entry("h")).await;
        // A second round on each takes in what the other just sent.
        once(&laptop, &channel, "laptop", SyncMemo::default().entry("h")).await;
        once(&phone, &channel, "phone", SyncMemo::default().entry("h")).await;

        for (state, id) in [(&laptop, &laptop_id), (&phone, &phone_id)] {
            let seen: std::collections::HashMap<String, f64> = state
                .with(|conn| rows::progress_for(conn, id))
                .unwrap()
                .into_iter()
                .map(|row| (row.set_id, row.at))
                .collect();
            assert_eq!(seen.get("01E4"), Some(&300.0));
            assert_eq!(seen.get("01E9"), Some(&700.0));
        }
    }

    #[tokio::test]
    async fn finishing_on_one_machine_takes_it_off_the_others_continue_shelf() {
        let (_ldir, laptop) = db();
        let laptop_id = profile(&laptop);
        let (_ddir, desktop) = db();
        let desktop_id = profile(&desktop);
        let channel = FakeChannel::new(Vec::new());

        laptop.with(|conn| rows::set_progress(conn, &laptop_id, "01FILM", 900.0, None)).unwrap();
        once(&laptop, &channel, "laptop", SyncMemo::default().entry("h")).await;
        once(&desktop, &channel, "desktop", SyncMemo::default().entry("h")).await;
        assert_eq!(desktop.with(|conn| rows::progress_for(conn, &desktop_id)).unwrap().len(), 1);

        // What the player does at the end of a title: both, together.
        laptop.with(|conn| rows::clear_progress(conn, &laptop_id, "01FILM")).unwrap();
        laptop.with(|conn| rows::set_watched(conn, &laptop_id, "01FILM", true)).unwrap();
        once(&laptop, &channel, "laptop", SyncMemo::default().entry("h")).await;
        once(&desktop, &channel, "desktop", SyncMemo::default().entry("h")).await;

        assert_eq!(desktop.with(|conn| rows::progress_for(conn, &desktop_id)).unwrap(), Vec::new());
        let watched = desktop.with(|conn| rows::watched_for(conn, &desktop_id)).unwrap();
        assert_eq!(watched.iter().map(|row| row.set_id.as_str()).collect::<Vec<_>>(), vec!["01FILM"]);
    }

    #[tokio::test]
    async fn a_machine_joining_late_gets_the_history_it_never_had_profile_and_all() {
        let (_ldir, laptop) = db();
        let laptop_id = profile(&laptop);
        laptop.with(|conn| rows::set_progress(conn, &laptop_id, "01A", 100.0, None)).unwrap();
        laptop.with(|conn| rows::set_watched(conn, &laptop_id, "01B", true)).unwrap();
        let channel = FakeChannel::new(Vec::new());
        once(&laptop, &channel, "laptop", SyncMemo::default().entry("h")).await;

        let (_fdir, fresh) = db();
        assert_eq!(fresh.with(profiles::list).unwrap(), Vec::new());
        once(&fresh, &channel, "fresh", SyncMemo::default().entry("h")).await;

        let them = fresh.with(profiles::list).unwrap();
        assert_eq!(them.len(), 1);
        assert_eq!(them[0].name, "André");
        let progress = fresh.with(|conn| rows::progress_for(conn, &them[0].id)).unwrap();
        assert_eq!(progress[0].set_id, "01A");
        let watched = fresh.with(|conn| rows::watched_for(conn, &them[0].id)).unwrap();
        assert_eq!(watched.iter().map(|row| row.set_id.as_str()).collect::<Vec<_>>(), vec!["01B"]);
    }
}

/// The document a round sends is what the web reads: `parse_record`, pinned
/// to `sync-record.ts`'s parser by the shared fixtures, has to make sense of
/// exactly these bytes, in exactly these keys.
mod the_body_a_round_sends {
    use super::*;

    #[tokio::test]
    async fn round_trips_through_parse_record_byte_for_byte() {
        let (_dir, db) = db();
        let id = profile(&db);
        db.with(|conn| rows::set_progress(conn, &id, "01A", 742.0, Some(1204.0))).unwrap();
        db.with(|conn| rows::set_watched(conn, &id, "01B", true)).unwrap();
        let channel = FakeChannel::new(Vec::new());

        once(&db, &channel, "laptop", SyncMemo::default().entry("h")).await;

        let body = channel.puts.borrow()[0].0.clone();
        let parsed = parse_record(&body).expect("the web's own parser must accept this document");
        // The whole point of the format: re-serialising what was parsed
        // reproduces the exact bytes sent, so nothing on the wire is lost
        // or reshaped by a round trip through `parse_record`.
        assert_eq!(serde_json::to_string(&parsed).unwrap(), body);
    }

    #[tokio::test]
    async fn uses_the_camelcase_keys_sync_record_ts_writes() {
        let (_dir, db) = db();
        let id = profile(&db);
        db.with(|conn| rows::set_progress(conn, &id, "01A", 742.0, Some(1204.0))).unwrap();
        db.with(|conn| rows::set_watched(conn, &id, "01B", true)).unwrap();
        let channel = FakeChannel::new(Vec::new());

        once(&db, &channel, "laptop", SyncMemo::default().entry("h")).await;

        let body = channel.puts.borrow()[0].0.clone();
        let value: serde_json::Value = serde_json::from_str(&body).unwrap();
        let top = value.as_object().unwrap();
        for key in ["format", "device", "writtenAt", "profiles"] {
            assert!(top.contains_key(key), "missing top-level key {key}");
        }
        let profile = top["profiles"].as_array().unwrap()[0].as_object().unwrap();
        for key in ["name", "localId", "progress", "watched"] {
            assert!(profile.contains_key(key), "missing profile key {key}");
        }
        let progress_row = profile["progress"].as_array().unwrap()[0].as_object().unwrap();
        for key in ["setId", "at", "duration", "updatedAt"] {
            assert!(progress_row.contains_key(key), "missing progress key {key}");
        }
        let watched_row = profile["watched"].as_array().unwrap()[0].as_object().unwrap();
        for key in ["setId", "updatedAt"] {
            assert!(watched_row.contains_key(key), "missing watched key {key}");
        }
    }
}

mod the_device_id {
    use super::*;

    #[test]
    fn is_made_once_and_kept() {
        let (_dir, db) = db();
        let first = db.with(device_id).unwrap();
        let second = db.with(device_id).unwrap();
        assert_eq!(first, second);
        assert!(!first.is_empty());
    }

    #[test]
    fn is_not_the_hostname() {
        let (_dir, db) = db();
        let made = db.with(device_id).unwrap();
        if let Ok(host) = std::env::var("HOSTNAME") {
            assert_ne!(made, host);
        }
    }
}
