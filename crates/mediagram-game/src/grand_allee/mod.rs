//! Grand Allee, the hotel game rebuilt for 2–8 players (design:
//! spieltisch `design/grand-allee-design.md`): the rules engine, a bot
//! simulator for tuning its numbers, and the live game the room plays.

pub mod board;
pub mod bots;
mod actions;
mod building;
mod debts;
pub(crate) mod live;
mod live_asks;
mod live_choose;
mod live_debts;
mod live_turn;
mod live_text;
mod live_tv;
mod live_views;
mod money;
mod offers;
pub mod play;
pub mod rules;

#[cfg(test)]
mod rules_tests;
#[cfg(test)]
mod live_tests;
#[cfg(test)]
mod live_debt_tests;
#[cfg(test)]
mod live_game_tests;
#[cfg(test)]
mod live_tap_tests;
