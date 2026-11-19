//! Lebensweg, the life path up the Lebensberg for 2–6 players: the board, the
//! rules engine, the live game the room plays, and a simulator that plays it
//! with bots. Not on the shelf yet: no views, and the game is not registered.

mod asks;
pub mod board;
pub mod bots;
pub mod cards;
mod effects;
mod numbers;
mod perks;
pub mod play;
pub mod rules;
pub mod spaces;
mod stretch;
pub mod turn;
pub mod walk;

#[cfg(test)]
mod board_tests;
#[cfg(test)]
mod walk_tests;
#[cfg(test)]
mod turn_tests;
