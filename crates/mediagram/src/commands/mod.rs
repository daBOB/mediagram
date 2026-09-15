//! One module per subcommand; each exposes `run`.

pub mod add;
pub mod args;
pub mod export_package;
pub mod login;
pub mod push_index;
pub mod rescan;
pub mod resume;
pub mod smoke_upload;
pub mod verify;
pub mod whoami;
