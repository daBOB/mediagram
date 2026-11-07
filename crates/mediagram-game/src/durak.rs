//! Durak (classic, with throw-in): the state and the actions around the rules
//! in `durak_rules`; the turn flow lives in `durak_flow`.
//!
//! A bout: the attacker leads a card, the defender beats it or takes. Once
//! every attack is beaten the attacker may add a card whose rank is on the
//! table, then the players after them in seat order, one at a time; each
//! presses "Dazulegen" for as many cards as they like (each is defended before
//! the next) and "Fertig" when done, and a player who is done is not asked
//! again. A player with nothing to add is skipped. Taking works the same way,
//! except nothing is defended. The table then stays in view for a moment (Bito
//! or taken) before the cards move on. Players who are gone play for
//! themselves after `auto_ms`.

use rand::SeedableRng;
use rand::rngs::StdRng;

use crate::cards::{Card, Rank, deck_from, shuffled};
use crate::durak_rules::{DEAL, auto_attack, auto_defence, beats, can_throw, first_attacker};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, CardsBanner, CardsEvent, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::room_seats::Player;

#[derive(Debug, Clone)]
pub struct DurakConfig {
    /// An absent player's move happens after this.
    pub auto_ms: f64,
    /// How long the banner keeps naming the last happening.
    pub banner_ms: f64,
    /// How long a finished bout stays on the table before the cards move.
    pub settle_ms: f64,
    pub seed: Option<u64>,
}

impl Default for DurakConfig {
    fn default() -> Self {
        Self { auto_ms: 1500.0, banner_ms: 2600.0, settle_ms: 2400.0, seed: None }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) enum Stage {
    /// The attacker leads the first card of a bout.
    Attack { since: ServerTime },
    /// The defender beats an attack card or takes.
    Defend { since: ServerTime },
    /// The thrower may add a card or end their turn; `taking` when the defender gave up.
    Throw { since: ServerTime, taking: bool },
    /// Bito or taken, shown on the table before the cards move on.
    Settle { since: ServerTime, taken: bool },
    /// Seats in the order they got out; a last one still holding cards is the Durak.
    Final { ranking: Vec<usize>, durak: Option<usize> },
}

pub struct DurakGame {
    pub(crate) cfg: DurakConfig,
    pub(crate) ids: Vec<PlayerId>,
    pub(crate) hands: Vec<Vec<Card>>,
    /// Draw pile, drawn from the end; the trump card is its first card, so it comes last.
    pub(crate) deck: Vec<Card>,
    pub(crate) trump: Card,
    pub(crate) attacker: usize,
    /// Attack cards with the card that beat each, in the order they were led.
    pub(crate) bout: Vec<(Card, Option<Card>)>,
    /// How many attacks this bout may hold.
    pub(crate) limit: usize,
    /// Whose turn to add cards: seats counted on from the attacker.
    pub(crate) thrower: usize,
    /// Seats in the order they ran out of cards.
    pub(crate) out: Vec<usize>,
    pub(crate) stage: Stage,
    pub(crate) events: Vec<CardsEvent>,
    pub(crate) banner: Option<(CardsBanner, ServerTime)>,
    pub(crate) next_event: u32,
}

impl DurakGame {
    pub fn new(cfg: DurakConfig, players: &[Player], now: ServerTime) -> Self {
        let mut rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let mut deck = shuffled(deck_from(Rank::Six), &mut rng);
        let trump = deck[0];
        let mut hands: Vec<Vec<Card>> = players.iter().map(|_| deck.split_off(deck.len() - DEAL)).collect();
        hands.iter_mut().for_each(|h| h.sort());
        Self {
            attacker: first_attacker(trump.suit, &hands),
            cfg,
            ids: players.iter().map(|p| p.id).collect(),
            hands,
            deck,
            trump,
            bout: Vec::new(),
            limit: 0,
            thrower: 0,
            out: Vec::new(),
            stage: Stage::Attack { since: now },
            events: Vec::new(),
            banner: None,
            next_event: 0,
        }
    }

    fn auto_play(&mut self, ctx: &mut GameCtx, seat: usize) {
        let trump = self.trump_suit();
        match self.stage {
            Stage::Attack { .. } => {
                if let Some(i) = auto_attack(trump, &self.hands[seat]) {
                    self.lead(ctx.now, i);
                }
            }
            Stage::Defend { .. } => {
                let open = self.bout.iter().position(|&(_, d)| d.is_none());
                match open.and_then(|a| auto_defence(trump, self.bout[a].0, &self.hands[seat]).map(|i| (a, i))) {
                    Some((a, i)) => self.beat(ctx.now, a, i),
                    None => self.take(ctx.now),
                }
            }
            Stage::Throw { .. } => self.pass(ctx.now),
            Stage::Settle { .. } | Stage::Final { .. } => {}
        }
    }
}

impl Game for DurakGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Cards { button, hand, table } = action else {
            return Err(ActionError::Ignored);
        };
        let seat = self.seat_of(player).ok_or(ActionError::Ignored)?;
        if self.actor() != Some(seat) {
            return Err(ActionError::Ignored);
        }
        let card_at = |i: u8| self.hands[seat].get(usize::from(i)).copied().ok_or(ActionError::Ignored);
        match (&self.stage, button.as_str()) {
            (Stage::Attack { .. }, "play") => {
                let [i] = hand[..] else { return Err(ActionError::Ignored) };
                card_at(i)?;
                self.lead(ctx.now, usize::from(i));
            }
            (Stage::Throw { .. }, "play") => {
                let [i] = hand[..] else { return Err(ActionError::Ignored) };
                if !can_throw(&self.table(), card_at(i)?) {
                    return Err(ActionError::NotAllowed("Nur Werte, die schon auf dem Tisch liegen."));
                }
                self.throw_in(ctx.now, usize::from(i));
            }
            (Stage::Defend { .. }, "beat") => {
                let ([i], Some(t)) = (&hand[..], table) else { return Err(ActionError::Ignored) };
                // the desk lists each attack followed by the card that beat it
                let a = self.attack_at(usize::from(*t)).ok_or(ActionError::Ignored)?;
                if !beats(self.trump_suit(), self.bout[a].0, card_at(*i)?) {
                    return Err(ActionError::NotAllowed("Diese Karte schlägt nicht."));
                }
                self.beat(ctx.now, a, usize::from(*i));
            }
            (Stage::Defend { .. }, "take") => self.take(ctx.now),
            (Stage::Throw { .. }, "done") => self.pass(ctx.now),
            _ => return Err(ActionError::Ignored),
        }
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        if let Stage::Settle { since, taken } = self.stage {
            if ctx.now >= since + self.cfg.settle_ms {
                self.resolve(ctx, taken);
            }
            return;
        }
        let (Some(seat), Some(since)) = (self.actor(), self.since()) else { return };
        let Some(left) = self.absent_since(ctx.players, seat) else { return };
        if ctx.now >= left.max(since) + self.cfg.auto_ms {
            self.auto_play(ctx, seat);
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
#[path = "durak_tests.rs"]
mod tests;
