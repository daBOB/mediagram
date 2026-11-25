//! What the phones and the TV show during Tiến Lên.

use crate::card_table::button;
use crate::game::{ViewCtx, name, unasked};
use crate::protocol::{ButtonNeeds, CardDesk, CardsBanner, CardsPile, CardsScene, CardsSeat, DeskCard, PhoneScreen, PlayerId, TvScene};
use crate::tienlen::{Stage, TienLenGame};
use crate::tienlen_rules::Kind;

/// A kind of play as the TV's note names it, and as the phone's prompt asks to beat it.
fn words(kind: Kind) -> (&'static str, &'static str) {
    match kind {
        Kind::Single => ("Einzelkarte", "die Karte"),
        Kind::Pair => ("Paar", "das Paar"),
        Kind::Triple => ("Drilling", "den Drilling"),
        Kind::Quad => ("Vierling", "den Vierling"),
        Kind::Run => ("Straße", "die Straße"),
        Kind::PairRun => ("Doppelstraße", "die Doppelstraße"),
    }
}

/// The bombs that beat that many twos.
fn bombs(twos: usize) -> &'static str {
    match twos {
        1 => "Vierling oder Doppelstraße aus 3 Paaren",
        2 => "Doppelstraße aus 4 Paaren",
        _ => "Doppelstraße aus 5 Paaren",
    }
}

impl TienLenGame {
    /// The place `seat` finished in, once it is out of cards.
    fn place(&self, seat: usize) -> Option<usize> {
        self.out.iter().position(|&s| s == seat).map(|i| i + 1)
    }

    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let Some(me) = self.table.seat_of(player) else {
            return unasked();
        };
        if matches!(self.stage, Stage::Final { .. }) {
            return unasked();
        }
        // while a play lies on show nobody is asked, not even whoever leads next
        let mine = me == self.turn && matches!(self.stage, Stage::Turn { .. });
        let play = button("play", "Legen", ButtonNeeds::SomeHand, true);
        let pass = button("pass", "Passen", ButtonNeeds::Nothing, false);
        let (prompt, info, buttons) = match (&self.top, mine) {
            (_, false) => {
                let aside = match self.place(me) {
                    Some(place) => Some(format!("Du bist fertig – Platz {place}")),
                    None => self.passed[me].then(|| "Du hast gepasst".into()),
                };
                let prompt = if me == self.turn { "Gleich spielst du aus".into() } else { format!("{} ist dran", name(view.players, self.table.player_at(self.turn))) };
                (prompt, aside, vec![])
            }
            (None, true) => ("Du spielst aus".into(), self.opening.map(|_| "Deine niedrigste Karte muss dabei sein".into()), vec![play]),
            (Some(top), true) if self.passed[me] => {
                ("Nur eine Bombe schlägt die 2".into(), Some(bombs(top.combo.len).into()), vec![button("play", "Bombe legen", ButtonNeeds::SomeHand, true), pass])
            }
            (Some(top), true) => {
                let hint = top.combo.twos().then(|| format!("Bombe: {}", bombs(top.combo.len)));
                (format!("Schlage {} oder passe", words(top.combo.kind).1), hint, vec![play, pass])
            }
        };
        let table = self.top.iter().flat_map(|top| &top.cards).map(|&card| DeskCard { card, enabled: false }).collect();
        // the host judges the pick, so every card of the hand in turn can be tapped
        let hand = self.hands[me].iter().map(|&card| DeskCard { card, enabled: mine }).collect();
        PhoneScreen::Cards { desk: CardDesk { prompt, info, table, hand, buttons } }
    }

    pub(crate) fn tv_view(&self, view: &ViewCtx) -> TvScene {
        let over = matches!(self.stage, Stage::Final { .. });
        let banner = self.table.banner(view.now, over).unwrap_or_else(|| CardsBanner {
            player: self.table.player_at(self.turn),
            text: if self.top.is_some() { " ist dran" } else { " spielt aus" }.into(),
        });
        let seats = self.table.players().iter().enumerate().map(|(seat, &player)| CardsSeat {
            player,
            cards: self.hands[seat].len() as u8,
            open: vec![],
            worth: None,
            marker: self.place(seat).map(|place| format!("Platz {place}")).or_else(|| (!over && self.passed[seat]).then(|| "Passt".into())),
        });
        TvScene::Cards {
            scene: CardsScene {
                game: "Tiến Lên".into(),
                seats: seats.collect(),
                // the play to beat lies alone in the middle; a cleared table shows none
                piles: self.top.iter().map(|top| CardsPile { cards: top.cards.clone(), count: top.cards.len() as u16 }).collect(),
                deck: 0,
                trump: None,
                turn: (!over).then(|| self.table.player_at(self.turn)),
                wish: None,
                note: self.top.as_ref().map(|top| words(top.combo.kind).0.into()),
                banner: Some(banner),
                events: self.table.events().to_vec(),
                ranking: match &self.stage {
                    Stage::Final { ranking } => Some(ranking.iter().map(|&s| self.table.player_at(s)).collect()),
                    Stage::Turn { .. } | Stage::Settle { .. } => None,
                },
            },
        }
    }
}
