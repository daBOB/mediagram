//! Bauherr's turn flow: what follows what, and what each answer does.

use crate::bauherr::{BauherrGame, Stage, name, taler};
use crate::bauherr_asks::{Ask, Opt};
use crate::bauherr_rules::{Table, permit_for};
use crate::game::GameCtx;
use crate::protocol::{BauherrPermit, BauherrTileKind, ServerTime};
use crate::room_seats::Player;

impl BauherrGame {
    pub(crate) fn end_turn(&mut self, now: ServerTime, note: Option<String>) {
        if let Some(text) = note {
            self.stage = Stage::Note { text, at: now };
        } else {
            self.next_turn(now);
        }
    }

    /// Hands over to the next player still in; ends the game on the round limit
    /// or when one player is left.
    pub(crate) fn next_turn(&mut self, now: ServerTime) {
        let n = self.table.seats.len();
        let mut next = self.turn;
        loop {
            next += 1;
            if next == n {
                next = 0;
                self.round += 1;
            }
            if !self.table.seats[next].out || self.table.active() <= 1 {
                break;
            }
        }
        if self.round > self.rounds || self.table.active() <= 1 {
            self.stage = Stage::Final;
        } else {
            self.turn = next;
            self.stage = Stage::Roll { since: now };
        }
    }

    pub(crate) fn ask(&mut self, now: ServerTime, ask: Option<Ask>, permit: BauherrPermit) {
        match ask {
            Some(ask) => self.stage = Stage::Ask { ask, since: now, permit },
            None => self.end_turn(now, None),
        }
    }

    /// The walk ended: a guest pays first, then the field offers its action.
    pub(crate) fn arrive(&mut self, now: ServerTime) {
        let field = self.table.seats[self.turn].field;
        if let Some((owner, plot)) = self.table.host_at(self.turn, field) {
            let nights = self.die();
            let amount = i32::from(nights) * self.table.rate(plot);
            self.stage = Stage::Guest { owner, plot, nights, amount, at: now };
        } else {
            self.offer(now);
        }
    }

    pub(crate) fn offer(&mut self, now: ServerTime) {
        let ask = self.table.field_ask(self.turn);
        self.ask(now, ask, BauherrPermit::Approved);
    }

    pub(crate) fn choose(&mut self, ctx: &mut GameCtx, opt: Opt, permit: BauherrPermit) {
        let (now, s) = (ctx.now, self.turn);
        let hotel = |t: &Table, p: u8| t.plot(p).name.clone();
        let who = name(ctx.players, self.player_at(s));
        match opt {
            Opt::Skip | Opt::Done => self.end_turn(now, None),
            Opt::Buy(p) => {
                self.table.buy(s, p);
                self.end_turn(now, Some(format!("{who} kauft {}.", hotel(&self.table, p))));
            }
            Opt::Build(plot) => {
                let outcome = permit_for(self.die());
                self.stage = Stage::Permit { plot, outcome, at: now };
            }
            Opt::BuildMenu => {
                let ask = self.table.build_menu_ask(s);
                self.ask(now + self.cfg.chain_ms, Some(ask), permit);
            }
            Opt::Entrances => {
                let ask = self.table.hotel_ask(s, false);
                self.ask(now + self.cfg.chain_ms, ask, permit);
            }
            Opt::Hotel { plot, free } => {
                let ask = self.table.place_ask(plot, free);
                self.ask(now + self.cfg.chain_ms, Some(ask), permit);
            }
            Opt::Place { plot, field, free } => {
                let price = if free { 0 } else { self.table.plot(plot).entrance };
                self.table.place_entrance(s, plot, field, price);
                let text = format!("{who} baut einen Eingang für {}.", hotel(&self.table, plot));
                self.errand_done(now, text);
            }
            Opt::Levels { plot, count, cost } => {
                self.table.build(s, plot, count, cost);
                let text = format!("{who} baut {}: jetzt {} Stufen.", hotel(&self.table, plot), self.table.levels(plot));
                self.errand_done(now, text);
            }
        }
    }

    /// An errand is done: at the Rathaus the menu comes back for the next one,
    /// anywhere else (or when nothing is left to do) the turn ends with `text`.
    fn errand_done(&mut self, now: ServerTime, text: String) {
        let at_rathaus = self.table.kind(self.table.seats[self.turn].field) == BauherrTileKind::Rathaus;
        match at_rathaus.then(|| self.table.rathaus_ask(self.turn, Some(&text))).flatten() {
            Some(ask) => self.ask(now + self.cfg.chain_ms, Some(ask), BauherrPermit::Approved),
            None => self.end_turn(now, Some(text)),
        }
    }

    /// When the player whose turn it is dropped out, if they are gone.
    pub(crate) fn absent_since(&self, players: &[Player]) -> Option<ServerTime> {
        let id = self.player_at(self.turn);
        let p = players.iter().find(|p| p.id == id)?;
        (!p.connected()).then(|| p.left_at.unwrap_or(0.0))
    }

    pub(crate) fn tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        let auto_after = |from: ServerTime| self.absent_since(ctx.players).map(|left| left.max(from) + self.cfg.auto_ms);
        let (roll, show) = (self.cfg.roll_ms, self.cfg.show_ms);
        match self.stage.clone() {
            Stage::Roll { since } if auto_after(since).is_some_and(|at| now >= at) => {
                self.stage = Stage::Rolled { value: self.die(), at: now };
            }
            Stage::Rolled { value, at } if now >= at + roll + show => {
                let (from, _, bonus) = self.table.walk(self.turn, value);
                let at = now + self.cfg.lead_ms;
                let ends_at = at + f64::from(value) * self.cfg.hop_ms;
                self.stage = Stage::Walking { value, from, at, ends_at, bonus };
            }
            Stage::Walking { ends_at, .. } if now >= ends_at => self.arrive(now),
            Stage::Guest { owner, amount, at, .. } if now >= at + roll + show => {
                let paid = self.table.pay(self.turn, Some(owner), amount);
                let who = name(ctx.players, self.player_at(self.turn));
                if self.table.seats[self.turn].out {
                    let text = format!("{who} kann nicht zahlen ({}) und ist raus.", taler(paid));
                    self.end_turn(now, Some(text));
                } else {
                    self.offer(now);
                }
            }
            Stage::Permit { plot, outcome, at } if now >= at + roll + show => {
                if outcome == BauherrPermit::Rejected {
                    let who = name(ctx.players, self.player_at(self.turn));
                    self.end_turn(now, Some(format!("{who}: Genehmigung abgelehnt.")));
                } else {
                    match self.table.levels_ask(self.turn, plot, outcome) {
                        None => {
                            let who = name(ctx.players, self.player_at(self.turn));
                            self.end_turn(now, Some(format!("{who} kann sich das Doppelte nicht leisten.")));
                        }
                        ask => self.ask(now, ask, outcome),
                    }
                }
            }
            Stage::Ask { ask, since, permit } if auto_after(since).is_some_and(|at| now >= at) => {
                let pick = self.table.auto_index(self.turn, &ask);
                self.choose(ctx, ask.opts[pick], permit);
            }
            Stage::Note { at, .. } if now >= at + self.cfg.note_ms => self.next_turn(now),
            _ => {}
        }
    }
}
