//! The turn flow of Durak: leading, beating, throwing in, taking, and what
//! happens to the cards once a bout is over.

use crate::card_table::sort_hand;
use crate::cards::Card;
use crate::durak::{DurakGame, Stage};
use crate::durak_rules::{DEAL, attack_limit, auto_attack, auto_defence};
use crate::game::GameCtx;
use crate::protocol::{CardsEventKind, ServerTime};

impl DurakGame {
    /// What the house does for an absent `seat`: the cheapest lead or defence, taking, adding nothing.
    pub(crate) fn auto_play(&mut self, ctx: &mut GameCtx, seat: usize) {
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

    /// The attack a table position on the desk stands for, if it is an unbeaten one.
    /// The desk lists each attack followed by the card that beat it.
    pub(crate) fn attack_at(&self, position: usize) -> Option<usize> {
        let mut at = 0;
        for (a, &(_, defence)) in self.bout.iter().enumerate() {
            if at == position {
                return defence.is_none().then_some(a);
            }
            at += 1 + usize::from(defence.is_some());
        }
        None
    }

    fn put_on_table(&mut self, now: ServerTime, seat: usize, index: usize) {
        let card = self.hands[seat].remove(index);
        let player = self.table.player_at(seat);
        self.bout.push((card, None));
        self.table.event(now, player, CardsEventKind::Play { card, pile: (self.bout.len() - 1) as u8 });
    }

    /// The attacker opens the bout; how many attacks it may hold is fixed now.
    pub(crate) fn lead(&mut self, now: ServerTime, index: usize) {
        self.limit = attack_limit(self.hands[self.defender()].len());
        self.thrower = 0;
        self.put_on_table(now, self.attacker, index);
        self.table.say(now, self.table.player_at(self.attacker), " greift an");
        self.stage = Stage::Defend { ask: self.table.ask(self.defender(), now) };
    }

    /// The thrower adds a card; the defender must answer it, unless they are already taking.
    pub(crate) fn throw_in(&mut self, now: ServerTime, index: usize) {
        let seat = self.thrower_seat();
        self.put_on_table(now, seat, index);
        self.table.say(now, self.table.player_at(seat), " legt dazu");
        match self.stage {
            Stage::Throw { taking: true, .. } => self.seek(now, true),
            _ => self.stage = Stage::Defend { ask: self.table.ask(self.defender(), now) },
        }
    }

    pub(crate) fn beat(&mut self, now: ServerTime, attack: usize, index: usize) {
        let defender = self.defender();
        let card = self.hands[defender].remove(index);
        self.bout[attack].1 = Some(card);
        self.table.event(now, self.table.player_at(defender), CardsEventKind::Play { card, pile: attack as u8 });
        self.table.say(now, self.table.player_at(defender), " schlägt");
        if self.bout.iter().all(|&(_, d)| d.is_some()) {
            self.seek(now, false);
        } else {
            self.stage = Stage::Defend { ask: self.table.ask(defender, now) };
        }
    }

    /// The defender gives up; the others may still add cards before they are taken.
    pub(crate) fn take(&mut self, now: ServerTime) {
        self.thrower = 0;
        self.table.say(now, self.table.player_at(self.defender()), " nimmt auf");
        self.seek(now, true);
    }

    /// The thrower is done and is not asked again this bout.
    pub(crate) fn pass(&mut self, now: ServerTime) {
        self.thrower += 1;
        let taking = matches!(self.stage, Stage::Throw { taking: true, .. });
        self.seek(now, taking);
    }

    /// Everyone who still holds cards is asked, whether or not a card fits: skipping the
    /// ones who cannot would tell the table who can. Only the public limit ends the round early.
    fn is_asked(&self, seat: usize) -> bool {
        seat != self.defender() && self.bout.len() < self.limit && !self.hands[seat].is_empty()
    }

    /// Moves on to the next player who is asked to add a card; when nobody can, the bout is over.
    fn seek(&mut self, now: ServerTime, taking: bool) {
        let n = self.table.players().len();
        while self.thrower < n && !self.is_asked(self.thrower_seat()) {
            self.thrower += 1;
        }
        self.stage = if self.thrower < n {
            Stage::Throw { ask: self.table.ask(self.thrower_seat(), now), taking }
        } else {
            let defender = self.table.player_at(self.defender());
            let text = if taking { " nimmt die Karten auf" } else { " hat alles geschlagen – Bito!" };
            self.table.say(now, defender, text);
            Stage::Settle { since: now, taken: taking }
        };
    }

    /// Moves the table's cards on, refills the hands and starts the next bout or ends the game.
    pub(crate) fn resolve(&mut self, ctx: &mut GameCtx, taken: bool) {
        let now = ctx.now;
        let (n, attacker, defender) = (self.table.players().len(), self.attacker, self.defender());
        let cards: Vec<Card> = self.table();
        self.bout.clear();
        if taken {
            self.hands[defender].extend(cards);
            sort_hand(&mut self.hands[defender]);
        }
        // the attacker draws first and the defender last
        let order: Vec<usize> = (0..n).map(|k| (attacker + k) % n).filter(|&s| s != defender).chain([defender]).filter(|s| !self.out.contains(s)).collect();
        for &seat in &order {
            let want = DEAL.saturating_sub(self.hands[seat].len()).min(self.deck.len());
            let from = self.deck.len() - want;
            self.hands[seat].extend(self.deck.split_off(from));
            sort_hand(&mut self.hands[seat]);
            if want > 0 {
                self.table.event(now, self.table.player_at(seat), CardsEventKind::Draw { count: want as u8 });
            }
        }
        if self.deck.is_empty() {
            for &seat in &order {
                if self.hands[seat].is_empty() {
                    self.out.push(seat);
                }
            }
        }
        if self.out.len() + 1 >= n {
            return self.finish(ctx);
        }
        self.attacker = if taken || self.out.contains(&defender) { self.next_active(defender) } else { defender };
        self.stage = Stage::Attack { ask: self.table.ask(self.attacker, now) };
        self.table.say(now, self.table.player_at(self.attacker), " greift an");
    }

    fn finish(&mut self, ctx: &mut GameCtx) {
        let durak = (0..self.table.players().len()).find(|s| !self.out.contains(s));
        let ranking: Vec<usize> = self.out.iter().copied().chain(durak).collect();
        for (place, &seat) in ranking.iter().enumerate() {
            if let Some(p) = ctx.players.iter_mut().find(|p| p.id == self.table.player_at(seat)) {
                p.score = (ranking.len() - 1 - place) as i32;
            }
        }
        match durak {
            Some(seat) => self.table.say(ctx.now, self.table.player_at(seat), " ist der Durak!"),
            None => self.table.say(ctx.now, self.table.player_at(ranking[0]), " – keiner ist Durak!"),
        }
        self.stage = Stage::Final { ranking, durak };
    }
}
