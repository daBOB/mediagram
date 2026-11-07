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

pub use crate::avatars::AvatarSlot;
pub use crate::cards::{Card, Rank, Suit};
pub use crate::protocol_cards::{
    ButtonNeeds, CardDesk, CardsBanner, CardsEvent, CardsEventKind, CardsPile, CardsScene, CardsSeat, DeskButton, DeskCard,
};
pub use crate::protocol_bauherr::{
    BauherrBoard, BauherrEntrance, BauherrEstate, BauherrLevel, BauherrMark, BauherrPermit, BauherrPlot, BauherrScene,
    BauherrSeat, BauherrTile, BauherrTileKind, BauherrTurn,
};
pub use crate::protocol_client::{Action, ClientMsg, Role};
pub use crate::protocol_choice::{AnswerColor, ChoiceOption, SheetLine, Tone};
pub use crate::protocol_poster::{PosterScene, PosterStage};
pub use crate::protocol_quiz::{QuizQuestionView, QuizResult, QuizScene, QuizStage};
pub use crate::protocol_race::{RaceCapture, RaceMove, RaceScene, RaceSeat, RaceStage};
pub use crate::protocol_settings::{GameKind, LobbySettings, QuizMode};

pub type PlayerId = u8;
pub type ServerTime = f64;

// ---------------------------------------------------------------- host → client

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "t", rename_all = "snake_case")]
#[ts(export)]
pub enum ServerMsg {
    Welcome { player: Option<PlayerId>, token: Option<String>, now: ServerTime },
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
    /// Catalogue id, see `avatars::AVATARS`.
    pub avatar: Option<String>,
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
        is_leader: bool,
        leader_name: Option<String>,
        settings: LobbySettings,
        /// Why the chosen game cannot start yet.
        blocked: Option<String>,
        /// The whole catalogue; `taken` = owned by another player.
        avatars: Vec<AvatarSlot>,
    },
    Wait {
        title: String,
        text: Option<String>,
    },
    /// Buttons become active at `opens_at` (server time) on every phone at once.
    /// Without `closes_at` the choice waits as long as it takes (no clock).
    Choice {
        prompt: Option<String>,
        options: Vec<ChoiceOption>,
        opens_at: ServerTime,
        closes_at: Option<ServerTime>,
        picked: Option<u8>,
    },
    /// A status sheet for money games: header lines (cash, holdings), a prompt
    /// and options answered with `Action::Choose`. Without options it only
    /// informs. Shown to every player, whoever's turn it is.
    Sheet {
        lines: Vec<SheetLine>,
        prompt: Option<String>,
        options: Vec<ChoiceOption>,
        opens_at: ServerTime,
        picked: Option<u8>,
    },
    Outcome {
        title: String,
        text: Option<String>,
        tone: Tone,
        points: i32,
    },
    /// A card table: own hand, buttons and what is on the table.
    Cards {
        desk: CardDesk,
    },
    Final {
        rank: u8,
        of: u8,
        score: i32,
        can_restart: bool,
    },
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
    Lobby {
        min_players: u8,
        settings: LobbySettings,
        leader_name: Option<String>,
        /// Why the chosen game cannot start yet.
        blocked: Option<String>,
    },
    Quiz { scene: QuizScene },
    Poster { scene: PosterScene },
    Race { scene: RaceScene },
    Cards { scene: CardsScene },
    Bauherr { scene: BauherrScene },
}
