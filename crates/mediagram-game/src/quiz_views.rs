//! What the phones and the TV show for each quiz stage.

use crate::game::ViewCtx;
use crate::protocol::{
    AnswerColor, ChoiceOption, PhoneScreen, PlayerId, QuizQuestionView, QuizResult, QuizScene,
    QuizStage, Tone, TvScene,
};
use crate::quiz::Quiz;
use crate::quiz_config::{Round, Stage};
use crate::room_seats::Player;

/// Competition ranking: equal scores share a place.
pub(crate) fn rank_of(players: &[Player], id: PlayerId) -> (u8, i32) {
    let score = players.iter().find(|p| p.id == id).map_or(0, |p| p.score);
    let better = players.iter().filter(|p| p.score > score).count();
    (better as u8 + 1, score)
}

/// The final screen of a game that places players by `order`, not by points.
pub(crate) fn final_by_order(order: &[PlayerId], view: &ViewCtx, player: PlayerId) -> PhoneScreen {
    let rank = order.iter().position(|&p| p == player).map_or(order.len() + 1, |i| i + 1);
    PhoneScreen::Final {
        rank: rank as u8,
        of: view.players.len() as u8,
        score: view.players.iter().find(|p| p.id == player).map_or(0, |p| p.score),
        can_restart: view.leader == Some(player),
    }
}

impl Round {
    pub(crate) fn view(&self) -> QuizQuestionView {
        QuizQuestionView {
            category: self.category.clone(),
            text: self.text.clone(),
            options: self.options.clone(),
            image: self.image.clone(),
        }
    }
}

impl Quiz {
    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        match self.stage {
            Stage::Intro { opens_at, closes_at } | Stage::Open { opens_at, closes_at } => {
                PhoneScreen::Choice {
                    prompt: Some(self.round().category.clone()),
                    options: self
                        .round()
                        .options
                        .iter()
                        .zip(AnswerColor::ALL)
                        .map(|(label, color)| ChoiceOption { label: label.clone(), color })
                        .collect(),
                    opens_at,
                    closes_at: Some(closes_at),
                    picked: self.answers.get(&player).map(|a| a.choice),
                    tricks: vec![],
                    numbered: false,
                    status: None,
                }
            }
            Stage::Reveal { .. } => {
                let round = self.round();
                let right = &round.options[usize::from(round.correct)];
                match self.results.iter().find(|r| r.player == player) {
                    Some(QuizResult { choice: Some(c), ms, points, .. }) if *c == round.correct => {
                        PhoneScreen::Outcome {
                            title: "Richtig!".into(),
                            text: ms.map(|ms| format!("in {:.1} s", f64::from(ms) / 1000.0).replace('.', ",")),
                            tone: Tone::Good,
                            points: *points,
                            keys: vec![],
                            show_score: true,
                        }
                    }
                    Some(QuizResult { choice: Some(_), .. }) => PhoneScreen::Outcome {
                        title: "Leider falsch".into(),
                        text: Some(format!("Richtig war: {right}")),
                        tone: Tone::Bad,
                        points: 0,
                        keys: vec![],
                        show_score: true,
                    },
                    _ => PhoneScreen::Outcome {
                        title: "Zu spät".into(),
                        text: Some(format!("Richtig war: {right}")),
                        tone: Tone::Neutral,
                        points: 0,
                        keys: vec![],
                        show_score: true,
                    },
                }
            }
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
            Stage::Intro { opens_at, closes_at } => {
                QuizStage::Intro { question: self.round().view(), opens_at, closes_at }
            }
            Stage::Open { opens_at, closes_at } => QuizStage::Open {
                question: self.round().view(),
                opens_at,
                closes_at,
                answered: self.answers.keys().copied().collect(),
            },
            Stage::Reveal { ends_at } => QuizStage::Reveal {
                question: self.round().view(),
                correct: self.round().correct,
                results: self.results.clone(),
                ends_at,
            },
            Stage::Final => QuizStage::Final,
        };
        TvScene::Quiz {
            scene: QuizScene {
                round: (self.index.min(self.rounds.len() - 1) + 1) as u8,
                rounds: self.rounds.len() as u8,
                stage,
            },
        }
    }
}
