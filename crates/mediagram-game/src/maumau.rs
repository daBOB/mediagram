//! Mau-Mau (classic): the turn flow around the rules in `maumau_rules`.
//!
//! A turn: play a card that fits, or draw one (and play it if it fits). A seven
//! makes the next player draw two unless they add a seven, an eight skips the
//! next player, a jack wishes a suit. With the second-to-last card the player
//! says "Mau!" while playing it, else one penalty card is dealt. The house
//! plays for a player who is gone.

use rand::rngs::StdRng;
use rand::SeedableRng;

use crate::ask::Ask;
use crate::card_table::{CardTable, sort_hand};
use crate::cards::{Card, Rank, Suit, deck_from, shuffled};
use crate::catalogue::{CatalogueEntry, Kids, Need, Seats};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::maumau_rules::{DEAL, SEVEN_DRAWS, Top, auto_pick, lowest_rank, most_held_suit, playable};
use crate::protocol::{Action, PhoneScreen, PlayerId, Ranking, ServerTime, TvScene};
use crate::maumau_views::wish_id;
use crate::room_seats::Player;

#[derive(Debug, Clone)]
pub struct MauMauConfig {
    /// How long the banner keeps naming the last happening.
    pub banner_ms: f64,
    pub seed: Option<u64>,
}

impl Default for MauMauConfig {
    fn default() -> Self {
        Self { banner_ms: 2600.0, seed: None }
    }
}

pub(crate) const ENTRY: CatalogueEntry = CatalogueEntry {
    // 32 cards deal five to five players, the 52-card deck to eight.
    name: "Mau-Mau", seats: Seats::Range(2, 8), timed: false, sized: false,
    kids: Kids::Plays, board: false, needs: Need::Nothing,
    start: |_, ctx, _| Box::new(MauMauGame::new(MauMauConfig::default(), ctx.players, ctx.now)),
};

#[derive(Debug, Clone, PartialEq)]
pub(crate) enum Stage {
    Turn { ask: Ask },
    /// Drew a card: play it if it fits, or keep it.
    Drawn { card: Card, ask: Ask },
    /// Played a jack and picks the suit.
    Wish { ask: Ask },
    Final { ranking: Vec<usize> },
}

pub struct MauMauGame {
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
    pub(crate) table: CardTable,
    pub(crate) rng: StdRng,
}

impl MauMauGame {
    pub fn new(cfg: MauMauConfig, players: &[Player], now: ServerTime) -> Self {
        let mut rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let mut deck = shuffled(deck_from(lowest_rank(players.len())), &mut rng);
        let mut hands: Vec<Vec<Card>> = players.iter().map(|_| deck.split_off(deck.len() - DEAL)).collect();
        hands.iter_mut().for_each(|h| sort_hand(h));
        let start = deck.pop().expect("a deck holds more than the deal");
        let mut game = Self::seated(cfg, players, hands, vec![start], deck, rng, now);
        if start.rank == Rank::Eight {
            game.advance(now, 1);
        }
        game
    }

    /// A game dealt by hand for tests: hands as given, `discard` with its top card last,
    /// `deck` drawn from its end, `owed` cards waiting for seat 0, who plays first.
    #[cfg(test)]
    pub(crate) fn dealt(cfg: MauMauConfig, players: &[Player], hands: Vec<Vec<Card>>, discard: Vec<Card>, deck: Vec<Card>, owed: u8) -> Self {
        let rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        Self { owed, ..Self::seated(cfg, players, hands, discard, deck, rng, 0.0) }
    }

    /// Seat 0 to play on `discard`'s top card, which counts as if turned up to start.
    fn seated(cfg: MauMauConfig, players: &[Player], hands: Vec<Vec<Card>>, discard: Vec<Card>, deck: Vec<Card>, rng: StdRng, now: ServerTime) -> Self {
        let start = *discard.last().expect("the discard pile is never empty");
        let table = CardTable::new(players, cfg.banner_ms);
        Self {
            hands,
            deck,
            discard,
            turn: 0,
            suit: (start.rank != Rank::Jack).then_some(start.suit),
            wish: None,
            owed: if start.rank == Rank::Seven { SEVEN_DRAWS } else { 0 },
            stage: Stage::Turn { ask: table.ask(0, now) },
            table,
            rng,
        }
    }

    pub(crate) fn top(&self) -> Top {
        Top { card: *self.discard.last().expect("the discard pile is never empty"), suit: self.suit }
    }
}

impl Game for MauMauGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Cards { button, hand, .. } = action else {
            return Err(ActionError::Ignored);
        };
        if self.table.seat_of(player) != Some(self.turn) {
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
        if !self.open_ask().is_some_and(|ask| ask.house_decides(ctx)) {
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

    fn seats(&self) -> Option<Vec<PlayerId>> {
        Some(self.table.players().to_vec())
    }

    fn ranking(&self, _players: &[Player]) -> Option<Ranking> {
        let Stage::Final { ranking, .. } = &self.stage else { return None };
        Some(self.table.ranking(ranking))
    }
}

#[cfg(test)]
#[path = "maumau_tests.rs"]
mod tests;
