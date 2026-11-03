//! Wire protocol between the host (TV) and its clients (phones, TV view).
//!
//! JSON over WebSocket. Every message is an internally tagged enum, so the
//! generated TypeScript types are discriminated unions.
//!
//! Times are `ServerTime`: milliseconds since the host started. Clients keep
//! an offset to it via `Ping`/`Pong`, which is what makes "who pressed first"
//! fair regardless of each phone's network latency.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

pub use crate::protocol_quiz::{QuizQuestionView, QuizResult, QuizScene, QuizStage};

pub type PlayerId = u8;
pub type ServerTime = f64;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum Role {
    Phone,
    Tv,
}

// ---------------------------------------------------------------- client → host

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "t", rename_all = "snake_case")]
#[ts(export)]
pub enum ClientMsg {
    /// First message on every connection. Phones send their name and, when
    /// reconnecting, the token from an earlier `Welcome`.
    Hello {
        role: Role,
        name: Option<String>,
        token: Option<String>,
    },
    /// Clock sync. `t0` is the client's local time; it is echoed back.
    Ping { t0: f64 },
    Action { action: Action },
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "t", rename_all = "snake_case")]
#[ts(export)]
pub enum Action {
    StartGame,
    BackToLobby,
    /// Pick an option of a `PhoneScreen::Choice`. `at` is the client's estimate
    /// of server time at the moment of the tap.
    Choose { index: u8, at: ServerTime },
}

// ---------------------------------------------------------------- host → client

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "t", rename_all = "snake_case")]
#[ts(export)]
pub enum ServerMsg {
    Welcome {
        player: Option<PlayerId>,
        token: Option<String>,
        now: ServerTime,
    },
    Pong { t0: f64, now: ServerTime },
    Phone { view: PhoneView },
    Tv { view: TvView },
    Error { code: ErrorCode, message: String },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum ErrorCode {
    RoomFull,
    NameRequired,
    NotAllowed,
    BadMessage,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct PlayerInfo {
    pub id: PlayerId,
    pub name: String,
    /// Hex colour, e.g. `#2f6fde`.
    pub color: String,
    pub score: i32,
    pub connected: bool,
    pub leader: bool,
}

// ---------------------------------------------------------------- phone views
//
// Phones are thin clients: they render one of these generic screens and know
// nothing about the game being played. New games reuse them.

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct PhoneView {
    pub me: PlayerInfo,
    pub screen: PhoneScreen,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "kind", rename_all = "snake_case")]
#[ts(export)]
pub enum PhoneScreen {
    Lobby {
        players: u8,
        min_players: u8,
        can_start: bool,
    },
    Wait {
        title: String,
        text: Option<String>,
    },
    /// Buttons become active at `opens_at` (server time) on every phone at once.
    Choice {
        prompt: Option<String>,
        options: Vec<ChoiceOption>,
        opens_at: ServerTime,
        closes_at: ServerTime,
        picked: Option<u8>,
    },
    Outcome {
        title: String,
        text: Option<String>,
        tone: Tone,
        points: i32,
    },
    Final {
        rank: u8,
        of: u8,
        score: i32,
        can_restart: bool,
    },
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct ChoiceOption {
    pub label: String,
    pub color: AnswerColor,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum AnswerColor {
    Blue,
    Orange,
    Yellow,
    Pink,
}

impl AnswerColor {
    pub const ALL: [AnswerColor; 4] = [Self::Blue, Self::Orange, Self::Yellow, Self::Pink];
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum Tone {
    Good,
    Bad,
    Neutral,
}

// ---------------------------------------------------------------- TV views
//
// The TV is allowed to be game specific: each game adds a scene variant.

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct TvView {
    pub join_url: String,
    pub players: Vec<PlayerInfo>,
    pub scene: TvScene,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "kind", rename_all = "snake_case")]
#[ts(export)]
pub enum TvScene {
    Lobby { min_players: u8 },
    Quiz { scene: QuizScene },
}
