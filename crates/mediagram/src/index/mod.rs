//! Local SQLite index (`library.db`): the canonical record of every set and
//! part, independent of what has actually reached the channel.

pub mod artwork;
pub mod assets;
mod columns;
pub mod db;
pub mod label;
pub mod merge;
mod merge_artwork;
mod merge_candidates;
mod merge_columns;
pub mod merge_conflicts;
mod merge_copy;
mod merge_credits;
mod merge_diff;
mod merge_shows;
mod migrations;
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
pub mod sqlite_init;
pub mod status;
