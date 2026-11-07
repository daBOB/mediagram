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
use crate::maumau_rules::{DEAL, SEVEN_DRAWS, Top, auto_pick, lowest_rank, most_held_suit, playable, ranking, sort_hand};
use crate::protocol::{
    Action, CardsBanner, CardsEvent, CardsEventKind, PhoneScreen, PlayerId, ServerTime, TvScene,
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

const MAU: &str = " ruft Mau!";
const PENALTY_START: &str = " vergisst Mau";
const PENALTY: &str = " vergisst Mau – Strafkarte!";

/// What the banner says after a forced draw of whatever number of cards was really dealt.
fn drawn_text(n: usize) -> String {
    match n {
        0 => " zieht keine Karte".into(),
        1 => " zieht eine Karte".into(),
        n => format!(" zieht {n} Karten"),
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
    next_event: u32,
    rng: StdRng,
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

    fn advance(&mut self, now: ServerTime, steps: usize) {
        self.turn = (self.turn + steps) % self.ids.len();
        self.stage = Stage::Turn { since: now };
    }

    fn event(&mut self, now: ServerTime, player: PlayerId, kind: CardsEventKind) {
        self.events.push(CardsEvent { id: self.next_event, at: now, player, kind });
        self.next_event += 1;
        if self.events.len() > 4 {
            self.events.remove(0);
        }
    }

    fn say(&mut self, now: ServerTime, player: PlayerId, text: impl Into<String>) {
        self.banner = Some((CardsBanner { player, text: text.into() }, now));
        self.queued = None;
    }

    /// Next card of the draw pile; an empty pile is refilled from the discards under the top card.
    fn draw_one(&mut self, now: ServerTime, player: PlayerId) -> Option<Card> {
        if self.deck.is_empty() && self.discard.len() > 1 {
            let top = self.discard.pop().expect("checked above");
            self.deck = shuffled(std::mem::replace(&mut self.discard, vec![top]), &mut self.rng);
            self.event(now, player, CardsEventKind::Reshuffle);
        }
        self.deck.pop()
    }

    /// Deals up to `n` cards into a hand, as one event; returns the cards actually dealt.
    fn give(&mut self, now: ServerTime, seat: usize, n: u8) -> Vec<Card> {
        let player = self.player_at(seat);
        let mut dealt = Vec::new();
        for _ in 0..n {
            let Some(card) = self.draw_one(now, player) else { break };
            self.hands[seat].push(card);
            dealt.push(card);
        }
        if !dealt.is_empty() {
            sort_hand(&mut self.hands[seat]);
            self.event(now, player, CardsEventKind::Draw { count: dealt.len() as u8 });
        }
        dealt
    }

    fn draw(&mut self, now: ServerTime) {
        let (seat, player) = (self.turn, self.player_at(self.turn));
        if self.owed > 0 {
            let n = std::mem::take(&mut self.owed);
            let drawn = self.give(now, seat, n).len();
            self.say(now, player, drawn_text(drawn));
            return self.advance(now, 1);
        }
        // The turn pauses on every drawn card, so nobody can tell from outside whether it fits.
        match self.give(now, seat, 1).first() {
            Some(&card) => self.stage = Stage::Drawn { card, since: now },
            None => self.advance(now, 1),
        }
        self.say(now, player, " zieht eine Karte");
    }

    fn play(&mut self, ctx: &mut GameCtx, index: usize, mau: bool) {
        let now = ctx.now;
        let seat = self.turn;
        let player = self.player_at(seat);
        let card = self.hands[seat].remove(index);
        self.discard.push(card);
        self.wish = None;
        self.suit = Some(card.suit);
        self.event(now, player, CardsEventKind::Play { card, pile: 0 });
        if self.hands[seat].is_empty() {
            return self.finish(ctx, seat);
        }
        match card.rank {
            Rank::Seven => {
                self.owed += SEVEN_DRAWS;
                self.advance(now, 1);
            }
            Rank::Eight => {
                self.say(now, self.player_at((seat + 1) % self.ids.len()), " setzt aus");
                self.advance(now, 2);
            }
            Rank::Jack => self.stage = Stage::Wish { since: now },
            _ => self.advance(now, 1),
        }
        if self.hands[seat].len() == 1 {
            if mau {
                self.say(now, player, MAU);
            } else {
                // the old top card is still under the played one, so the pile never runs dry here
                self.give(now, seat, 1);
                self.say(now, player, PENALTY);
            }
        }
    }

    fn finish(&mut self, ctx: &mut GameCtx, winner: usize) {
        let order = ranking(winner, &self.hands);
        for (place, &seat) in order.iter().enumerate() {
            if let Some(p) = ctx.players.iter_mut().find(|p| p.id == self.ids[seat]) {
                p.score = (order.len() - 1 - place) as i32;
            }
        }
        self.say(ctx.now, self.ids[winner], " gewinnt!");
        self.stage = Stage::Final { ranking: order };
    }

    fn choose_suit(&mut self, now: ServerTime, suit: Suit) {
        self.suit = Some(suit);
        self.wish = Some(suit);
        let wish = format!(" wünscht {}", suit.name());
        let player = self.player_at(self.turn);
        // Mau, or the penalty for forgetting it, named a moment ago stays up its full time; the wish follows.
        let pending = self.banner.as_ref().filter(|(b, at)| (b.text == MAU || b.text.starts_with(PENALTY_START)) && now < at + self.cfg.banner_ms).map(|&(_, at)| at);
        match pending {
            Some(at) => {
                self.queued = Some((CardsBanner { player, text: wish }, at + self.cfg.banner_ms));
            }
            None => self.say(now, player, wish),
        }
        self.advance(now, 1);
    }

    /// When the player whose turn it is dropped out, if they are gone.
    fn absent_since(&self, players: &[Player]) -> Option<ServerTime> {
        let p = players.iter().find(|p| p.id == self.player_at(self.turn))?;
        (!p.connected()).then(|| p.left_at.unwrap_or(0.0))
    }

    fn index_of(&self, card: Card) -> usize {
        self.hands[self.turn].iter().position(|&c| c == card).expect("the drawn card is in hand")
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

impl MauMauGame {
    /// When the current stage began.
    fn since(&self) -> Option<ServerTime> {
        match self.stage {
            Stage::Turn { since } | Stage::Drawn { since, .. } | Stage::Wish { since, .. } => Some(since),
            Stage::Final { .. } => None,
        }
    }
}

#[cfg(test)]
#[path = "maumau_tests.rs"]
mod tests;
