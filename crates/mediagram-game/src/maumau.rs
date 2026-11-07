//! Mau-Mau (classic): the turn flow around the rules in `maumau_rules`.
//!
//! A turn: play a card that fits, or draw one (and play it if it fits). A seven
//! makes the next player draw two unless they add a seven, an eight skips the
//! next player, a jack wishes a suit. With the second-to-last card the player
//! says "Mau!" while playing it, else one penalty card is dealt. A player who
//! is gone plays by themselves after `auto_ms`.

use rand::rngs::StdRng;
use rand::SeedableRng;

use crate::cards::{Card, Rank, Suit, deck_from, shuffled};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::maumau_rules::{DEAL, SEVEN_DRAWS, Top, auto_pick, lowest_rank, most_held_suit, playable, sort_hand};
use crate::protocol::{
    Action, CardsBanner, CardsEvent, PhoneScreen, PlayerId, ServerTime, TvScene,
};
use crate::maumau_views::wish_id;
use crate::room_seats::Player;

#[derive(Debug, Clone)]
pub struct MauMauConfig {
    /// An absent player's move happens after this.
    pub auto_ms: f64,
    /// How long the banner keeps naming the last happening.
    pub banner_ms: f64,
    pub seed: Option<u64>,
}

impl Default for MauMauConfig {
    fn default() -> Self {
        Self { auto_ms: 1500.0, banner_ms: 2600.0, seed: None }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) enum Stage {
    Turn { since: ServerTime },
    /// Drew a card: play it if it fits, or keep it.
    Drawn { card: Card, since: ServerTime },
    /// Played a jack and picks the suit.
    Wish { since: ServerTime },
    Final { ranking: Vec<usize> },
}

pub struct MauMauGame {
    pub(crate) cfg: MauMauConfig,
    pub(crate) ids: Vec<PlayerId>,
    pub(crate) hands: Vec<Vec<Card>>,
    /// Draw pile, drawn from the end.
    pub(crate) deck: Vec<Card>,
    /// Discards, the top one last.
    pub(crate) discard: Vec<Card>,
    pub(crate) turn: usize,
    /// The suit that counts; `None` after a starting jack.
    pub(crate) suit: Option<Suit>,
    pub(crate) wish: Option<Suit>,
    /// Cards the next player must draw, built up by sevens.
    pub(crate) owed: u8,
    pub(crate) stage: Stage,
    pub(crate) events: Vec<CardsEvent>,
    pub(crate) banner: Option<(CardsBanner, ServerTime)>,
    /// A banner waiting for the one on show to be read, and when it may appear.
    pub(crate) queued: Option<(CardsBanner, ServerTime)>,
    pub(crate) next_event: u32,
    pub(crate) rng: StdRng,
}

impl MauMauGame {
    pub fn new(cfg: MauMauConfig, players: &[Player], now: ServerTime) -> Self {
        let mut rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let mut deck = shuffled(deck_from(lowest_rank(players.len())), &mut rng);
        let mut hands: Vec<Vec<Card>> = players.iter().map(|_| deck.split_off(deck.len() - DEAL)).collect();
        hands.iter_mut().for_each(|h| sort_hand(h));
        let start = deck.pop().expect("a deck holds more than the deal");
        let mut game = Self {
            cfg,
            ids: players.iter().map(|p| p.id).collect(),
            hands,
            deck,
            discard: vec![start],
            turn: 0,
            suit: (start.rank != Rank::Jack).then_some(start.suit),
            wish: None,
            owed: if start.rank == Rank::Seven { SEVEN_DRAWS } else { 0 },
            stage: Stage::Turn { since: now },
            events: Vec::new(),
            banner: None,
            queued: None,
            next_event: 0,
            rng,
        };
        if start.rank == Rank::Eight {
            game.advance(now, 1);
        }
        game
    }

    pub(crate) fn top(&self) -> Top {
        Top { card: *self.discard.last().expect("the discard pile is never empty"), suit: self.suit }
    }

    pub(crate) fn player_at(&self, seat: usize) -> PlayerId {
        self.ids[seat]
    }

    pub(crate) fn seat_of(&self, player: PlayerId) -> Option<usize> {
        self.ids.iter().position(|&p| p == player)
    }

}

impl Game for MauMauGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Cards { button, hand, .. } = action else {
            return Err(ActionError::Ignored);
        };
        if self.seat_of(player) != Some(self.turn) {
            return Err(ActionError::Ignored);
        }
        let pick = || -> Result<usize, ActionError> {
            let [i] = hand[..] else { return Err(ActionError::Ignored) };
            let i = usize::from(i);
            let card = *self.hands[self.turn].get(i).ok_or(ActionError::Ignored)?;
            match self.stage {
                Stage::Drawn { card: drawn, .. } if drawn != card => Err(ActionError::NotAllowed("Spiele die gezogene Karte oder behalte sie.")),
                _ if playable(self.top(), self.owed, card) => Ok(i),
                _ if self.owed > 0 => Err(ActionError::NotAllowed("Lege eine 7 oder zieh die Karten.")),
                _ => Err(ActionError::NotAllowed("Diese Karte passt nicht.")),
            }
        };
        match (&self.stage, button.as_str()) {
            (Stage::Turn { .. } | Stage::Drawn { .. }, "play" | "mau") => {
                let index = pick()?;
                self.play(ctx, index, button == "mau");
            }
            (Stage::Turn { .. }, "draw") => self.draw(ctx.now),
            (Stage::Drawn { .. }, "keep") => self.advance(ctx.now, 1),
            (Stage::Wish { .. }, id) => {
                let suit = Suit::ALL.into_iter().find(|&s| id == wish_id(s)).ok_or(ActionError::Ignored)?;
                self.choose_suit(ctx.now, suit);
            }
            _ => return Err(ActionError::Ignored),
        }
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let (Some(left), Some(since)) = (self.absent_since(ctx.players), self.since()) else { return };
        if ctx.now < left.max(since) + self.cfg.auto_ms {
            return;
        }
        match self.stage.clone() {
            Stage::Turn { .. } => match auto_pick(self.top(), self.owed, &self.hands[self.turn]) {
                Some(i) => self.play(ctx, i, true),
                None => self.draw(ctx.now),
            },
            Stage::Drawn { card, .. } if playable(self.top(), 0, card) => self.play(ctx, self.index_of(card), true),
            Stage::Drawn { .. } => self.advance(ctx.now, 1),
            Stage::Wish { .. } => self.choose_suit(ctx.now, most_held_suit(&self.hands[self.turn])),
            Stage::Final { .. } => {}
        }
    }

    fn phone_screen(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        self.phone_view(view, player)
    }

    fn tv_scene(&self, view: &ViewCtx) -> TvScene {
        self.tv_view(view)
    }

    fn is_finished(&self) -> bool {
        matches!(self.stage, Stage::Final { .. })
    }
}

#[cfg(test)]
#[path = "maumau_tests.rs"]
mod tests;
