//! What the phones show during Grand Allee: the money sheet with the
//! player's cash and hotels, their decision when it is theirs, a sealed bid
//! during a debt auction, and otherwise what is going on.

use std::cmp::Reverse;

use super::board::HOTELS;
use super::live::{Bid, GrandAlleeGame, Stage};
use super::live_asks::Opt;
use super::rules::Permit;
use super::live_text::{grouped, name, taler};
use crate::game::ViewCtx;
use super::live_debts::BID_STEP;
use crate::protocol::{AnswerColor, BidRange, ChoiceOption, HotelCard, PhoneScreen, PlayerId, ServerTime, SheetLine};

/// Each hotel's own colour on the phone, by hotel id: its theme (lagoon, cherry
/// blossom, fjord, terracotta, ochre, alpine green, saffron, steel, savanna, plum).
const HOTEL_COLORS: [&str; 10] = ["#2aa3a0", "#d9608a", "#4a78a8", "#d0672f", "#c9862b", "#4f9a4a", "#e0a21a", "#5e6b7a", "#8a9a3a", "#8b5a9e"];

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
            Opt::Raise { .. } => "Anderes versteigern und bauen".into(),
            Opt::Done if !self.served.is_empty() => "Fertig".into(),
            Opt::EndTurn | Opt::Skip | Opt::Done => "Lieber nicht".into(),
            Opt::Back => "Zurück".into(),
        }
    }

    fn lines(&self, seat: usize) -> Vec<SheetLine> {
        let line = |label: &str, value: String| SheetLine { label: label.into(), value };
        vec![
            line("Taler", grouped(self.t.seats[seat].cash)),
            line("Runde", format!("{} von {}", self.round.min(self.rounds), self.rounds)),
            line("Vermögen", grouped(self.t.net_worth(seat))),
        ]
    }

    fn hotel_cards(&self, seat: usize) -> Vec<HotelCard> {
        let t = &self.t;
        let card = |h: u8| HotelCard {
            name: hotel(h).into(),
            color: HOTEL_COLORS[usize::from(h)].into(),
            built: t.estate(h).levels,
            stages: HOTELS[usize::from(h)].stages.len() as u8,
            rate: t.rate(h),
            entrances: t.entrances.iter().filter(|&&e| e == Some(h)).count() as u8,
            value: t.value(h),
        };
        t.owned(seat).into_iter().map(card).collect()
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
        let (lines, hotels) = (self.lines(me), self.hotel_cards(me));
        let sheet = |prompt: String, options, opens_at, closes_at: Option<ServerTime>, bid| PhoneScreen::Sheet {
            lines,
            hotels,
            prompt: Some(prompt),
            options,
            opens_at,
            closes_at,
            bid,
        };
        if self.t.seats[me].out {
            return sheet("Du bist raus. Schau zu, wie es ausgeht.".into(), Vec::new(), 0.0, None, None);
        }
        match &self.stage {
            Stage::Ask { ask, since } if me == self.turn => {
                let options = buttons(ask.opts.iter().map(|&o| self.label(view, o)));
                sheet(ask.prompt.clone(), options, *since, Some(since + self.cfg.ask_ms), None)
            }
            Stage::Auction(a) => match a.bids.iter().find(|(b, _)| *b == me) {
                Some(&(_, answer)) => {
                    let placed = match answer {
                        Bid::Open => None,
                        Bid::Pass => Some(0),
                        Bid::Amount(x) => Some(x),
                    };
                    let value = self.t.value(a.hotel);
                    let bid = BidRange { value, max: self.bid_max(me), step: BID_STEP, placed };
                    let prompt = format!("Versteigerung: {}", hotel(a.hotel));
                    sheet(prompt, Vec::new(), a.since, Some(a.since + self.cfg.auction_ms), Some(bid))
                }
                None => sheet(self.status(view, me), Vec::new(), 0.0, None, None),
            },
            _ => sheet(self.status(view, me), Vec::new(), 0.0, None, None),
        }
    }
}
