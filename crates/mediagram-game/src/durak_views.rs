//! What the phones and the TV show during Durak.

use crate::cards::Card;
use crate::durak::{DurakGame, Stage};
use crate::durak_rules::{beats, can_throw};
use crate::game::ViewCtx;
use crate::protocol::{
    ButtonNeeds, CardDesk, CardsBanner, CardsPile, CardsScene, CardsSeat, DeskButton, DeskCard, PhoneScreen, PlayerId, TvScene,
};

fn button(id: &str, label: &str, needs: ButtonNeeds, primary: bool) -> DeskButton {
    DeskButton { id: id.into(), label: label.into(), needs, primary, suit: None }
}

fn name(view: &ViewCtx, id: PlayerId) -> String {
    view.players.iter().find(|p| p.id == id).map_or_else(|| "Jemand".into(), |p| p.name.clone())
}

impl DurakGame {
    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let Some(me) = self.seat_of(player) else {
            return PhoneScreen::Wait { title: "Durak läuft".into(), text: Some("Du spielst in der nächsten Runde mit.".into()) };
        };
        if let Stage::Final { ranking, .. } = &self.stage {
            return PhoneScreen::Final {
                rank: ranking.iter().position(|&s| s == me).map_or(1, |r| r as u8 + 1),
                of: self.ids.len() as u8,
                score: view.players.iter().find(|p| p.id == player).map_or(0, |p| p.score),
                can_restart: view.leader == Some(player),
            };
        }
        let hand = &self.hands[me];
        let table = self.table();
        let trump = self.trump_suit();
        let who = |seat: usize| name(view, self.player_at(seat));
        let mine = self.actor() == Some(me);
        let (prompt, enabled, buttons): (String, Vec<bool>, Vec<DeskButton>) = match (&self.stage, mine) {
            (Stage::Attack { .. }, true) => ("Du greifst an – spiele eine Karte".into(), vec![true; hand.len()], vec![button("play", "Ausspielen", ButtonNeeds::OneHand, true)]),
            (Stage::Defend { .. }, true) => (
                "Schlage die Karten oder nimm sie auf".into(),
                hand.iter().map(|&c| self.bout.iter().any(|&(a, d)| d.is_none() && beats(trump, a, c))).collect(),
                vec![button("beat", "Schlagen", ButtonNeeds::HandAndTable, true), button("take", "Aufnehmen", ButtonNeeds::Nothing, false)],
            ),
            (Stage::Throw { taking, .. }, true) => (
                if *taking { format!("{} nimmt auf – lege noch dazu?", who(self.defender())) } else { "Du darfst dazulegen".into() },
                hand.iter().map(|&c| self.bout.len() < self.limit && can_throw(&table, c)).collect(),
                vec![button("play", "Dazulegen", ButtonNeeds::OneHand, true), button("done", "Fertig", ButtonNeeds::Nothing, false)],
            ),
            (Stage::Attack { .. }, false) => (format!("{} greift an", who(self.attacker)), vec![false; hand.len()], vec![]),
            (Stage::Defend { .. }, false) => (format!("{} verteidigt", who(self.defender())), vec![false; hand.len()], vec![]),
            (Stage::Throw { .. }, false) => (format!("{} darf dazulegen", who(self.thrower_seat())), vec![false; hand.len()], vec![]),
            _ => (self.settle_text(view), vec![false; hand.len()], vec![]),
        };
        let role = if me == self.attacker { "Du greifst an" } else if me == self.defender() { "Du verteidigst" } else { "" };
        let defending = matches!(self.stage, Stage::Defend { .. }) && mine;
        let mut shown = Vec::new();
        for &(a, d) in &self.bout {
            shown.push(DeskCard { card: a, enabled: defending && d.is_none() });
            shown.extend(d.map(|card| DeskCard { card, enabled: false }));
        }
        let info = format!("Trumpf {} {} · Stapel {} · {role}", trump.symbol(), trump.name(), self.deck.len());
        PhoneScreen::Cards {
            desk: CardDesk {
                prompt,
                info: Some(info.trim_end_matches(&[' ', '·'][..]).into()),
                table: shown,
                hand: hand.iter().zip(enabled).map(|(&card, enabled)| DeskCard { card, enabled }).collect(),
                buttons,
            },
        }
    }

    fn settle_text(&self, view: &ViewCtx) -> String {
        match self.stage {
            Stage::Settle { taken: true, .. } => format!("{} nimmt die Karten auf", name(view, self.player_at(self.defender()))),
            _ => "Bito – alles geschlagen".into(),
        }
    }

    fn marker(&self, seat: usize) -> Option<String> {
        let text = match &self.stage {
            Stage::Final { durak, .. } if *durak == Some(seat) => "Durak",
            _ if self.out.contains(&seat) => "Raus",
            Stage::Final { .. } => return None,
            _ if seat == self.attacker => "Angreifer",
            _ if seat == self.defender() => "Verteidiger",
            _ => return None,
        };
        Some(text.into())
    }

    pub(crate) fn tv_view(&self, view: &ViewCtx) -> TvScene {
        let over = matches!(self.stage, Stage::Final { .. });
        let banner = match &self.banner {
            Some((b, at)) if over || matches!(self.stage, Stage::Settle { .. }) || view.now < at + self.cfg.banner_ms => b.clone(),
            _ => self.status_banner(),
        };
        let pair = |&(a, d): &(Card, Option<Card>)| {
            let cards: Vec<Card> = std::iter::once(a).chain(d).collect();
            CardsPile { count: cards.len() as u16, cards }
        };
        TvScene::Cards {
            scene: CardsScene {
                game: "Durak".into(),
                seats: self
                    .ids
                    .iter()
                    .zip(&self.hands)
                    .enumerate()
                    .map(|(seat, (&player, h))| CardsSeat { player, cards: h.len() as u8, open: vec![], worth: None, marker: self.marker(seat) })
                    .collect(),
                piles: self.bout.iter().map(pair).collect(),
                deck: self.deck.len() as u16,
                trump: (!self.deck.is_empty()).then_some(self.trump),
                turn: self.actor().map(|s| self.player_at(s)),
                wish: None,
                note: Some(format!("Trumpf {} {}", self.trump.suit.symbol(), self.trump.suit.name())),
                banner: Some(banner),
                events: self.events.clone(),
                ranking: match &self.stage {
                    Stage::Final { ranking, .. } => Some(ranking.iter().map(|&s| self.player_at(s)).collect()),
                    _ => None,
                },
            },
        }
    }

    /// What the banner says between happenings: who has to act.
    fn status_banner(&self) -> CardsBanner {
        let seat = self.actor().unwrap_or(self.attacker);
        let text = match self.stage {
            Stage::Attack { .. } => " greift an",
            Stage::Defend { .. } => " verteidigt",
            Stage::Throw { taking: true, .. } => " darf noch dazulegen",
            _ => " darf dazulegen",
        };
        CardsBanner { player: self.player_at(seat), text: text.into() }
    }
}
