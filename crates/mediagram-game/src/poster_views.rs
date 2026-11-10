//! What the phones and the TV show for each poster stage.

use crate::game::ViewCtx;
use crate::poster::PosterGame;
use crate::poster_config::Stage;
use crate::protocol::{
    AnswerColor, ChoiceOption, PhoneScreen, PlayerId, PosterScene, PosterStage, Tone, TvScene,
};
use crate::quiz_views::rank_of;
use crate::titles::{ImageKind, image_url};

impl PosterGame {
    fn image(&self) -> String {
        image_url(self.round().title, ImageKind::Poster)
    }

    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let round = self.round();
        match self.stage {
            Stage::Showing { .. } if self.locked.contains(&player) => PhoneScreen::Outcome {
                title: "Falsch – diesmal bist du raus.".into(),
                text: None,
                tone: Tone::Bad,
                points: 0,
                keys: vec![],
                show_score: true,
            },
            Stage::Showing { started_at, closes_at } => PhoneScreen::Choice {
                prompt: Some(if round.series { "Welche Serie?" } else { "Welcher Film?" }.into()),
                options: round
                    .options
                    .iter()
                    .zip(AnswerColor::ALL)
                    .map(|(label, color)| ChoiceOption { label: label.clone(), color })
                    .collect(),
                opens_at: started_at,
                closes_at: Some(closes_at),
                picked: self.claims.contains_key(&player).then_some(round.correct),
                tricks: vec![],
                numbered: false,
                status: None,
            },
            Stage::Reveal { .. } => match self.winner {
                Some((w, points)) if w == player => PhoneScreen::Outcome {
                    title: "Richtig!".into(),
                    text: Some(round.name.clone()),
                    tone: Tone::Good,
                    points,
                    keys: vec![],
                    show_score: true,
                },
                _ => PhoneScreen::Outcome {
                    title: "Aufgelöst".into(),
                    text: Some(round.name.clone()),
                    tone: Tone::Neutral,
                    points: 0,
                    keys: vec![],
                    show_score: true,
                },
            },
            Stage::Final => {
                let (rank, score) = rank_of(view.players, player);
                PhoneScreen::Final {
                    rank,
                    of: view.players.len() as u8,
                    score,
                    can_restart: view.leader == Some(player),
                }
            }
        }
    }

    pub(crate) fn tv_view(&self) -> TvScene {
        let stage = match self.stage {
            Stage::Showing { started_at, .. } => PosterStage::Showing {
                image: self.image(),
                started_at,
                step_ms: self.cfg.step_ms,
                steps: self.cfg.steps,
                locked: self.locked.iter().copied().collect(),
            },
            Stage::Reveal { ends_at } => PosterStage::Reveal {
                image: self.image(),
                title: self.round().name.clone(),
                winner: self.winner.map(|w| w.0),
                points: self.winner.map_or(0, |w| w.1),
                ends_at,
            },
            Stage::Final => PosterStage::Final,
        };
        TvScene::Poster {
            scene: PosterScene {
                round: (self.index.min(self.rounds.len() - 1) + 1) as u8,
                rounds: self.rounds.len() as u8,
                stage,
            },
        }
    }
}
