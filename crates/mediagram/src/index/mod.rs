//! Local SQLite index (`library.db`): the canonical record of every set and
//! part, independent of what has actually reached the channel.

pub mod db;
pub mod parts;
pub mod rescan;
pub mod set_row;
pub mod sets;
pub mod snapshot;
