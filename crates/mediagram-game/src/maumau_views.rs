//! What the phones and the TV show during Mau-Mau.

use crate::cards::Suit;
use crate::game::{ViewCtx, name};
use crate::maumau::{MauMauGame, Stage};
use crate::maumau_rules::playable;
use crate::protocol::{
    ButtonNeeds, CardDesk, CardsBanner, CardsPile, CardsScene, CardsSeat, DeskButton, DeskCard, PhoneScreen, PlayerId,
    TvScene,
};

/// Discards the TV draws: a few are enough to show a pile.
const PILE_SHOWN: usize = 3;

pub(crate) fn wish_id(suit: Suit) -> String {
    format!("wish_{}", format!("{suit:?}").to_lowercase())
}

pub(crate) fn button(id: &str, label: &str, needs: ButtonNeeds, primary: bool) -> DeskButton {
    DeskButton { id: id.into(), label: label.into(), needs, primary, suit: None }
}

impl MauMauGame {
    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let Some(me) = self.seat_of(player) else {
            return PhoneScreen::Wait { title: "Mau-Mau läuft".into(), text: Some("Du spielst in der nächsten Runde mit.".into()) };
        };
        if let Stage::Final { ranking } = &self.stage {
            return PhoneScreen::Final {
                rank: ranking.iter().position(|&s| s == me).map_or(1, |r| r as u8 + 1),
                of: self.ids.len() as u8,
                score: view.players.iter().find(|p| p.id == player).map_or(0, |p| p.score),
                can_restart: view.leader == Some(player),
            };
        }
        let mine = me == self.turn;
        let top = self.top();
        let hand = &self.hands[me];
        let (prompt, enabled, buttons): (String, Vec<bool>, Vec<DeskButton>) = match (&self.stage, mine) {
            (_, false) => (format!("{} ist dran", name(view.players, self.player_at(self.turn))), vec![false; hand.len()], vec![]),
            (Stage::Turn { .. }, true) => {
                let draw = if self.owed > 0 { format!("{} Karten ziehen", self.owed) } else { "Karte ziehen".into() };
                let prompt = if self.owed > 0 { format!("Lege eine 7 oder zieh {}", self.owed) } else { "Du bist dran".into() };
                let mut buttons = self.play_buttons(me);
                buttons.push(button("draw", &draw, ButtonNeeds::Nothing, false));
                (prompt, hand.iter().map(|&c| playable(top, self.owed, c)).collect(), buttons)
            }
            (Stage::Drawn { card, .. }, true) => {
                let mut buttons = self.play_buttons(me);
                buttons.push(button("keep", "Behalten", ButtonNeeds::Nothing, false));
                let fits = playable(top, 0, *card);
                let prompt = if fits { "Die gezogene Karte passt" } else { "Die gezogene Karte passt nicht" };
                (prompt.into(), hand.iter().map(|c| c == card && fits).collect(), buttons)
            }
            (_, true) => {
                let suits = Suit::ALL.map(|s| DeskButton { suit: Some(s), ..button(&wish_id(s), &format!("{} {}", s.symbol(), s.name()), ButtonNeeds::Nothing, true) });
                ("Welche Farbe wünschst du dir?".into(), vec![false; hand.len()], suits.to_vec())
            }
        };
        let info = match (self.wish, self.owed) {
            (Some(s), _) => Some(format!("Gewünscht: {} {}", s.symbol(), s.name())),
            (None, n) if n > 0 => Some(format!("Der Nächste zieht {n}")),
            _ => None,
        };
        PhoneScreen::Cards {
            desk: CardDesk {
                prompt,
                info,
                table: vec![DeskCard { card: top.card, enabled: false }],
                hand: hand.iter().zip(enabled).map(|(&card, enabled)| DeskCard { card, enabled }).collect(),
                buttons,
            },
        }
    }

    /// With the second-to-last card, "Mau!" comes first: playing without it costs a card.
    fn play_buttons(&self, seat: usize) -> Vec<DeskButton> {
        let play = button("play", "Spielen", ButtonNeeds::OneHand, true);
        if self.hands[seat].len() == 2 {
            vec![button("mau", "Mau! und spielen", ButtonNeeds::OneHand, true), DeskButton { primary: false, ..play }]
        } else {
            vec![play]
        }
    }

    pub(crate) fn tv_view(&self, view: &ViewCtx) -> TvScene {
        let over = matches!(self.stage, Stage::Final { .. });
        let shown = match &self.queued {
            Some((b, from)) if view.now >= *from => Some((b, *from)),
            _ => self.banner.as_ref().map(|(b, at)| (b, *at)),
        };
        let banner = match shown {
            Some((b, at)) if over || view.now < at + self.cfg.banner_ms => b.clone(),
            _ => CardsBanner { player: self.player_at(self.turn), text: " ist dran".into() },
        };
        TvScene::Cards {
            scene: CardsScene {
                game: "Mau-Mau".into(),
                seats: self
                    .ids
                    .iter()
                    .zip(&self.hands)
                    .map(|(&player, h)| CardsSeat {
                        player,
                        cards: h.len() as u8,
                        open: vec![],
                        worth: None,
                        marker: (h.len() == 1).then(|| "Mau!".into()),
                    })
                    .collect(),
                piles: vec![CardsPile { cards: self.discard[self.discard.len().saturating_sub(PILE_SHOWN)..].to_vec(), count: self.discard.len() as u16 }],
                deck: self.deck.len() as u16,
                trump: None,
                turn: (!over).then(|| self.player_at(self.turn)),
                wish: self.wish,
                note: (self.owed > 0).then(|| format!("Zieh {}", self.owed)),
                banner: Some(banner),
                events: self.events.clone(),
                ranking: match &self.stage {
                    Stage::Final { ranking } => Some(ranking.iter().map(|&s| self.player_at(s)).collect()),
                    _ => None,
                },
            },
        }
    }
}
