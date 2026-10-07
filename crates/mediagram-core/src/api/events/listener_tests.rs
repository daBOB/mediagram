//! The listener against updates the test sends in Telegram's place, on a
//! paused clock: every window below ends the instant nothing else is due.

use grammers_client::client::UpdatesConfiguration;
use grammers_session::updates::UpdatesLike;
use grammers_tl_types::{self as tl, Deserializable, Serializable};
use tokio::sync::mpsc::{UnboundedSender, unbounded_channel};

use super::*;

const LIBRARY: i64 = 3816522481;
const ELSEWHERE: i64 = 1234567890;
const OWN: &str = "this-phone";

/// A listener on the core's own connection, reading what the returned
/// sender pushes instead of what Telegram would.
async fn listening(core: &Core) -> (UnboundedSender<UpdatesLike>, Listener) {
    let (client, handle) = session::connection(core).await;
    let (updates, receiver) = unbounded_channel();
    let configuration = UpdatesConfiguration {
        catch_up: false,
        update_queue_limit: Some(100),
    };
    let stream = client
        .stream_updates(receiver, configuration)
        .await
        .unwrap();
    (updates, Listener::new(stream, handle))
}

/// One update as Telegram pushes it. `pts` counts per channel from 1, as the
/// stream insists: a skipped number would read as a gap to fetch.
fn send(updates: &UnboundedSender<UpdatesLike>, update: tl::enums::Update) {
    let batch = tl::types::Updates {
        updates: vec![update],
        users: Vec::new(),
        chats: Vec::new(),
        date: 0,
        seq: 0,
    };
    updates.send(UpdatesLike::Updates(batch.into())).unwrap();
}

fn pin(channel: i64, pinned: bool, pts: i32) -> tl::enums::Update {
    tl::types::UpdatePinnedChannelMessages {
        pinned,
        channel_id: channel,
        messages: vec![pts],
        pts,
        pts_count: 1,
    }
    .into()
}

/// A new channel post carrying only its text. Read from Telegram's wire
/// form rather than written as the generated struct, whose four dozen
/// optional fields would bury the one that matters here: the caption.
fn post(channel: i64, caption: &str, pts: i32) -> tl::enums::Update {
    let mut wire = Vec::new();
    wire.extend(0x7600b9d3_u32.to_le_bytes()); // message, every flag clear
    wire.extend([0; 8]); // flags, flags2
    wire.extend(pts.to_le_bytes()); // id
    wire.extend(0xa2a5371e_u32.to_le_bytes()); // peerChannel
    wire.extend(channel.to_le_bytes());
    wire.extend(0_i32.to_le_bytes()); // date
    caption.to_owned().serialize(&mut wire);
    tl::types::UpdateNewChannelMessage {
        message: tl::enums::Message::from_bytes(&wire).unwrap(),
        pts,
        pts_count: 1,
    }
    .into()
}

fn assert_at_window_end(start: Instant) {
    let waited = start.elapsed();
    let window = Duration::from_millis(WINDOW_MS);
    assert!(
        waited >= window && waited < window + Duration::from_secs(1),
        "{waited:?}"
    );
}

/// Whether a minute passes with nothing to report.
async fn quiet(listener: &mut Listener) -> bool {
    tokio::time::timeout(Duration::from_secs(60), listener.wait(LIBRARY, OWN))
        .await
        .is_err()
}

#[tokio::test(start_paused = true)]
async fn a_burst_of_pins_is_one_index_event_when_its_window_ends() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let (updates, mut listener) = listening(&core).await;
    let start = Instant::now();
    for pts in 1..=3 {
        send(&updates, pin(LIBRARY, true, pts));
    }

    assert_eq!(
        listener.wait(LIBRARY, OWN).await.unwrap(),
        LibraryEvent::Index
    );
    assert_at_window_end(start);
    assert!(quiet(&mut listener).await, "the burst is released once");
}

/// The window runs from the first change, so an upload publishing every few
/// seconds for an hour still lets an event through after the first five.
#[tokio::test(start_paused = true)]
async fn changes_that_keep_coming_do_not_hold_the_event_back() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let (updates, mut listener) = listening(&core).await;
    let start = Instant::now();
    let publishing = tokio::spawn(async move {
        for pts in 1..=10 {
            send(&updates, pin(LIBRARY, true, pts));
            tokio::time::sleep(Duration::from_secs(2)).await;
        }
    });

    assert_eq!(
        listener.wait(LIBRARY, OWN).await.unwrap(),
        LibraryEvent::Index
    );
    assert_at_window_end(start);
    publishing.abort();
}

/// Both kinds fall due in one window; the second is already decided, so the
/// next wait hands it over without waiting at all.
#[tokio::test(start_paused = true)]
async fn another_devices_state_and_a_new_index_come_out_state_first() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let (updates, mut listener) = listening(&core).await;
    let start = Instant::now();
    send(&updates, pin(LIBRARY, true, 1));
    send(
        &updates,
        post(LIBRARY, "#mlib-state v=1 device=living-room-tv", 2),
    );

    assert_eq!(
        listener.wait(LIBRARY, OWN).await.unwrap(),
        LibraryEvent::State
    );
    assert_at_window_end(start);
    let released = Instant::now();
    assert_eq!(
        listener.wait(LIBRARY, OWN).await.unwrap(),
        LibraryEvent::Index
    );
    assert_eq!(released.elapsed(), Duration::ZERO);
}

#[tokio::test(start_paused = true)]
async fn changes_that_are_no_news_never_wake_the_listener() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let (updates, mut listener) = listening(&core).await;
    send(
        &updates,
        post(LIBRARY, "#mlib-state v=1 device=this-phone", 1),
    );
    send(&updates, pin(LIBRARY, false, 2));
    send(&updates, post(LIBRARY, "an ordinary post", 3));
    send(&updates, pin(ELSEWHERE, true, 1));

    assert!(quiet(&mut listener).await);

    send(&updates, pin(LIBRARY, true, 4));
    assert_eq!(
        listener.wait(LIBRARY, OWN).await.unwrap(),
        LibraryEvent::Index
    );
}

/// Opening a listener subscribes over the network, which no test reaches:
/// the event arriving at all shows the installed one was reused.
#[tokio::test(start_paused = true)]
async fn waiting_again_reuses_the_listener_on_the_current_connection() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let (updates, listener) = listening(&core).await;
    *core.events.lock().await = Some(listener);
    send(&updates, pin(LIBRARY, true, 1));

    assert_eq!(
        next(&core, LIBRARY, OWN).await.unwrap(),
        LibraryEvent::Index
    );
    assert!(core.events.lock().await.is_some());
}

#[tokio::test]
async fn an_event_keeps_the_listener_for_the_next_wait() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let (_updates, listener) = listening(&core).await;
    let handle = listener.handle.clone();
    let mut slot = Some(listener);

    let event = finish_wait(&core, &mut slot, Ok(LibraryEvent::State)).await;

    assert_eq!(event.unwrap(), LibraryEvent::State);
    assert!(slot.is_some());
    assert!(session::is_current(&core, &handle).await);
}
