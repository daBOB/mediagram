//! The wire types that travel from a client to the host: the connection
//! handshake and the actions players take.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{GameKind, QuizMode, ServerTime};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum Role {
    Phone,
    Tv,
}

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
        /// Avatar the phone had last time; used if it is free.
        #[serde(default)]
        #[ts(optional)]
        avatar: Option<String>,
    },
    /// Clock sync. `t0` is the client's local time; it is echoed back.
    Ping {
        t0: f64,
    },
    Action {
        action: Action,
    },
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "t", rename_all = "snake_case")]
#[ts(export)]
pub enum Action {
    /// Leader only, lobby only.
    Configure {
        game: GameKind,
        quiz_mode: QuizMode,
        kids_only: bool,
        /// Older clients leave it out: off.
        #[serde(default)]
        adult: bool,
    },
    StartGame,
    BackToLobby,
    /// Take a free avatar. Lobby only; refused if unknown or taken.
    PickAvatar {
        id: String,
    },
    /// Pick an option of a `PhoneScreen::Choice`. `at` is the client's estimate
    /// of server time at the moment of the tap.
    Choose {
        index: u8,
        at: ServerTime,
    },
    /// A sealed bid set on a `PhoneScreen::Sheet`: a whole number of its
    /// steps up to its `max`, or 0 to pass.
    Bid {
        amount: i32,
        at: ServerTime,
    },
    /// A button of a `PhoneScreen::Cards` desk, with the cards picked for it
    /// (indices into the desk's hand and table).
    Cards {
        button: String,
        hand: Vec<u8>,
        table: Option<u8>,
    },
}
