//! What the phones and the TV show for each Dreifelder stage.

use crate::dreifelder::{DreifelderGame, Stage};
use crate::game::ViewCtx;
use crate::protocol::{
    AnswerColor, ChoiceOption, DreifelderScene, DreifelderStage, FieldPick, OutPlayer, PhoneScreen, PlayerId, Tone,
    TvScene,
};
use crate::quiz_views::final_by_order;

impl DreifelderGame {
    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        if self.stage == Stage::Final {
            return final_by_order(&self.order, view, player);
        }
        if !self.tally.plays(player) {
            return PhoneScreen::Wait { title: "Dreifelder läuft".into(), text: Some("Beim nächsten Spiel bist du dabei.".into()) };
        }
        let Stage::Question { opens_at, closes_at } = self.stage else { return self.outcome(player) };
        let round = self.round();
        let prompt = if self.tally.is_alive(player) { round.category.clone() } else { "Du bist raus – rate mit".into() };
        PhoneScreen::Choice {
            prompt: Some(prompt),
            options: round.options.iter().zip(AnswerColor::ALL).map(|(l, color)| ChoiceOption { label: l.clone(), color }).collect(),
            opens_at,
            closes_at: Some(closes_at),
            picked: self.answers.get(&player).map(|a| a.choice),
            tricks: vec![],
        }
    }

    /// The reveal on `player`'s phone: on, out, or how their ghost guess went.
    fn outcome(&self, player: PlayerId) -> PhoneScreen {
        let round = self.round();
        let picked = self.answers.get(&player).map(|a| a.choice);
        let right = picked == Some(round.correct);
        let (title, tone) = if self.eliminated.contains(&player) {
            ("Raus!", Tone::Bad)
        } else if self.tally.is_alive(player) && right {
            (if self.over { "Richtig!" } else { "Weiter!" }, Tone::Good)
        } else if self.tally.is_alive(player) {
            ("Alle daneben – alle bleiben drin", Tone::Neutral)
        } else if right {
            ("Richtig, ohne Wertung", Tone::Neutral)
        } else if picked.is_some() {
            ("Falsch, ohne Wertung", Tone::Neutral)
        } else {
            ("Kein Tipp", Tone::Neutral)
        };
        PhoneScreen::Outcome {
            title: title.into(),
            text: (!right).then(|| format!("Richtig war: {}", round.options[usize::from(round.correct)])),
            tone,
            points: 0,
        }
    }

    pub(crate) fn tv_view(&self) -> TvScene {
        let stage = match self.stage {
            Stage::Question { opens_at, closes_at } => DreifelderStage::Question {
                question: self.round().view(),
                opens_at,
                closes_at,
                answered: self.answers.keys().copied().collect(),
            },
            Stage::Reveal { ends_at } => DreifelderStage::Reveal {
                question: self.round().view(),
                correct: self.round().correct,
                picks: self
                    .tally
                    .players()
                    .into_iter()
                    .map(|player| FieldPick { player, choice: self.answers.get(&player).map(|a| a.choice) })
                    .collect(),
                eliminated: self.eliminated.clone(),
                ends_at,
            },
            Stage::Final => DreifelderStage::Final { order: self.order.clone() },
        };
        let out = self.tally.out().into_iter().map(|(player, r)| OutPlayer { player, round: r as u8 + 1 }).collect();
        let scene = DreifelderScene {
            round: (self.index + 1) as u8,
            rounds: self.rounds.len() as u8,
            alive: self.tally.alive(),
            out,
            stage,
        };
        TvScene::Dreifelder { scene }
    }
}

#[cfg(test)]
#[path = "dreifelder_views_tests.rs"]
mod tests;
