//! One module per subcommand, each exposing `run`, plus `args` (their clap
//! structs) and `background` (handing an upload to a detached process).
//! Logic another command or module needs lives in the domain modules
//! (`index`, `upload`, `media`, ...), not here.

pub mod accept_login;
pub mod add;
pub mod add_course;
pub mod add_show;
pub mod args;
pub mod background;
pub mod edit;
pub mod export_package;
pub mod finish_set;
pub mod login;
pub mod login_code;
pub mod metadata;
pub mod posters;
pub mod prepare;
pub mod pull_index;
pub mod push_index;
pub mod remove;
pub mod rescan;
pub mod resume;
pub mod serve;
pub mod setup;
pub mod smoke_upload;
pub mod status;
pub mod verify;
pub mod whoami;
