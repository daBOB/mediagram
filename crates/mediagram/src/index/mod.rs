//! Local SQLite index (`library.db`): the canonical record of every set and
//! part, independent of what has actually reached the channel.

pub mod assets;
pub mod db;
pub mod label;
pub mod parts;
pub mod pins;
pub mod progress;
pub mod rescan;
pub mod rescan_parts;
pub mod set_lookup;
pub mod set_row;
pub mod sets;
pub mod shows;
pub mod snapshot;
pub mod status;
