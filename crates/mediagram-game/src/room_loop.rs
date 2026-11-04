//! The task that owns the room: sockets send it events, it answers them.

use std::collections::HashMap;
use std::time::{Duration, Instant};

use tokio::sync::{mpsc, oneshot};
use tokio::time::MissedTickBehavior;

use crate::room::{ConnId, Room};

const TICK: Duration = Duration::from_millis(50);

pub(crate) enum Event {
    Connect(ConnId, bool, mpsc::UnboundedSender<String>),
    Text(ConnId, String),
    Disconnect(ConnId),
    Back(oneshot::Sender<bool>),
    Stop,
}

/// All room state lives here; sockets only forward frames, so there is no
/// locking and the room stays deterministic.
pub(crate) async fn room_loop(mut room: Room, mut rx: mpsc::UnboundedReceiver<Event>) {
    let start = Instant::now();
    let now = || start.elapsed().as_secs_f64() * 1000.0;
    let mut senders: HashMap<ConnId, mpsc::UnboundedSender<String>> = HashMap::new();
    let mut ticker = tokio::time::interval(TICK);
    ticker.set_missed_tick_behavior(MissedTickBehavior::Skip);

    loop {
        let replies = tokio::select! {
            ev = rx.recv() => match ev {
                None | Some(Event::Stop) => return,
                Some(Event::Connect(id, local, tx)) => {
                    senders.insert(id, tx);
                    room.connect(id, local);
                    Vec::new()
                }
                Some(Event::Text(id, text)) => room.handle(id, &text, now()),
                Some(Event::Disconnect(id)) => {
                    senders.remove(&id);
                    room.disconnect(id, now());
                    Vec::new()
                }
                Some(Event::Back(reply)) => {
                    let _ = reply.send(room.back_to_lobby());
                    Vec::new()
                }
            },
            _ = ticker.tick() => {
                room.tick(now());
                Vec::new()
            }
        };
        for (id, json) in replies.into_iter().chain(room.flush(now())) {
            if let Some(tx) = senders.get(&id) {
                let _ = tx.send(json);
            }
        }
    }
}
