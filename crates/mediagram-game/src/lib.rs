//! Game night: the host-side game framework (rooms, player sessions, the
//! `Game` trait) and its wire protocol.
//!
//! Transport agnostic and synchronous; a server wraps it in sockets.

pub mod game;
pub mod host;
pub mod lan;
pub mod protocol;
mod protocol_choice;
mod protocol_quiz;
mod protocol_settings;
mod quiz;
mod quiz_config;
mod quiz_views;
pub mod room;
mod room_seats;
mod room_settings;
mod room_views;
mod server;
pub mod starter;

pub use game::{ActionError, Game, GameCtx, GameFactory, ViewCtx};
pub use host::{Host, HostConfig};
pub use quiz::Quiz;
pub use quiz_config::{Question, QuizConfig};
pub use room::{ConnId, Outbox, Room, RoomConfig};
pub use room_seats::Player;
pub use room_settings::Readiness;
