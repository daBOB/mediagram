//! What the phones and the TV show for each Kritzelei stage.

use crate::game::ViewCtx;
use crate::kritzelei::{KritzeleiGame, Stage};
use crate::protocol::{KritzeleiScene, KritzeleiStage, PhoneScreen, PlayerId, TvScene};

#[cfg_attr(not(test), allow(dead_code))]
impl KritzeleiGame {
    pub(crate) fn phone_view(&self, _view: &ViewCtx, _player: PlayerId) -> PhoneScreen {
        PhoneScreen::Wait { title: "Kritzelei".into(), text: None }
    }

    /// The word only from the reveal on.
    pub(crate) fn tv_view(&self) -> TvScene {
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
}
