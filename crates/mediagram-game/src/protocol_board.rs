//! Wire types of the drawing board, the third client role: what the board
//! sends (`BoardAct`, `DrawOp`) and what the host shows it (`BoardScreen`).
//! Points are x,y pairs, each `0..=10000` of the paper's width and height.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{PlayerId, ServerTime};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "t", rename_all = "snake_case")]
#[ts(export)]
pub enum BoardAct {
    Ready,
    Skip,
    Guessed,
    KeepDrawing,
    /// Who got it; `None` is "nobody".
    Pick { player: Option<PlayerId> },
    Draw { op: DrawOp },
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "t", rename_all = "snake_case")]
#[ts(export)]
pub enum DrawOp {
    /// A new stroke: palette index, width index and the first points.
    Line { color: u8, width: u8, points: Vec<u16> },
    /// More points on the last stroke.
    More { points: Vec<u16> },
    Undo,
    Clear,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct BoardPlayer {
    pub id: PlayerId,
    pub name: String,
    /// Hex colour, e.g. `#2f6fde`.
    pub color: String,
    /// Catalogue id, see `avatars::AVATARS`.
    pub avatar: Option<String>,
}

/// Runs while `ends_at` is set; paused with `left_ms` otherwise.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct Clock {
    pub ends_at: Option<ServerTime>,
    pub left_ms: f64,
    pub total_ms: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "kind", rename_all = "snake_case")]
#[ts(export)]
pub enum BoardScreen {
    /// Rendered by the page: lobby, between turns, the reveal, another game.
    Wait { title: String, text: Option<String>, can_leave: bool },
    /// Native. The word stays hidden until "Ich bin dran".
    Handover { turn: u32, drawer: BoardPlayer },
    Drawing { turn: u32, drawer: BoardPlayer, word: String, clock: Clock },
    /// The who-got-it list. `can_go_back` is false once the time is up.
    Picking { turn: u32, drawer: BoardPlayer, word: String, clock: Clock, guessers: Vec<BoardPlayer>, can_go_back: bool },
}

/// A short vibration on a phone.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum Buzz {
    Turn,
    Accepted,
    Wrong,
    Won,
}
