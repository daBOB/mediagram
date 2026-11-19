//! Wire types of Letztes Wort: its TV scene and its own phone screen.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{PlayerId, ServerTime};

/// The TV never carries an answer: only the question, the stick and who is where.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct LetztesWortScene {
    /// 1-based.
    pub round: u8,
    pub rounds: u8,
    pub question: String,
    /// This round's host, who judges and does not play.
    pub host: PlayerId,
    pub holder: Option<PlayerId>,
    /// Out for this round after a wrong answer.
    pub out: Vec<PlayerId>,
    pub stage: LetztesWortStage,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "stage", rename_all = "snake_case")]
#[ts(export)]
pub enum LetztesWortStage {
    /// The buzzers are open from `opens_at`.
    Open { opens_at: ServerTime },
    /// `player` buzzed and answers out loud; `buzz` counts the round's buzzes, so each sounds once.
    Floor { player: PlayerId, buzz: u16 },
    /// The hidden time ran out (or nobody could take the stick any more).
    RoundEnd { winner: Option<PlayerId>, next_host: Option<PlayerId> },
    /// Players by points, winner first.
    Final { order: Vec<PlayerId> },
}

/// A Letztes Wort phone: a player's buzzer, the stick, or the round host's desk.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "view", rename_all = "snake_case")]
#[ts(export)]
pub enum LetztesWortPhone {
    /// Pressing it buzzes (`Choose`, any index) while `live` from `opens_at`. `button` is the key's
    /// own text when it is not live; `line` the status under the question.
    Buzzer { question: String, opens_at: ServerTime, live: bool, button: String, line: String },
    /// A player's avatar with the stick: this phone holds it, or it won the round.
    Stick { name: String, avatar: Option<String>, title: String, text: String },
    /// The round's host. While someone has the `floor`, `Choose` judges: an answer's index, then
    /// `answers.len()` for Falsch, then `answers.len() + 1` for Auch richtig.
    Host { question: String, answers: Vec<HostAnswer>, floor: Option<String> },
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct HostAnswer {
    pub text: String,
    /// Who gave it this round; a given answer is used up.
    pub given_by: Option<String>,
}
