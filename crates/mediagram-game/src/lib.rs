//! Game night: the host-side game framework (rooms, player sessions, the
//! `Game` trait) and its wire protocol.
//!
//! Transport agnostic and synchronous; a server wraps it in sockets.

mod factory;
mod film_questions;
pub mod game;
pub mod general;
pub mod host;
pub mod lan;
pub mod protocol;
mod protocol_choice;
mod protocol_quiz;
mod protocol_settings;
mod quiz;
mod quiz_config;
mod quiz_mix;
mod quiz_views;
pub mod room;
mod room_seats;
mod room_settings;
mod room_views;
mod server;
mod titles;

pub use game::{ActionError, Game, GameCtx, GameFactory, ViewCtx};
pub use host::{Host, HostConfig, start_game_night};
pub use quiz::Quiz;
pub use quiz_config::{Question, QuizConfig};
pub use room::{ConnId, Outbox, Room, RoomConfig};
pub use room_seats::Player;
pub use room_settings::Readiness;
pub use titles::{GameTitle, ImageKind, TitleKind, TitlePool, image_url};
