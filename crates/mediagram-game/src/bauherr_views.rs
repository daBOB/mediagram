//! What the phones and the TV show during Bauherr.

use crate::bauherr::{BauherrGame, Stage, grouped, taler};
use crate::bauherr_asks::Opt;
use crate::game::ViewCtx;
use crate::protocol::{
    AnswerColor, BauherrEntrance, BauherrEstate, BauherrMark, BauherrPermit, BauherrScene, BauherrSeat, BauherrTurn,
    ChoiceOption, PhoneScreen, PlayerId, SheetLine, TvScene,
};

fn name(view: &ViewCtx, id: PlayerId) -> String {
    view.players.iter().find(|p| p.id == id).map_or_else(|| "Jemand".into(), |p| p.name.clone())
}

impl BauherrGame {
    fn label(&self, opt: &Opt) -> String {
        let t = &self.table;
        match *opt {
            Opt::Buy(p) => format!("{} kaufen · {}", t.plot(p).name, taler(t.plot(p).land)),
            Opt::Build(p) => format!("{} ausbauen", t.plot(p).name),
            Opt::BuildMenu => "Hotel ausbauen".into(),
            Opt::Entrances => "Eingang kaufen".into(),
            Opt::Hotel { plot, free: true } => t.plot(plot).name.clone(),
            Opt::Hotel { plot, .. } => format!("{} · {}", t.plot(plot).name, taler(t.plot(plot).entrance)),
            Opt::Place { field, free: true, .. } => format!("Feld {}", field + 1),
            Opt::Place { field, plot, .. } => format!("Feld {} · {}", field + 1, taler(t.plot(plot).entrance)),
            Opt::Levels { plot, count, cost } => {
                let reached = &t.plot(plot).levels[usize::from(t.levels(plot) + count) - 1].name;
                format!("Bis {reached} · {}", if cost == 0 { "gratis".into() } else { taler(cost) })
            }
            Opt::Skip => "Lieber nicht".into(),
        }
    }

    fn lines(&self, seat: usize, round_line: bool) -> Vec<SheetLine> {
        let t = &self.table;
        let line = |label: &str, value: String| SheetLine { label: label.into(), value };
        let mut lines = vec![line("Taler", grouped(t.seats[seat].cash))];
        if round_line {
            lines.push(line("Runde", format!("{} von {}", self.round.min(self.rounds), self.rounds)));
        }
        for p in t.owned(seat) {
            let (built, all) = (t.levels(p), t.plot(p).levels.len());
            let rate = if built > 0 { format!(" · {} pro Nacht", taler(t.rate(p))) } else { String::new() };
            lines.push(line(&t.plot(p).name, format!("Stufe {built} von {all}{rate}")));
        }
        lines
    }

    /// What everybody sees while nobody has anything to decide.
    fn status(&self, view: &ViewCtx, mine: bool) -> String {
        let t = &self.table;
        let who = name(view, self.player_at(self.turn));
        let sub = |du: &str, er: &str| if mine { format!("Du {du}") } else { format!("{who} {er}") };
        match &self.stage {
            Stage::Roll { .. } => sub("bist dran.", "ist dran."),
            Stage::Rolled { value, at } if view.now >= self.lands_at(*at) => format!("Eine {value}!"),
            Stage::Rolled { .. } => "Der Würfel rollt …".into(),
            Stage::Walking { bonus: true, .. } => sub("läufst und kassierst an der Bank 2.000 Taler.", "läuft und kassiert an der Bank."),
            Stage::Walking { .. } => sub("läufst …", "läuft …"),
            Stage::Guest { at, .. } if view.now < self.lands_at(*at) => "Wie viele Nächte? Der Würfel rollt …".into(),
            Stage::Guest { owner, plot, nights, amount, .. } => {
                let host = name(view, self.player_at(*owner));
                let hotel = &t.plot(*plot).name;
                let pay = if mine { "zahlst" } else { "zahlt" };
                let sub = if mine { "Du".to_string() } else { who };
                format!("{sub} {pay} {host} {} für {nights} Nächte im {hotel}.", taler(*amount))
            }
            Stage::Permit { at, .. } if view.now < self.lands_at(*at) => "Genehmigung: der Würfel rollt …".into(),
            Stage::Permit { outcome, .. } => match outcome {
                BauherrPermit::Approved => "Genehmigt!",
                BauherrPermit::Rejected => "Abgelehnt – diesmal wird nichts gebaut.",
                BauherrPermit::Free => "Gratis! Die erste Stufe schenkt die Stadt.",
                BauherrPermit::Double => "Doppelt! Alles kostet das Doppelte.",
            }
            .into(),
            Stage::Ask { .. } => sub("entscheidest …", "überlegt …"),
            Stage::Note { text, .. } => text.clone(),
            Stage::Final => String::new(),
        }
    }

    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let Some(me) = self.table.seats.iter().position(|s| s.player == player) else {
            return PhoneScreen::Wait { title: "Bauherr läuft".into(), text: Some("Du spielst in der nächsten Runde mit.".into()) };
        };
        if self.stage == Stage::Final {
            let order = self.table.standings();
            return PhoneScreen::Final {
                rank: order.iter().position(|&s| s == me).map_or(1, |r| r as u8 + 1),
                of: order.len() as u8,
                score: self.table.worth(me),
                can_restart: view.leader == Some(player),
            };
        }
        let mine = me == self.turn;
        let mut lines = self.lines(me, true);
        lines.push(SheetLine { label: "Vermögen".into(), value: grouped(self.table.worth(me)) });
        let sheet = |prompt: String, options: Vec<ChoiceOption>, opens_at| PhoneScreen::Sheet {
            lines: lines.clone(),
            prompt: Some(prompt),
            options,
            opens_at,
            picked: None,
        };
        if self.table.seats[me].out {
            return sheet("Du bist raus. Schau zu, wie es ausgeht.".into(), Vec::new(), 0.0);
        }
        match (&self.stage, mine) {
            (Stage::Roll { since }, true) => {
                sheet("Du bist dran".into(), vec![ChoiceOption { label: "Würfeln".into(), color: AnswerColor::Blue }], *since)
            }
            (Stage::Ask { ask, since, .. }, true) => {
                let options =
                    ask.opts.iter().zip(AnswerColor::ALL.iter().cycle()).map(|(o, &color)| ChoiceOption { label: self.label(o), color });
                sheet(ask.prompt.clone(), options.collect(), *since)
            }
            _ => sheet(self.status(view, mine), Vec::new(), 0.0),
        }
    }

    pub(crate) fn tv_view(&self) -> TvScene {
        let t = &self.table;
        let player = self.player_at(self.turn);
        let turn = match &self.stage {
            Stage::Roll { .. } => BauherrTurn::Roll { player },
            Stage::Rolled { value, at } => {
                BauherrTurn::Rolled { player, value: *value, rolled_at: *at, lands_at: self.lands_at(*at) }
            }
            Stage::Walking { value, from, at, bonus, .. } => BauherrTurn::Walking {
                player,
                value: *value,
                from: *from,
                to: t.seats[self.turn].field,
                started_at: *at,
                hop_ms: self.cfg.hop_ms,
                bonus: *bonus,
            },
            Stage::Guest { owner, plot, nights, amount, at } => BauherrTurn::Guest {
                player,
                owner: self.player_at(*owner),
                plot: *plot,
                nights: *nights,
                amount: *amount,
                rolled_at: *at,
                lands_at: self.lands_at(*at),
                paid_at: self.lands_at(*at) + self.cfg.show_ms,
            },
            Stage::Permit { plot, outcome, at } => BauherrTurn::Permit {
                player,
                plot: *plot,
                outcome: *outcome,
                rolled_at: *at,
                lands_at: self.lands_at(*at),
            },
            Stage::Ask { ask, .. } => {
                let marks = ask.opts.iter().zip(AnswerColor::ALL.iter().cycle()).filter_map(|(o, &color)| match *o {
                    Opt::Place { field, .. } => Some(BauherrMark { field, color }),
                    _ => None,
                });
                BauherrTurn::Ask { player, prompt: ask.prompt.clone(), plot: ask.plot, marks: marks.collect() }
            }
            Stage::Note { text, at } => BauherrTurn::Note { player, text: text.clone(), started_at: *at },
            Stage::Final => {
                let order = t.standings();
                BauherrTurn::Final {
                    winner: self.player_at(order[0]),
                    ranking: order.into_iter().map(|s| self.player_at(s)).collect(),
                }
            }
        };
        let estates = (0..t.estates.len() as u8)
            .filter_map(|plot| t.owner(plot).map(|o| BauherrEstate { plot, owner: self.player_at(o), levels: t.levels(plot) }))
            .collect();
        let entrances = t
            .entrances
            .iter()
            .enumerate()
            .filter_map(|(f, plot)| plot.map(|plot| BauherrEntrance { field: f as u8, plot }))
            .collect();
        let seats = t
            .seats
            .iter()
            .enumerate()
            .map(|(s, seat)| BauherrSeat { player: seat.player, field: seat.field, cash: seat.cash, worth: t.worth(s), out: seat.out })
            .collect();
        TvScene::Bauherr {
            scene: BauherrScene {
                board: t.board.clone(),
                estates,
                entrances,
                seats,
                round: self.round.min(self.rounds),
                rounds: self.rounds,
                turn,
            },
        }
    }
}
