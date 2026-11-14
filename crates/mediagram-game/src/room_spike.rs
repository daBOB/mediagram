//! The finger-to-TV latency spike: a phone's test strokes go straight to every TV, untouched
//! by the room or a game, so the TV can measure how late they arrive.

use crate::protocol::{Role, ServerMsg, ServerTime};
use crate::room::{Outbox, Room, encode};

impl Room {
    /// The spike's points, as sent, to every TV.
    pub(crate) fn relay_spike_ink(&self, points: Vec<u16>, at: ServerTime) -> Outbox {
        let text = encode(&ServerMsg::SpikeInk { points, at });
        self.conns.iter().filter(|(_, c)| c.role == Some(Role::Tv)).map(|(id, _)| (*id, text.clone())).collect()
    }
}
