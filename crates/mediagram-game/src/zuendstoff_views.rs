//! What the phones and the TV show in Zündstoff. The holder's phone lists whom
//! to throw to, the target's shows one big Fangen!; everyone else watches the TV.

use crate::game::{ViewCtx, name, unasked};
use crate::protocol::{
    AnswerColor, Buzz, ChoiceOption, PhoneScreen, PlayerId, ServerTime, Tone, TvScene, ZuendstoffScene, ZuendstoffSeat,
    ZuendstoffStage,
};
use crate::zuendstoff::ZuendstoffGame;
use crate::zuendstoff_config::{Catch, Stage};

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

/// Live from `opens_at` with no clock: the phone gives no hint of the fuse or the catch window.
fn choice(prompt: String, options: Vec<ChoiceOption>, opens_at: f64, picked: Option<u8>) -> PhoneScreen {
    PhoneScreen::Choice {
        prompt: Some(prompt),
        options,
        opens_at,
        closes_at: None,
        picked,
        tricks: Vec::new(),
        numbered: false,
        status: None,
    }
}

impl ZuendstoffGame {
    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let mine = self.lives_of(player);
        let watch = |title: String| PhoneScreen::Wait {
            title,
            text: Some(if mine > 0 {
                format!("{} Halte dich bereit!", lives_text(mine))
            } else {
                "Du bist raus – schau zu, wer übrig bleibt.".into()
            }),
        };
        match self.stage {
            Stage::Final => unasked(),
            Stage::Boom { victim, .. } => PhoneScreen::Outcome {
                title: if victim == player { "BUMM!".into() } else { format!("BUMM bei {}!", name(view.players, victim)) },
                text: Some(lives_text(mine)),
                tone: if victim == player { Tone::Bad } else if mine > 0 { Tone::Good } else { Tone::Muted },
                points: 0,
                keys: Vec::new(),
                show_score: false,
            },
            Stage::Hold { ask, .. } if ask.who() != player => {
                watch(format!("{} hat die Bombe", name(view.players, ask.who())))
            }
            Stage::Hold { dropped: true, .. } => {
                PhoneScreen::Wait { title: "Heb die Bombe auf …".into(), text: Some(lives_text(mine)) }
            }
            Stage::Hold { ready_at, .. } => {
                let names = self.targets(view.players, player).into_iter().map(|id| name(view.players, id).to_string());
                choice("Wirf die Bombe – an wen?".into(), buttons(names), ready_at, None)
            }
            Stage::Flight { from, to, thrown_at, catch, .. } if to == player => choice(
                format!("{} wirft dir die Bombe zu!", name(view.players, from)),
                buttons(["Fangen!".to_string()]),
                thrown_at,
                (catch != Catch::Pending).then_some(0),
            ),
            Stage::Flight { from, to, .. } => {
                watch(format!("{} wirft zu {}", name(view.players, from), name(view.players, to)))
            }
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
            Stage::Hold { ask, ready_at, dropped } => ZuendstoffStage::Hold { holder: ask.who(), ready_at, dropped },
            Stage::Flight { from, to, thrown_at, lands_at, catch } => ZuendstoffStage::Flight {
                from,
                to,
                thrown_at,
                lands_at,
                caught: match catch {
                    Catch::Pending => None,
                    Catch::Caught => Some(true),
                    Catch::Fumbled => Some(false),
                },
            },
        };
        TvScene::Zuendstoff { scene: ZuendstoffScene { round: self.round, seats, stage } }
    }

    pub(crate) fn buzz(&self, player: PlayerId) -> Option<Buzz> {
        match self.stage {
            Stage::Boom { victim, .. } if victim == player => Some(Buzz::Wrong),
            // the target's phone buzzes from the throw, before the bomb leaves the hand
            _ if self.bearer(ServerTime::INFINITY) == Some(player) => Some(Buzz::Turn),
            _ => None,
        }
    }
}
