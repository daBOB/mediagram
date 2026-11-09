//! What the phones and the TV show for each Kopfnuss stage.

use crate::game::{ViewCtx, name};
use crate::kopfnuss::{KopfnussGame, Stage};
use crate::kopfnuss_picks::Step;
use crate::protocol::{
    AnswerColor, ChoiceOption, KopfnussScene, KopfnussStage, PhoneScreen, PlayerId, TrickLoad, TvScene,
};
use crate::room_seats::Player;

/// Buttons in the answer colors, repeating them past four.
fn buttons(labels: impl IntoIterator<Item = String>) -> Vec<ChoiceOption> {
    labels.into_iter().zip(AnswerColor::ALL.iter().cycle()).map(|(label, &color)| ChoiceOption { label, color }).collect()
}

impl KopfnussGame {
    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        match self.stage {
            Stage::Pick { closes_at } => {
                let Some(sheet) = self.sheets.get(&player) else {
                    return PhoneScreen::Wait { title: "Gleich geht's weiter".into(), text: None };
                };
                let (prompt, options) = match sheet.step() {
                    Step::Vote => ("Welche Kategorie?".to_string(), buttons(self.categories.iter().cloned())),
                    Step::Trick => (
                        "Welchen Trick verschickst du?".to_string(),
                        buttons(sheet.offered.iter().map(|t| format!("{} – {}", t.name(), t.hint()))),
                    ),
                    Step::Target => (
                        format!("{} – für wen?", sheet.trick.map_or("Trick", |t| t.name())),
                        buttons(sheet.targets.iter().map(|&t| name(view.players, t).to_string())),
                    ),
                    Step::Done => {
                        return PhoneScreen::Wait { title: "Erledigt!".into(), text: Some("Gleich wird ausgezählt …".into()) };
                    }
                };
                PhoneScreen::Choice {
                    prompt: Some(prompt),
                    options,
                    opens_at: sheet.step_at,
                    closes_at: Some(closes_at),
                    picked: None,
                    tricks: vec![],
                }
            }
            Stage::Resolve { .. } => PhoneScreen::Wait {
                title: self.categories.get(usize::from(self.chosen)).cloned().unwrap_or_default(),
                text: Some(self.trick_news(view.players, player)),
            },
            Stage::Question => {
                let Some(quiz) = &self.quiz else { return PhoneScreen::Wait { title: String::new(), text: None } };
                match quiz.phone_view(view, player) {
                    PhoneScreen::Choice { prompt, options, opens_at, closes_at, picked, .. } => PhoneScreen::Choice {
                        prompt,
                        options,
                        opens_at,
                        closes_at,
                        picked,
                        tricks: self.tricks.get(&player).cloned().unwrap_or_default(),
                    },
                    // The contained quiz's own final screen is never Kopfnuss's.
                    PhoneScreen::Final { .. } => PhoneScreen::Wait { title: "Nächste Runde".into(), text: None },
                    other => other,
                }
            }
            Stage::Climb => match &self.climb {
                Some(climb) => climb.phone(player),
                None => PhoneScreen::Wait { title: String::new(), text: None },
            },
            Stage::Fact { .. } => {
                let winner = self.order.first().copied();
                let title = if winner == Some(player) {
                    "Du hast den Gipfel erreicht!".to_string()
                } else {
                    format!("{} hat den Gipfel erreicht", winner.map_or("", |w| name(view.players, w)))
                };
                PhoneScreen::Wait { title, text: Some(self.fact.clone()) }
            }
            Stage::Final => {
                let rank = self.order.iter().position(|&p| p == player).map_or(self.order.len() + 1, |i| i + 1);
                PhoneScreen::Final {
                    rank: rank as u8,
                    of: view.players.len() as u8,
                    score: view.players.iter().find(|p| p.id == player).map_or(0, |p| p.score),
                    can_restart: view.leader == Some(player),
                }
            }
        }
    }

    /// What this round's tricks did to and for `player`, in one line.
    fn trick_news(&self, players: &[Player], player: PlayerId) -> String {
        let mut news = Vec::new();
        if let Some(hit) = self.hits.iter().find(|h| h.from == player) {
            let verb = if hit.bounced { "prallt ab an" } else { "trifft" };
            news.push(format!("Dein {} {verb} {}", hit.trick.name(), name(players, hit.to)));
        }
        match self.tricks.get(&player) {
            Some(t) if !t.is_empty() => {
                news.push(format!("Auf dich: {}", t.iter().map(|t| t.name()).collect::<Vec<_>>().join(", ")))
            }
            _ => news.push("Du bleibst verschont".into()),
        }
        news.join(" · ")
    }

    pub(crate) fn tv_view(&self, view: &ViewCtx) -> TvScene {
        let in_rounds = matches!(self.stage, Stage::Pick { .. } | Stage::Resolve { .. } | Stage::Question);
        let stage = match self.stage {
            Stage::Pick { closes_at } => KopfnussStage::Pick {
                categories: self.categories.clone(),
                done: view
                    .players
                    .iter()
                    .filter(|p| self.sheets.get(&p.id).is_some_and(|s| s.step() == Step::Done))
                    .map(|p| p.id)
                    .collect(),
                closes_at,
            },
            Stage::Resolve { ends_at } => KopfnussStage::Resolve {
                categories: self.categories.clone(),
                votes: self.votes.clone(),
                chosen: self.chosen,
                hits: self.hits.clone(),
                ends_at,
            },
            Stage::Question => match self.quiz.as_ref().map(|q| q.tv_view()) {
                Some(TvScene::Quiz { scene }) => KopfnussStage::Question {
                    category: self.categories.get(usize::from(self.chosen)).cloned().unwrap_or_default(),
                    quiz: scene,
                    tricks: self.tricks.iter().map(|(&player, t)| TrickLoad { player, tricks: t.clone() }).collect(),
                },
                _ => KopfnussStage::Final { order: self.order.clone() },
            },
            Stage::Climb => match &self.climb {
                Some(c) => KopfnussStage::Climb { steps: c.steps(), climbers: c.climbers(), ends_at: c.ends_at },
                None => KopfnussStage::Final { order: self.order.clone() },
            },
            Stage::Fact { ends_at } => {
                KopfnussStage::Fact { winner: self.order.first().copied(), text: self.fact.clone(), ends_at }
            }
            Stage::Final => KopfnussStage::Final { order: self.order.clone() },
        };
        let scene = KopfnussScene {
            round: if in_rounds { self.round as u8 + 1 } else { 0 },
            rounds: self.cfg.rounds as u8,
            stage,
        };
        TvScene::Kopfnuss { scene }
    }
}
