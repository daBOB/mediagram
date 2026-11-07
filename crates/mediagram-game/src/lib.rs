//! Game night: the host-side game framework (rooms, player sessions, the
//! `Game` trait) and its wire protocol.
//!
//! Transport agnostic and synchronous; a server wraps it in sockets.

pub mod avatars;
mod cards;
mod factory;
mod film_questions;
pub mod game;
pub mod general;
pub mod host;
pub mod lan;
pub mod protocol;
mod protocol_cards;
mod protocol_choice;
mod protocol_poster;
mod protocol_quiz;
mod protocol_race;
mod protocol_settings;
mod durak;
mod durak_flow;
mod durak_rules;
mod durak_views;
mod maumau;
mod maumau_rules;
mod maumau_views;
mod poster;
mod poster_config;
mod poster_lookup;
mod poster_views;
mod quiz;
mod quiz_config;
mod quiz_mix;
mod quiz_views;
mod race;
mod race_rules;
mod race_views;
pub mod room;
mod room_loop;
mod room_seats;
mod room_settings;
mod room_views;
mod server;
mod titles;

pub use game::{ActionError, Game, GameCtx, GameFactory, ViewCtx};
pub use host::{Host, HostConfig, start_game_night};
pub use poster::PosterGame;
pub use poster_config::{PosterConfig, poster_ready};
pub use poster_lookup::PosterLookup;
pub use quiz::Quiz;
pub use quiz_config::{Question, QuizConfig};
pub use room::{ConnId, Outbox, Room, RoomConfig};
pub use room_seats::Player;
pub use room_settings::Readiness;
pub use titles::{GameTitle, ImageKind, TitleKind, TitlePool, image_url};
