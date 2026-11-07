//! The turn flow of Durak: leading, beating, throwing in, taking, and what
//! happens to the cards once a bout is over.

use crate::cards::Card;
use crate::durak::{DurakGame, Stage};
use crate::durak_rules::{DEAL, attack_limit};
use crate::game::GameCtx;
use crate::protocol::{CardsEventKind, ServerTime};

impl DurakGame {
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
        let player = self.player_at(seat);
        self.bout.push((card, None));
        self.event(now, player, CardsEventKind::Play { card, pile: (self.bout.len() - 1) as u8 });
    }

    /// The attacker opens the bout; how many attacks it may hold is fixed now.
    pub(crate) fn lead(&mut self, now: ServerTime, index: usize) {
        self.limit = attack_limit(self.hands[self.defender()].len());
        self.thrower = 0;
        self.put_on_table(now, self.attacker, index);
        self.say(now, self.player_at(self.attacker), " greift an");
        self.stage = Stage::Defend { since: now };
    }

    /// The thrower adds a card; the defender must answer it, unless they are already taking.
    pub(crate) fn throw_in(&mut self, now: ServerTime, index: usize) {
        let seat = self.thrower_seat();
        self.put_on_table(now, seat, index);
        self.say(now, self.player_at(seat), " legt dazu");
        match self.stage {
            Stage::Throw { taking: true, .. } => self.seek(now, true),
            _ => self.stage = Stage::Defend { since: now },
        }
    }

    pub(crate) fn beat(&mut self, now: ServerTime, attack: usize, index: usize) {
        let defender = self.defender();
        let card = self.hands[defender].remove(index);
        self.bout[attack].1 = Some(card);
        self.event(now, self.player_at(defender), CardsEventKind::Play { card, pile: attack as u8 });
        self.say(now, self.player_at(defender), " schlägt");
        if self.bout.iter().all(|&(_, d)| d.is_some()) {
            self.seek(now, false);
        } else {
            self.stage = Stage::Defend { since: now };
        }
    }

    /// The defender gives up; the others may still add cards before they are taken.
    pub(crate) fn take(&mut self, now: ServerTime) {
        self.thrower = 0;
        self.say(now, self.player_at(self.defender()), " nimmt auf");
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
        let n = self.ids.len();
        while self.thrower < n && !self.is_asked(self.thrower_seat()) {
            self.thrower += 1;
        }
        self.stage = if self.thrower < n {
            Stage::Throw { since: now, taking }
        } else {
            let defender = self.player_at(self.defender());
            let text = if taking { " nimmt die Karten auf" } else { " hat alles geschlagen – Bito!" };
            self.say(now, defender, text);
            Stage::Settle { since: now, taken: taking }
        };
    }

    /// Moves the table's cards on, refills the hands and starts the next bout or ends the game.
    pub(crate) fn resolve(&mut self, ctx: &mut GameCtx, taken: bool) {
        let now = ctx.now;
        let (n, attacker, defender) = (self.ids.len(), self.attacker, self.defender());
        let cards: Vec<Card> = self.table();
        self.bout.clear();
        if taken {
            self.hands[defender].extend(cards);
            self.hands[defender].sort();
        }
        // the attacker draws first and the defender last
        let order: Vec<usize> = (0..n).map(|k| (attacker + k) % n).filter(|&s| s != defender).chain([defender]).filter(|s| !self.out.contains(s)).collect();
        for &seat in &order {
            let want = DEAL.saturating_sub(self.hands[seat].len()).min(self.deck.len());
            let from = self.deck.len() - want;
            self.hands[seat].extend(self.deck.split_off(from));
            self.hands[seat].sort();
            if want > 0 {
                self.event(now, self.player_at(seat), CardsEventKind::Draw { count: want as u8 });
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
        self.stage = Stage::Attack { since: now };
        self.say(now, self.player_at(self.attacker), " greift an");
    }

    fn finish(&mut self, ctx: &mut GameCtx) {
        let durak = (0..self.ids.len()).find(|s| !self.out.contains(s));
        let ranking: Vec<usize> = self.out.iter().copied().chain(durak).collect();
        for (place, &seat) in ranking.iter().enumerate() {
            if let Some(p) = ctx.players.iter_mut().find(|p| p.id == self.ids[seat]) {
                p.score = (ranking.len() - 1 - place) as i32;
            }
        }
        match durak {
            Some(seat) => self.say(ctx.now, self.ids[seat], " ist der Durak!"),
            None => self.say(ctx.now, self.ids[ranking[0]], " – keiner ist Durak!"),
        }
        self.stage = Stage::Final { ranking, durak };
    }
}
