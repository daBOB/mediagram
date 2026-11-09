//! Grand Allee, the hotel game rebuilt for 2–8 players (design:
//! spieltisch `design/grand-allee-design.md`): the rules engine, the live game
//! the room plays, and a simulator that plays it with bots to tune the numbers.

pub mod board;
pub mod bots;
mod building;
pub(crate) mod live;
mod live_asks;
mod live_choose;
mod live_debts;
mod live_log;
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
mod live_log_tests;
#[cfg(test)]
mod live_permit_tests;
#[cfg(test)]
mod live_tap_tests;
