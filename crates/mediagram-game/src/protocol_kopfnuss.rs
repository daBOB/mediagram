//! Wire types of Kopfnuss: the tricks players send each other and the TV scene.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{PlayerId, QuizScene, ServerTime};

/// A nuisance one player puts on another player's answer buttons. The phone
/// draws it; the host only says which tricks a phone carries.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum Trick {
    /// The answer buttons swap places every 1.5 s.
    Wechselbad,
    /// The answer texts appear letter by letter.
    Schneckenpost,
    /// The answer texts are mirrored.
    Spiegel,
    /// An answer has to be held for a second to count.
    Kleber,
}

impl Trick {
    pub const ALL: [Trick; 4] = [Self::Wechselbad, Self::Schneckenpost, Self::Spiegel, Self::Kleber];

    pub fn name(self) -> &'static str {
        match self {
            Self::Wechselbad => "Wechselbad",
            Self::Schneckenpost => "Schneckenpost",
            Self::Spiegel => "Spiegel",
            Self::Kleber => "Kleber",
        }
    }

    /// What the victim will suffer, for the pick button.
    pub fn hint(self) -> &'static str {
        match self {
            Self::Wechselbad => "Knöpfe tauschen die Plätze",
            Self::Schneckenpost => "Antworten tröpfeln herein",
            // Short words: a phone button is narrow, and a long one breaks mid-word.
            Self::Spiegel => "Schrift steht verkehrt herum",
            Self::Kleber => "Antwort eine Sekunde halten",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct KopfnussScene {
    /// 1-based; 0 once the finale runs.
    pub round: u8,
    pub rounds: u8,
    pub stage: KopfnussStage,
}

/// One trick sent this round, in the order they land.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct TrickHit {
    pub from: PlayerId,
    pub to: PlayerId,
    pub trick: Trick,
    /// The target was full (three tricks) or already carried this one.
    pub bounced: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct TrickLoad {
    pub player: PlayerId,
    pub tricks: Vec<Trick>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct Climber {
    pub player: PlayerId,
    pub step: u8,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "stage", rename_all = "snake_case")]
#[ts(export)]
pub enum KopfnussStage {
    /// Phones vote a category and send a trick; votes stay secret.
    Pick {
        categories: Vec<String>,
        done: Vec<PlayerId>,
        closes_at: ServerTime,
    },
    Resolve {
        categories: Vec<String>,
        /// Votes per offered category, same order.
        votes: Vec<u8>,
        chosen: u8,
        hits: Vec<TrickHit>,
        ends_at: ServerTime,
    },
    Question {
        category: String,
        quiz: QuizScene,
        tricks: Vec<TrickLoad>,
    },
    /// Gipfelsturm: everyone climbs on their own question stream.
    Climb {
        steps: u8,
        climbers: Vec<Climber>,
        ends_at: ServerTime,
    },
    Fact {
        winner: Option<PlayerId>,
        text: String,
        ends_at: ServerTime,
    },
    /// Players in finishing order: the climb decides, not the points.
    Final { order: Vec<PlayerId> },
}
