//! What the phones and the TV show in Zündstoff. Only the holder's phone
//! asks anything; everyone else waits and watches the TV.

use crate::game::{ViewCtx, name};
use crate::protocol::{
    AnswerColor, ChoiceOption, ChoiceStatus, PhoneScreen, PlayerId, Tone, TvScene, ZuendstoffScene, ZuendstoffSeat,
    ZuendstoffStage,
};
use crate::quiz_views::final_by_order;
use crate::zuendstoff::ZuendstoffGame;
use crate::zuendstoff_config::{Stage, Step};

fn buttons(labels: impl IntoIterator<Item = String>) -> Vec<ChoiceOption> {
    labels.into_iter().zip(AnswerColor::ALL.iter().cycle()).map(|(label, &color)| ChoiceOption { label, color }).collect()
}

fn lives_text(lives: u8) -> String {
    match lives {
        0 => "Du bist raus.".into(),
        1 => "Du hast noch ein Leben.".into(),
        n => format!("Du hast noch {n} Leben."),
    }
}

fn choice(prompt: String, options: Vec<ChoiceOption>, opens_at: f64, status: Option<&str>) -> PhoneScreen {
    PhoneScreen::Choice {
        prompt: Some(prompt),
        options,
        opens_at,
        closes_at: None,
        picked: None,
        tricks: Vec::new(),
        numbered: false,
        status: status.map(|text| ChoiceStatus { text: text.into(), out: false }),
    }
}

impl ZuendstoffGame {
    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let mine = self.lives_of(player);
        match self.stage {
            Stage::Final => final_by_order(&self.order, view, player),
            Stage::Boom { victim, .. } => PhoneScreen::Outcome {
                title: if victim == player { "BUMM!".into() } else { format!("BUMM bei {}!", name(view.players, victim)) },
                text: Some(lives_text(mine)),
                tone: if victim == player { Tone::Bad } else if mine > 0 { Tone::Good } else { Tone::Muted },
                points: 0,
                keys: Vec::new(),
                show_score: false,
            },
            Stage::Lit { ask, .. } if ask.who() != player => PhoneScreen::Wait {
                title: format!("{} hat die Bombe", name(view.players, ask.who())),
                text: Some(if mine > 0 {
                    format!("{} Halte dich bereit!", lives_text(mine))
                } else {
                    "Du bist raus – schau zu, wer übrig bleibt.".into()
                }),
            },
            Stage::Lit { ask, step: Step::Throw } => {
                let names = self.targets(view.players, player).into_iter().map(|id| name(view.players, id).to_string());
                choice("Richtig! Wirf die Bombe – an wen?".into(), buttons(names), ask.opened_at(), None)
            }
            Stage::Lit { step: Step::Answer { opens_at, wrong }, .. } => choice(
                self.question.text.clone(),
                buttons(self.question.options.iter().cloned()),
                opens_at,
                // Still true after the pause, so it must read right with the keys live too.
                Some(if wrong { "Falsch – hier kommt die nächste!" } else { "Richtig antworten, dann werfen!" }),
            ),
        }
    }

    pub(crate) fn tv_view(&self, view: &ViewCtx) -> TvScene {
        let seats = view
            .players
            .iter()
            .filter(|p| self.lives.contains_key(&p.id))
            .map(|p| ZuendstoffSeat { player: p.id, lives: self.lives_of(p.id) })
            .collect();
        let stage = match self.stage {
            Stage::Final => ZuendstoffStage::Final { order: self.order.clone() },
            Stage::Boom { victim, .. } => ZuendstoffStage::Boom { victim, out: !self.alive(victim) },
            Stage::Lit { ask, step: Step::Throw } => ZuendstoffStage::Throw { holder: ask.who() },
            Stage::Lit { ask, step: Step::Answer { opens_at, wrong } } => ZuendstoffStage::Answer {
                holder: ask.who(),
                question: self.question.view(),
                opens_at,
                wrong,
                thrown_by: self.thrown.filter(|&(_, to)| to == ask.who()).map(|(from, _)| from),
            },
        };
        TvScene::Zuendstoff { scene: ZuendstoffScene { round: self.round, seats, stage } }
    }
}
