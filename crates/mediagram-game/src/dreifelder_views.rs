//! What the phones and the TV show for each Dreifelder stage.

use crate::dreifelder::{DreifelderGame, Stage};
use crate::game::ViewCtx;
use crate::protocol::{
    AnswerColor, ChoiceOption, ChoiceStatus, DreifelderScene, DreifelderStage, FieldPick, OutPlayer, OutcomeKey,
    PhoneScreen, PlayerId, Tone, TvScene,
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
        let (alive, all) = self.head_count();
        let (prompt, status) = if self.tally.is_alive(player) {
            (round.category.clone(), ChoiceStatus { text: format!("Du bist drin · noch {alive} von {all}"), out: false })
        } else {
            ("Raus – rate mit".into(), ChoiceStatus { text: format!("Zählt für deinen Platz · noch {alive} von {all} dabei"), out: true })
        };
        PhoneScreen::Choice {
            prompt: Some(prompt),
            options: round.options.iter().zip(AnswerColor::ALL).map(|(l, color)| ChoiceOption { label: l.clone(), color }).collect(),
            opens_at,
            closes_at: Some(closes_at),
            picked: self.answers.get(&player).map(|a| a.choice),
            tricks: vec![],
            numbered: true,
            status: Some(status),
        }
    }

    /// Players still in, and everyone who started.
    fn head_count(&self) -> (usize, usize) {
        (self.tally.alive().len(), self.tally.players().len())
    }

    /// The reveal on `player`'s phone: on, out, or how their ghost guess went.
    fn outcome(&self, player: PlayerId) -> PhoneScreen {
        let round = self.round();
        let picked = self.answers.get(&player).map(|a| a.choice);
        let right = picked == Some(round.correct);
        let (alive, all) = self.head_count();
        let left = format!("{alive} von {all} dabei");
        // Once the game is over there is nothing left to go on to.
        let more = |text: String| (!self.over).then_some(text);
        let (title, tone, text) = if self.eliminated.contains(&player) {
            ("Raus!", Tone::Bad, more("Rate weiter – Treffer zählen für deinen Platz.".into()))
        } else if self.tally.is_alive(player) {
            let title = match (right, self.over) {
                (true, true) => "Richtig!",
                (true, false) => "Weiter!",
                // A leaver can still go out in a round nobody got right.
                (false, _) if self.eliminated.is_empty() && !self.over => "Alle daneben – alle bleiben drin",
                (false, _) => "Alle daneben",
            };
            (title, if right { Tone::Good } else { Tone::Neutral }, more(format!("Noch {left}")))
        } else {
            let title = if right { "Richtig" } else if picked.is_some() { "Falsch" } else { "Kein Tipp" };
            // Ghost guesses do count: they rank players who went out in the same round.
            (title, Tone::Muted, more(format!("Zählt für deinen Platz · noch {left}")))
        };
        let key = |i: u8, caption: &str, crossed| OutcomeKey {
            caption: caption.into(),
            number: i + 1,
            label: round.options[usize::from(i)].clone(),
            color: AnswerColor::ALL[usize::from(i)],
            crossed,
        };
        let missed = picked.filter(|_| !right).map(|p| key(p, "Dein Tipp", true));
        PhoneScreen::Outcome {
            title: title.into(),
            text,
            tone,
            points: 0,
            keys: missed.into_iter().chain([key(round.correct, "Richtig", false)]).collect(),
            show_score: false,
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
