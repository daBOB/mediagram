//! mediagram library surface: everything the CLI binary and the integration
//! tests share. The binary in `main.rs` only parses arguments and dispatches.

pub mod commands;
pub mod config;
pub mod course;
pub mod edit;
pub mod export;
pub mod index;
pub mod media;
pub mod metadata;
pub mod paths;
pub mod remove;
pub mod serve;
pub mod telegram;
#[cfg(test)]
pub(crate) mod test_env;
pub mod upload;
pub mod verify;
