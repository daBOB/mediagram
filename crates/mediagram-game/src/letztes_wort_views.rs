//! What Letztes Wort's phones and TV show. Only the host's phone ever lists the answers.

use crate::game::{ViewCtx, name, unasked};
use crate::letztes_wort::{LetztesWortGame, Stage};
use crate::protocol::{
    HostAnswer, LetztesWortPhone, LetztesWortScene, LetztesWortStage, PhoneScreen, PlayerId, Tone, TvScene,
};

impl LetztesWortGame {
    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let named = |id: PlayerId| name(view.players, id).to_owned();
        let me = |p: LetztesWortPhone| PhoneScreen::LetztesWort { phone: p };
        let stick = |id: PlayerId, title: String, text: &str| {
            let avatar = view.players.iter().find(|p| p.id == id).and_then(|p| p.avatar).map(str::to_owned);
            me(LetztesWortPhone::Stick { name: named(id), avatar, title, text: text.into() })
        };
        let question = self.prompt.q.clone();
        match self.stage {
            Stage::Final => unasked(),
            Stage::RoundEnd { winner, .. } => {
                let text = if self.next_host() == Some(player) { "Die nächste Frage moderierst du." } else { "Gleich kommt die nächste Frage." };
                match winner {
                    Some(w) if w == player => stick(w, "Du hattest das letzte Wort!".into(), text),
                    Some(w) => stick(w, format!("{} hatte das letzte Wort", named(w)), text),
                    None => PhoneScreen::Wait { title: "Zeit! Niemand hatte den Stab".into(), text: Some(text.into()) },
                }
            }
            _ if player == self.host() => {
                let answers = self.prompt.answers.iter().enumerate().map(|(i, text)| HostAnswer {
                    text: text.clone(),
                    given_by: self.given.iter().find(|&&(a, _)| a == Some(i)).map(|&(_, p)| named(p)),
                });
                let floor = match self.stage {
                    Stage::Floor { player, .. } => Some(named(player)),
                    _ => None,
                };
                me(LetztesWortPhone::Host { question, answers: answers.collect(), floor })
            }
            _ if self.out.contains(&player) => PhoneScreen::Outcome {
                title: "Falsch – du bist raus".into(),
                text: Some("Bei der nächsten Frage bist du wieder dabei.".into()),
                tone: Tone::Bad,
                points: 0,
                keys: vec![],
                show_score: false,
            },
            _ if self.holder == Some(player) => {
                stick(player, "Du hast den Stab!".into(), "Halt ihn, bis die Zeit um ist. Wer jetzt richtig antwortet, nimmt ihn dir weg.")
            }
            Stage::Floor { player: p, .. } => {
                let (button, line) = if p == player {
                    ("Du bist dran!".into(), format!("Sag deine Antwort laut – {} entscheidet", named(self.host())))
                } else {
                    (format!("{} ist dran", named(p)), format!("{} hat gebuzzert – {} entscheidet", named(p), named(self.host())))
                };
                me(LetztesWortPhone::Buzzer { question, opens_at: 0.0, live: false, button, line })
            }
            Stage::Open { opens_at } => {
                let line = match self.holder {
                    Some(h) => format!("{} hat den Stab – hol ihn dir!", named(h)),
                    None => "Wer weiß eine Antwort?".into(),
                };
                me(LetztesWortPhone::Buzzer { question, opens_at, live: true, button: "BUZZ!".into(), line })
            }
        }
    }

    pub(crate) fn tv_view(&self, view: &ViewCtx) -> TvScene {
        let stage = match self.stage {
            Stage::Open { opens_at } => LetztesWortStage::Open { opens_at },
            Stage::Floor { player, .. } => LetztesWortStage::Floor { player, buzz: self.buzzes },
            Stage::RoundEnd { winner, .. } => LetztesWortStage::RoundEnd { winner, next_host: self.next_host() },
            Stage::Final => {
                let mut order: Vec<_> = view.players.iter().map(|p| (p.id, p.score)).collect();
                order.sort_by_key(|&(_, score)| std::cmp::Reverse(score));
                LetztesWortStage::Final { order: order.into_iter().map(|(id, _)| id).collect() }
            }
        };
        TvScene::LetztesWort {
            scene: LetztesWortScene {
                round: self.round,
                rounds: self.rounds,
                question: self.prompt.q.clone(),
                host: self.host(),
                holder: self.holder,
                out: self.out.iter().copied().collect(),
                stage,
            },
        }
    }
}

#[cfg(test)]
#[path = "letztes_wort_views_tests.rs"]
mod tests;
