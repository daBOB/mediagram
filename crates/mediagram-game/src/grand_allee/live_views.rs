//! What the phones show during Grand Allee: the money sheet with the
//! player's cash and hotels, their decision when it is theirs, a sealed bid
//! during a debt auction, and otherwise what is going on.

use std::cmp::Reverse;

use super::board::HOTELS;
use super::live::{Bid, GrandAlleeGame, Stage};
use super::live_asks::Opt;
use super::rules::Permit;
use crate::bauherr::{grouped, name, taler};
use crate::game::ViewCtx;
use crate::protocol::{AnswerColor, ChoiceOption, PhoneScreen, PlayerId, SheetLine};

fn buttons(labels: impl IntoIterator<Item = String>) -> Vec<ChoiceOption> {
    labels.into_iter().zip(AnswerColor::ALL.iter().cycle()).map(|(label, &color)| ChoiceOption { label, color }).collect()
}

fn hotel(h: u8) -> &'static str {
    HOTELS[usize::from(h)].name
}

impl GrandAlleeGame {
    /// Seats first to last: those still in by net worth, then those out, whoever lasted longest first.
    pub(crate) fn standings(&self) -> Vec<usize> {
        let mut order: Vec<usize> = (0..self.t.seats.len()).filter(|&s| !self.t.seats[s].out).collect();
        order.sort_by_key(|&s| (Reverse(self.t.net_worth(s)), s));
        order.extend(self.gone.iter().rev());
        order
    }

    fn label(&self, view: &ViewCtx, opt: Opt) -> String {
        let (t, cost) = (&self.t, taler(self.price(opt)));
        match opt {
            Opt::Roll if self.roll == 6 => "Nochmal würfeln".into(),
            Opt::Roll => "Würfeln".into(),
            Opt::Leisure(h) => format!("{}: Freizeitflügel · {cost}", hotel(h)),
            Opt::Buy(h) => format!("{} kaufen · {cost}", hotel(h)),
            Opt::Force(h) => {
                let owner = t.estate(h).owner.map_or_else(String::new, |o| name(view.players, self.ids[o]));
                format!("{} von {owner} übernehmen · {cost}", hotel(h))
            }
            Opt::Build { hotel: h, count } => {
                let stages = if count == 1 { "1 Stufe".into() } else { format!("{count} Stufen") };
                format!("{}: {stages} · {cost}", hotel(h))
            }
            Opt::Hotel { hotel: h, free: true } => hotel(h).into(),
            Opt::Hotel { hotel: h, free: false } => format!("{} · {cost}", hotel(h)),
            Opt::Place { field, .. } => format!("Feld {}", field + 1),
            Opt::FreeStage(h) => format!("{}: Stufe {} gratis", hotel(h), t.estate(h).levels + 1),
            Opt::Sell(h) => format!("{} versteigern · Wert {}", hotel(h), taler(t.value(h))),
            Opt::Done if !self.served.is_empty() => "Fertig".into(),
            Opt::EndTurn | Opt::Skip | Opt::Done => "Lieber nicht".into(),
            Opt::Back => "Zurück".into(),
        }
    }

    fn lines(&self, seat: usize) -> Vec<SheetLine> {
        let t = &self.t;
        let line = |label: &str, value: String| SheetLine { label: label.into(), value };
        let mut lines = vec![line("Taler", grouped(t.seats[seat].cash)), line("Runde", format!("{} von {}", self.round.min(self.rounds), self.rounds))];
        for h in t.owned(seat) {
            let (built, all) = (t.estate(h).levels, HOTELS[usize::from(h)].stages.len());
            let rate = if built > 0 { format!(" · {} pro Nacht", taler(t.rate(h))) } else { String::new() };
            lines.push(line(hotel(h), format!("Stufe {built} von {all}{rate}")));
        }
        lines.push(line("Vermögen", grouped(t.net_worth(seat))));
        lines
    }

    /// What everybody sees while nobody has anything to decide.
    fn status(&self, view: &ViewCtx, me: usize) -> String {
        let mine = me == self.turn;
        let who = name(view.players, self.ids[self.turn]);
        let sub = |du: &str, er: &str| if mine { format!("Du {du}") } else { format!("{who} {er}") };
        let landed = |at: f64| view.now >= at + self.cfg.roll_ms;
        match &self.stage {
            Stage::Ask { .. } => sub("entscheidest …", "überlegt …"),
            Stage::Rolled { value, at } if landed(*at) => format!("Eine {value}!"),
            Stage::Rolled { .. } => "Der Würfel rollt …".into(),
            Stage::Walking { bank: true, .. } => {
                let bonus = taler(self.t.rules.lap_bonus);
                sub(&format!("läufst und kassierst an der Bank {bonus}."), &format!("läuft und kassiert an der Bank {bonus}."))
            }
            Stage::Walking { .. } => sub("läufst …", "läuft …"),
            Stage::Guest { at, .. } if !landed(*at) => "Wie viele Nächte? Der Würfel rollt …".into(),
            Stage::Guest { owner, hotel: h, nights, amount, .. } => {
                let host = if *owner == me { "dir".to_string() } else { name(view.players, self.ids[*owner]) };
                let stay = format!("{} für {nights} {} im {}.", taler(*amount), if *nights == 1 { "Nacht" } else { "Nächte" }, hotel(*h));
                sub(&format!("zahlst {host} {stay}"), &format!("zahlt {host} {stay}"))
            }
            Stage::Permit { at, .. } if !landed(*at) => "Genehmigung: der Würfel rollt …".into(),
            Stage::Permit { permit, .. } => match permit {
                Permit::Approved => "Genehmigt!",
                Permit::Rejected => "Abgelehnt – diesmal wird nichts gebaut.",
                Permit::Free => "Gratis! Die erste Stufe schenkt die Stadt.",
                Permit::Double => "Doppelt! Alles kostet das Doppelte.",
            }
            .into(),
            Stage::Auction(a) => sub(&format!("versteigerst {} …", hotel(a.hotel)), &format!("versteigert {} …", hotel(a.hotel))),
            Stage::Sold { hotel: h, winner: Some(w), price, .. } => {
                format!("{} ersteigert {} für {}.", if *w == me { "Du".into() } else { name(view.players, self.ids[*w]) }, hotel(*h), taler(*price))
            }
            Stage::Sold { hotel: h, price, .. } => format!("Kein Gebot: Die Bank kauft {} für {}.", hotel(*h), taler(*price)),
            Stage::Note { text, .. } => text.clone(),
            Stage::Final => String::new(),
        }
    }

    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let Some(me) = self.ids.iter().position(|&id| id == player) else {
            return PhoneScreen::Wait { title: "Grand Allee läuft".into(), text: Some("Du spielst in der nächsten Runde mit.".into()) };
        };
        if self.stage == Stage::Final {
            let order = self.standings();
            let rank = order.iter().position(|&s| s == me).map_or(1, |r| r as u8 + 1);
            return PhoneScreen::Final { rank, of: order.len() as u8, score: self.t.net_worth(me), can_restart: view.leader == Some(player) };
        }
        let lines = self.lines(me);
        let sheet = |prompt: String, options, opens_at, picked| PhoneScreen::Sheet { lines, prompt: Some(prompt), options, opens_at, picked };
        if self.t.seats[me].out {
            return sheet("Du bist raus. Schau zu, wie es ausgeht.".into(), Vec::new(), 0.0, None);
        }
        match &self.stage {
            Stage::Ask { ask, since } if me == self.turn => {
                sheet(ask.prompt.clone(), buttons(ask.opts.iter().map(|&o| self.label(view, o))), *since, None)
            }
            Stage::Auction(a) => match a.bids.iter().find(|(b, _)| *b == me) {
                Some(&(_, answer)) => {
                    let amounts = self.bid_amounts(me, a.hotel);
                    let picked = match answer {
                        Bid::Open => None,
                        Bid::Pass => Some(amounts.len() as u8),
                        Bid::Amount(x) => amounts.iter().position(|&y| y == x).map(|i| i as u8),
                    };
                    let labels = amounts.iter().map(|&x| taler(x)).chain(["Passen".to_string()]);
                    let prompt = format!("Versteigerung: {} (Wert {}). Dein Gebot?", hotel(a.hotel), taler(self.t.value(a.hotel)));
                    sheet(prompt, buttons(labels), a.since, picked)
                }
                None => sheet(self.status(view, me), Vec::new(), 0.0, None),
            },
            _ => sheet(self.status(view, me), Vec::new(), 0.0, None),
        }
    }
}
