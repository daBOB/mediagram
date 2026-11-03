//! Game night: the host-side game framework (rooms, player sessions, the
//! `Game` trait) and its wire protocol.
//!
//! Transport agnostic and synchronous; a server wraps it in sockets.

pub mod game;
pub mod protocol;
pub mod room;
mod room_seats;
mod room_views;

pub use game::{ActionError, Game, GameCtx, GameFactory, ViewCtx};
pub use room::{ConnId, Outbox, Room, RoomConfig};
pub use room_seats::Player;
