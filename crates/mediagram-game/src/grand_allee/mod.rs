//! Grand Allee, the hotel game rebuilt for 2–8 players (design:
//! spieltisch `design/grand-allee-design.md`). This is the rules engine and a
//! bot simulator for tuning its numbers; the live room still plays Bauherr
//! until the TV and phone are rebuilt on it.

pub mod board;
pub mod bots;
mod actions;
mod building;
mod debts;
pub mod play;
pub mod rules;

#[cfg(test)]
mod rules_tests;
