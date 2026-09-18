//! One module per subcommand; each exposes `run`.

pub mod accept_login;
pub mod add;
pub mod add_course;
pub mod add_show;
pub mod args;
pub mod edit;
pub mod export_package;
pub mod export_session;
pub mod login;
pub mod login_code;
pub mod metadata;
pub mod posters;
pub mod prepare;
pub mod push_index;
pub mod remove;
pub mod rescan;
pub mod resume;
pub mod serve;
pub mod smoke_upload;
pub mod status;
pub mod verify;
pub mod whoami;
