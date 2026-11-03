//! Game night: the host-side game framework (rooms, player sessions, the
//! `Game` trait) and its wire protocol.
//!
//! Transport agnostic and synchronous; a server wraps it in sockets.

pub mod game;
pub mod host;
pub mod lan;
pub mod protocol;
pub mod room;
pub mod starter;
mod protocol_quiz;
mod quiz;
mod quiz_config;
mod quiz_views;
mod room_seats;
mod room_views;
mod server;

pub use game::{ActionError, Game, GameCtx, GameFactory, ViewCtx};
pub use host::{Host, HostConfig};
pub use room::{ConnId, Outbox, Room, RoomConfig};
pub use room_seats::Player;
pub use quiz::Quiz;
pub use quiz_config::{Question, QuizConfig};
