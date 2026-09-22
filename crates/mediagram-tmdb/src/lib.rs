//! Everything that talks to TMDB, in one place.
//!
//! The uploader resolves titles and publishes packages with this; the mobile
//! core fetches artwork with it. A second copy would be a second set of
//! answers to how a credential is presented, how a rate limit is obeyed, and
//! what a poster path is allowed to look like — and the two would drift
//! apart the first time only one of them was fixed.

pub mod details;
pub mod disk_cache;
pub mod localized;
pub mod posters;
pub mod tmdb_client;
pub mod tmdb_types;

pub use details::details;
