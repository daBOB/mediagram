//! The card table every card game sits at: who sits in which seat, the events
//! the TV animates and the banner naming the last happening. Whose turn it is
//! stays with each game, since Durak's actor comes from its stage, not a rota.

use crate::ask::Ask;
use crate::cards::Card;
use crate::protocol::{ButtonNeeds, CardsBanner, CardsEvent, CardsEventKind, DeskButton, PlayerId, ServerTime};
use crate::room_seats::Player;

/// The TV plays each event once, by id; six covers one Durak refill dealing to every seat.
const EVENTS_KEPT: usize = 6;

pub(crate) struct CardTable {
    ids: Vec<PlayerId>,
    /// How long a banner stays up once said.
    banner_ms: ServerTime,
    events: Vec<CardsEvent>,
    next_event: u32,
    banner: Option<(CardsBanner, ServerTime)>,
    /// A banner waiting for the one on show to be read, and when it may appear.
    queued: Option<(CardsBanner, ServerTime)>,
}

impl CardTable {
    /// Seats `players` in joining order.
    pub(crate) fn new(players: &[Player], banner_ms: ServerTime) -> Self {
        Self { ids: players.iter().map(|p| p.id).collect(), banner_ms, events: Vec::new(), next_event: 0, banner: None, queued: None }
    }

    /// Everyone at the table, in seat order.
    pub(crate) fn players(&self) -> &[PlayerId] {
        &self.ids
    }

    pub(crate) fn player_at(&self, seat: usize) -> PlayerId {
        self.ids[seat]
    }

    pub(crate) fn seat_of(&self, player: PlayerId) -> Option<usize> {
        self.ids.iter().position(|&p| p == player)
    }

    /// Asks `seat` from `now`, with no clock: the table waits for a player who is there.
    pub(crate) fn ask(&self, seat: usize, now: ServerTime) -> Ask {
        Ask::open(self.ids[seat], now, None)
    }

    pub(crate) fn event(&mut self, now: ServerTime, player: PlayerId, kind: CardsEventKind) {
        self.events.push(CardsEvent { id: self.next_event, at: now, player, kind });
        self.next_event += 1;
        if self.events.len() > EVENTS_KEPT {
            self.events.remove(0);
        }
    }

    /// The newest events, oldest first.
    pub(crate) fn events(&self) -> &[CardsEvent] {
        &self.events
    }

    /// Names a happening at once, replacing the banner on show and any waiting behind it.
    pub(crate) fn say(&mut self, now: ServerTime, player: PlayerId, text: impl Into<String>) {
        self.banner = Some((CardsBanner { player, text: text.into() }, now));
        self.queued = None;
    }

    /// Names a happening once the banner on show has been up its full time; at once when none is up.
    pub(crate) fn say_next(&mut self, now: ServerTime, player: PlayerId, text: impl Into<String>) {
        match self.banner {
            Some((_, at)) if now < at + self.banner_ms => {
                self.queued = Some((CardsBanner { player, text: text.into() }, at + self.banner_ms));
            }
            _ => self.say(now, player, text),
        }
    }

    /// The banner said last, while it is still up.
    pub(crate) fn said(&self, now: ServerTime) -> Option<&CardsBanner> {
        self.banner.as_ref().filter(|(_, at)| now < at + self.banner_ms).map(|(b, _)| b)
    }

    /// What the TV's banner shows at `now`: a waiting one once its time has come, else the one
    /// said last, while it is up or `hold` keeps it; `None` once it has been read.
    pub(crate) fn banner(&self, now: ServerTime, hold: bool) -> Option<CardsBanner> {
        let shown = match &self.queued {
            Some((b, from)) if now >= *from => Some((b, *from)),
            _ => self.banner.as_ref().map(|(b, at)| (b, *at)),
        };
        shown.filter(|&(_, at)| hold || now < at + self.banner_ms).map(|(b, _)| b.clone())
    }
}

/// Hands are kept in suit and rank order, which is also the order the phone shows.
pub(crate) fn sort_hand(hand: &mut [Card]) {
    hand.sort();
}

pub(crate) fn button(id: &str, label: &str, needs: ButtonNeeds, primary: bool) -> DeskButton {
    DeskButton { id: id.into(), label: label.into(), needs, primary, suit: None }
}

#[cfg(test)]
#[path = "card_table_tests.rs"]
mod tests;
