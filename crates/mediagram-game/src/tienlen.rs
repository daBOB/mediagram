//! Tiến Lên (Thirteen): the turn flow around the rules in `tienlen_rules`.
//!
//! Everyone is dealt 13 cards. Whoever holds the lowest card leads, and that
//! card must be in the first play. Each next player beats the play on the
//! table or passes; a pass locks them out until the table is cleared, except
//! to bomb twos. A play nobody beats clears the table and its owner leads
//! anew. Players go out as their hands empty; the last one holding cards
//! loses. The house passes for a player who is gone, and leads their lowest card.

use rand::SeedableRng;
use rand::rngs::StdRng;

use crate::ask::Ask;
use crate::card_table::CardTable;
use crate::cards::{Card, Rank, deck_from, shuffled};
use crate::catalogue::{CatalogueEntry, Kids, Need, Seats};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, PhoneScreen, PlayerId, Ranking, ServerTime, TvScene};
use crate::room_seats::Player;
use crate::tienlen_rules::{Combo, DEAL, beats, combo, opener, sort_hand};

#[derive(Debug, Clone)]
pub struct TienLenConfig {
    /// How long the banner keeps naming the last happening.
    pub banner_ms: f64,
    /// How long a play nobody may answer lies on show before the table is cleared.
    pub settle_ms: f64,
    pub seed: Option<u64>,
}

impl Default for TienLenConfig {
    fn default() -> Self {
        Self { banner_ms: 2600.0, settle_ms: 2000.0, seed: None }
    }
}

pub(crate) const ENTRY: CatalogueEntry = CatalogueEntry {
    // One pack deals 13 cards to four players at most.
    name: "Tiến Lên", seats: Seats::Range(2, 4), timed: false, sized: false,
    kids: Kids::Plays, board: false, needs: Need::Nothing,
    start: |_, ctx, _| Box::new(TienLenGame::new(TienLenConfig::default(), ctx.players, ctx.now)),
};

/// The play to beat: its cards lowest first, what they make and whose it is.
#[derive(Debug, Clone, PartialEq)]
pub(crate) struct Top {
    pub(crate) cards: Vec<Card>,
    pub(crate) combo: Combo,
    pub(crate) owner: usize,
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) enum Stage {
    Turn { ask: Ask },
    /// A play laid when nobody could answer any more lies on show; then the player in turn leads.
    Settle { since: ServerTime },
    Final { ranking: Vec<usize> },
}

pub struct TienLenGame {
    pub(crate) hands: Vec<Vec<Card>>,
    /// `None` while the player whose turn it is leads onto a cleared table.
    pub(crate) top: Option<Top>,
    /// Who passed since the table was cleared.
    pub(crate) passed: Vec<bool>,
    /// Seats in the order they went out.
    pub(crate) out: Vec<usize>,
    /// The lowest card dealt, until it has been led.
    pub(crate) opening: Option<Card>,
    pub(crate) turn: usize,
    pub(crate) stage: Stage,
    pub(crate) table: CardTable,
    settle_ms: ServerTime,
}

impl TienLenGame {
    pub fn new(cfg: TienLenConfig, players: &[Player], now: ServerTime) -> Self {
        let mut rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let mut deck = shuffled(deck_from(Rank::Two), &mut rng);
        let hands = players.iter().map(|_| deck.split_off(deck.len() - DEAL)).collect();
        Self::dealt(cfg, players, hands, now)
    }

    /// A game with the hands as given; the holder of the lowest card leads.
    pub(crate) fn dealt(cfg: TienLenConfig, players: &[Player], mut hands: Vec<Vec<Card>>, now: ServerTime) -> Self {
        hands.iter_mut().for_each(|h| sort_hand(h));
        let (turn, low) = opener(&hands).expect("every player is dealt cards");
        let table = CardTable::new(players, cfg.banner_ms);
        Self {
            passed: vec![false; hands.len()],
            hands,
            top: None,
            out: Vec::new(),
            opening: Some(low),
            turn,
            stage: Stage::Turn { ask: table.ask(turn, now) },
            table,
            settle_ms: cfg.settle_ms,
        }
    }

    /// The cards of the hand in turn at the places picked on the phone, each once.
    fn picked(&self, places: &[u8]) -> Result<Vec<Card>, ActionError> {
        let hand = &self.hands[self.turn];
        let mut cards = Vec::with_capacity(places.len());
        for &i in places {
            let card = *hand.get(usize::from(i)).ok_or(ActionError::Ignored)?;
            if cards.contains(&card) {
                return Err(ActionError::Ignored);
            }
            cards.push(card);
        }
        Ok(cards)
    }

    /// What `cards` make, if the player in turn may lay them now.
    fn allowed(&self, cards: &[Card]) -> Result<Combo, ActionError> {
        let made = combo(cards).ok_or(ActionError::NotAllowed("Das ist keine gültige Kombination."))?;
        if self.opening.is_some_and(|low| !cards.contains(&low)) {
            return Err(ActionError::NotAllowed("Deine niedrigste Karte muss beim ersten Ausspiel dabei sein."));
        }
        let Some(top) = &self.top else { return Ok(made) };
        if !beats(top.combo, made) {
            return Err(ActionError::NotAllowed("Das schlägt die Karten auf dem Tisch nicht."));
        }
        if self.passed[self.turn] && !made.bomb() {
            return Err(ActionError::NotAllowed("Du hast gepasst – jetzt schlägt nur noch eine Bombe."));
        }
        Ok(made)
    }
}

impl Game for TienLenGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Cards { button, hand, .. } = action else {
            return Err(ActionError::Ignored);
        };
        if !matches!(self.stage, Stage::Turn { .. }) || self.table.seat_of(player) != Some(self.turn) {
            return Err(ActionError::Ignored);
        }
        match button.as_str() {
            "play" => {
                let cards = self.picked(hand)?;
                let made = self.allowed(&cards)?;
                self.play(ctx, cards, made);
            }
            // whoever leads has nothing to pass on
            "pass" if self.top.is_some() => self.pass(ctx.now),
            _ => return Err(ActionError::Ignored),
        }
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let ask = match self.stage {
            Stage::Turn { ask } => ask,
            Stage::Settle { since } if ctx.now >= since + self.settle_ms => return self.clear(ctx.now),
            Stage::Settle { .. } | Stage::Final { .. } => return,
        };
        if !ask.house_decides(ctx) {
            return;
        }
        if self.top.is_some() {
            return self.pass(ctx.now);
        }
        // the hand lies lowest first, so this is also the card the first lead must hold
        let card = self.hands[self.turn][0];
        let made = combo(&[card]).expect("one card is a play");
        self.play(ctx, vec![card], made);
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
        let Stage::Final { ranking } = &self.stage else { return None };
        Some(self.table.ranking(ranking))
    }
}

#[cfg(test)]
#[path = "tienlen_tests.rs"]
mod tests;
