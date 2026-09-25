//! mediagram-cache: a dumb LAN chunk store. It has no Telegram session, no
//! index and no UI; a set id is an opaque string to it. Android devices on
//! the home network read and write it so a chunk fetched from Telegram once
//! is not fetched again by the next device.

pub mod config;
pub mod http;
pub mod mdns;
pub mod rules;
pub mod store;
pub mod token;
