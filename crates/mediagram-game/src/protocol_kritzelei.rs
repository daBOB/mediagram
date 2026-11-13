//! Wire types of Kritzelei's TV scene. The drawing itself travels apart, as
//! `Draw` messages. The word is in none of it before the reveal.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{Clock, PlayerId, ServerTime};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct KritzeleiScene {
    /// 1-based.
    pub turn: u32,
    pub turns: u32,
    pub drawer: PlayerId,
    /// The drawing board is connected.
    pub board: bool,
    pub stage: KritzeleiStage,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "stage", rename_all = "snake_case")]
#[ts(export)]
pub enum KritzeleiStage {
    /// The iPad goes to the drawer; the word stays hidden on it until they take the turn.
    Handover,
    Drawing { clock: Clock },
    /// The drawer is picking who got it; the clock stands.
    Picking { clock: Clock },
    /// `guesser` is `None` when nobody got it.
    Reveal { word: String, guesser: Option<PlayerId>, ends_at: ServerTime },
    Final,
}
