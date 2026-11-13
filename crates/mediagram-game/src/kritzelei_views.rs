//! What the phones, the TV and the drawing board show for each Kritzelei stage.
//! The word reaches only the board until the reveal.

use crate::game::ViewCtx;
use crate::kritzelei::{KritzeleiGame, Stage};
use crate::protocol::{
    BoardPlayer, BoardScreen, Buzz, KritzeleiScene, KritzeleiStage, PhoneScreen, PlayerId, Tone, TvScene,
};
use crate::quiz_views::rank_of;
use crate::room_seats::Player;

fn board_player(p: &Player) -> BoardPlayer {
    BoardPlayer { id: p.id, name: p.name.clone(), color: p.color.into(), avatar: p.avatar.map(Into::into) }
}

fn name_of(view: &ViewCtx, id: PlayerId) -> String {
    view.players.iter().find(|p| p.id == id).map_or_else(String::new, |p| p.name.clone())
}

#[cfg_attr(not(test), allow(dead_code))]
impl KritzeleiGame {
    /// Who got it, as the reveal says it on every surface.
    fn verdict(&self, view: &ViewCtx, guesser: Option<PlayerId>) -> String {
        guesser.map_or_else(|| "Niemand hat's erraten".into(), |g| format!("{} hat's erraten", name_of(view, g)))
    }

    fn drawer_view(&self, view: &ViewCtx) -> BoardPlayer {
        let id = self.drawer();
        view.players.iter().find(|p| p.id == id).map_or_else(
            || BoardPlayer { id, name: String::new(), color: String::new(), avatar: None },
            board_player,
        )
    }

    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let drawer = self.drawer();
        match self.stage {
            Stage::Handover | Stage::Drawing | Stage::Picking if player == drawer => {
                PhoneScreen::Wait { title: "Du zeichnest – hol dir das iPad".into(), text: None }
            }
            Stage::Handover | Stage::Drawing | Stage::Picking => PhoneScreen::Wait {
                title: "Rate laut!".into(),
                text: Some(format!("{} zeichnet.", name_of(view, drawer))),
            },
            Stage::Reveal { guesser, .. } => {
                let line = self.verdict(view, guesser);
                let word = self.word().to_string();
                if guesser == Some(player) {
                    return PhoneScreen::Outcome {
                        title: "Richtig!".into(), text: Some(word), tone: Tone::Good, points: self.cfg.guesser_points,
                        keys: vec![], show_score: true,
                    };
                }
                let (tone, points) = if player == drawer && guesser.is_some() {
                    (Tone::Good, self.cfg.drawer_points)
                } else {
                    (Tone::Neutral, 0)
                };
                PhoneScreen::Outcome { title: word, text: Some(line), tone, points, keys: vec![], show_score: true }
            }
            Stage::Final => PhoneScreen::Final {
                rank: rank_of(view.players, player).0,
                of: view.players.len() as u8,
                score: rank_of(view.players, player).1,
                can_restart: view.leader == Some(player),
            },
        }
    }

    pub(crate) fn buzz(&self, player: PlayerId) -> Option<Buzz> {
        match self.stage {
            Stage::Handover | Stage::Drawing | Stage::Picking if player == self.drawer() => Some(Buzz::Turn),
            Stage::Reveal { guesser, .. } if guesser == Some(player) => Some(Buzz::Accepted),
            _ => None,
        }
    }

    /// The word only from the reveal on.
    pub(crate) fn tv_view(&self, _view: &ViewCtx) -> TvScene {
        let total = self.cfg.draw_ms;
        let stage = match self.stage {
            Stage::Handover => KritzeleiStage::Handover,
            Stage::Drawing => KritzeleiStage::Drawing { clock: self.clock.wire(total) },
            Stage::Picking => KritzeleiStage::Picking { clock: self.clock.wire(total) },
            Stage::Reveal { guesser, ends_at } => KritzeleiStage::Reveal { word: self.word().into(), guesser, ends_at },
            Stage::Final => KritzeleiStage::Final,
        };
        let scene = KritzeleiScene {
            turn: self.index as u32 + 1,
            turns: self.turns.len() as u32,
            drawer: self.drawer(),
            board: self.board_present,
            stage,
        };
        TvScene::Kritzelei { scene }
    }

    pub(crate) fn board_view(&self, view: &ViewCtx) -> BoardScreen {
        let turn = self.index as u32 + 1;
        let wait = |title: &str, text: Option<String>| BoardScreen::Wait { title: title.into(), text, can_leave: false };
        match self.stage {
            Stage::Handover => BoardScreen::Handover { turn, drawer: self.drawer_view(view) },
            Stage::Drawing => BoardScreen::Drawing {
                turn, drawer: self.drawer_view(view), word: self.word().into(), clock: self.clock.wire(self.cfg.draw_ms),
            },
            Stage::Picking => BoardScreen::Picking {
                turn,
                drawer: self.drawer_view(view),
                word: self.word().into(),
                clock: self.clock.wire(self.cfg.draw_ms),
                guessers: view.players.iter().filter(|p| p.id != self.drawer()).map(board_player).collect(),
                can_go_back: self.clock.left(view.now) > 0.0,
            },
            Stage::Reveal { guesser, .. } => wait(self.word(), Some(self.verdict(view, guesser))),
            Stage::Final => wait("Spiel vorbei", Some("Die Siegerehrung läuft auf dem Fernseher.".into())),
        }
    }
}

#[cfg(test)]
#[path = "kritzelei_views_tests.rs"]
mod tests;
