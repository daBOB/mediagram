//! What the phones and the TV show during Schwimmen.

use crate::game::{ViewCtx, name, unasked};
use crate::card_table::button;
use crate::protocol::{
    ButtonNeeds, CardDesk, CardsBanner, CardsPile, CardsScene, CardsSeat, DeskCard, PhoneScreen, PlayerId, TvScene,
};
use crate::schwimmen::{SchwimmenGame, Stage};
use crate::schwimmen_rules::{FEUER, value};

/// Lives as coins, by lives left; none left means the player swims.
pub(crate) const COINS: [&str; 4] = ["schwimmt", "●○○", "●●○", "●●●"];

impl SchwimmenGame {
    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let Some(me) = self.table.seat_of(player) else {
            return unasked();
        };
        if matches!(self.stage, Stage::Final { .. }) {
            return unasked();
        }
        if self.out[me] {
            return PhoneScreen::Wait { title: "Du schwimmst nicht mehr mit".into(), text: Some("Schau zu, wer übrig bleibt.".into()) };
        }
        let hand = &self.hands[me];
        let mine = matches!(self.stage, Stage::Turn { .. }) && me == self.turn;
        let worth = value(hand).show();
        let (prompt, info, buttons) = match &self.stage {
            Stage::Reveal { .. } => {
                let end = if self.lost[me] { "Du verlierst ein Leben" } else { "Diesmal sicher" };
                ("Aufgedeckt".into(), format!("Dein Blatt: {worth} · {end}"), vec![])
            }
            _ if !mine => {
                let prompt = format!("{} ist dran", name(view.players, self.table.player_at(self.turn)));
                (prompt, format!("Dein Blatt: {worth} · {}", COINS[usize::from(self.lives[me])]), vec![])
            }
            _ => {
                let mut buttons = vec![
                    button("swap", "Tauschen", ButtonNeeds::HandAndTable, true),
                    button("swap_all", "Alle drei tauschen", ButtonNeeds::Nothing, false),
                    button("pass", "Schieben", ButtonNeeds::Nothing, false),
                ];
                let prompt = match self.knocker {
                    Some(k) => format!("{} hat geklopft – dein letzter Zug", name(view.players, self.table.player_at(k))),
                    None => {
                        buttons.push(button("knock", "Klopfen", ButtonNeeds::Nothing, false));
                        "Du bist dran".into()
                    }
                };
                (prompt, format!("Dein Blatt: {worth} · {}", COINS[usize::from(self.lives[me])]), buttons)
            }
        };
        PhoneScreen::Cards {
            desk: CardDesk {
                prompt,
                info: Some(info),
                table: self.middle.iter().map(|&card| DeskCard { card, enabled: mine }).collect(),
                hand: hand.iter().map(|&card| DeskCard { card, enabled: mine }).collect(),
                buttons,
            },
        }
    }

    /// Lives, then what the seat did: knocking, or the loss at the reveal (the value rides in the badge).
    fn marker(&self, seat: usize) -> String {
        if self.out[seat] {
            return "raus".into();
        }
        let coins = COINS[usize::from(self.lives[seat])];
        if self.revealed {
            return if self.lost[seat] { format!("{coins} · −1") } else { coins.into() };
        }
        if self.knocker == Some(seat) { format!("{coins} · klopft") } else { coins.into() }
    }

    pub(crate) fn tv_view(&self, view: &ViewCtx) -> TvScene {
        let banner = self.table.banner(view.now, self.revealed).unwrap_or_else(|| CardsBanner { player: self.table.player_at(self.turn), text: " ist dran".into() });
        let fire = self.revealed && self.alive_hands_hold(FEUER);
        let note = match (self.revealed, self.knocker) {
            (true, _) if fire => Some("Feuer – 31!".to_string()),
            (true, _) => Some("Aufgedeckt".to_string()),
            (false, Some(_)) => Some("Geklopft – letzte Runde".to_string()),
            _ => None,
        };
        TvScene::Cards {
            scene: CardsScene {
                game: "Schwimmen".into(),
                seats: (0..self.table.players().len())
                    .map(|s| CardsSeat {
                        player: self.table.player_at(s),
                        cards: if self.revealed { 0 } else { self.hands[s].len() as u8 },
                        open: if self.revealed { self.hands[s].clone() } else { vec![] },
                        worth: (self.revealed && !self.hands[s].is_empty()).then(|| value(&self.hands[s]).0),
                        marker: Some(self.marker(s)),
                    })
                    .collect(),
                piles: self.middle.iter().map(|&c| CardsPile { cards: vec![c], count: 1 }).collect(),
                deck: self.deck.len() as u16,
                trump: None,
                turn: matches!(self.stage, Stage::Turn { .. }).then(|| self.table.player_at(self.turn)),
                wish: None,
                note,
                banner: Some(banner),
                events: self.table.events().to_vec(),
                ranking: match &self.stage {
                    Stage::Final { ranking } => Some(ranking.iter().map(|&s| self.table.player_at(s)).collect()),
                    _ => None,
                },
            },
        }
    }

    fn alive_hands_hold(&self, v: crate::schwimmen_rules::Value) -> bool {
        self.hands.iter().any(|h| !h.is_empty() && value(h) == v)
    }
}
