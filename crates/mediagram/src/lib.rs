//! mediagram library surface: everything the CLI binary and the integration
//! tests share. The binary in `main.rs` only parses arguments and dispatches.

pub mod commands;
pub mod config;
pub mod export;
pub mod index;
pub mod media;
pub mod metadata;
pub mod paths;
pub mod telegram;
pub mod upload;
pub mod verify;
