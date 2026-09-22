//! mlib spec v2: the machine-readable contract shared by the Linux uploader and
//! the Android TV player. Pure data + parsing, no IO, no Telegram dependency.
//!
//! - [`caption`]: the JSON record carried on every uploaded part
//! - [`caption_codec`]: caption text ⇄ [`caption::Caption`] with the 1,024-char budget
//! - [`part_plan`]: byte-range planning for raw splits
//! - [`part_name`]: ≤60-char Telegram file names
//! - [`filename`]: fallback grammar for un-captioned files
//! - [`set_hash`]: set identity derived from per-part hashes
//! - [`schema`]: SQLite DDL for `library.db`
//! - [`slug`]: default derivation of a collection id
//! - [`package`]: the prebuilt metadata package published for a player

pub mod caption;
pub mod caption_codec;
pub mod filename;
pub mod ids;
pub mod index_caption;
pub mod package;
pub mod part_name;
pub mod part_plan;
pub mod schema;
pub mod set_hash;
pub mod slug;

pub use caption::{Caption, Episode, Kind, Part};
pub use caption_codec::{CaptionError, check_budget, parse, to_text};
pub use ids::ProviderIds;
pub use package::{LatestPointer, PackageManifest};
pub use part_plan::{PartRange, PlanError, plan_parts};

/// Spec version written in the caption marker line and `sets.spec_version`.
pub const SPEC_VERSION: u32 = 4;
