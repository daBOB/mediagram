//! The end of a Schwimmen round: the reveal, who loses a life, who is out, and
//! the ranking once one player is left.

use crate::game::GameCtx;
use crate::protocol::ServerTime;
use crate::schwimmen::{SchwimmenGame, Stage};
use crate::schwimmen_rules::value;

impl SchwimmenGame {
    /// Turns every hand face up. The lowest hands (all of them, if they are the
    /// same) lose a life; with none left a player was already swimming and is out.
    /// Everyone tied is spared: nobody would be left to lose to.
    pub(crate) fn reveal_round(&mut self, now: ServerTime) {
        self.revealed = true;
        let alive = self.alive();
        let low = alive.iter().map(|&s| value(&self.hands[s])).min();
        let mut losers: Vec<usize> = alive.iter().copied().filter(|&s| Some(value(&self.hands[s])) == low).collect();
        if losers.len() == alive.len() {
            losers.clear();
        }
        for &s in &losers {
            self.lost[s] = true;
            if self.lives[s] > 0 {
                self.lives[s] -= 1;
            } else {
                self.out[s] = true;
                self.out_order.push(s);
            }
        }
        let left = self.alive();
        if let [winner] = left[..] {
            let ranking = std::iter::once(winner).chain(self.out_order.iter().rev().copied()).collect();
            self.say(now, self.player_at(winner), " gewinnt!");
            self.stage = Stage::Final { ranking };
            return;
        }
        match losers[..] {
            [] => self.say(now, self.player_at(self.turn), " und alle anderen sind gleichauf"),
            [only] => {
                let text = if self.out[only] {
                    " scheidet aus"
                } else if self.lives[only] == 0 {
                    " schwimmt jetzt"
                } else {
                    " verliert ein Leben"
                };
                self.say(now, self.player_at(only), text);
            }
            [first, ..] => self.say(now, self.player_at(first), " und alle mit gleichem Blatt verlieren"),
        }
        self.stage = Stage::Reveal { since: now };
    }

    /// Writes the final scores (players beaten, like Mau-Mau) once, when the game ends.
    pub(crate) fn settle(&mut self, ctx: &mut GameCtx) {
        let Stage::Final { ranking } = &self.stage else { return };
        if std::mem::replace(&mut self.scored, true) {
            return;
        }
        for (place, &seat) in ranking.iter().enumerate() {
            if let Some(p) = ctx.players.iter_mut().find(|p| p.id == self.ids[seat]) {
                p.score = (ranking.len() - 1 - place) as i32;
            }
        }
    }
}
