//! Destroying a set: its messages in the channel and its rows in the index.
//!
//! The only command here that loses data irrecoverably. The bytes exist
//! nowhere but the channel and Telegram has no undelete, so deciding what to
//! destroy is separated from destroying it: [`plan`] is pure and states the
//! whole cost, [`apply`] carries it out only after that has been shown.

pub mod apply;
pub mod plan;
