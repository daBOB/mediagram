//! One module per subcommand; each exposes `run`.

pub mod accept_login;
pub mod add;
pub mod add_course;
pub mod args;
pub mod edit;
pub mod export_package;
pub mod export_session;
pub mod login;
pub mod login_code;
pub mod prepare;
pub mod push_index;
pub mod rescan;
pub mod resume;
pub mod serve;
pub mod smoke_upload;
pub mod verify;
pub mod whoami;
