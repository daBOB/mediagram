//! Finding a title among five hundred, ported from the web player's
//! `web/src/search/`.
//!
//! The web runs search server-side, over the index, because it needs a
//! title's summary text and the browser's catalog never carries one. The
//! phone has no server but holds the same index, so search runs here rather
//! than as a Kotlin filter over the in-memory set list, which would have no
//! summary to search either.
//!
//! `normalize` and `excerpt` are line-for-line ports; `rank` orders hits with
//! `icu_collator`, ICU4X's pure-Rust German collator, so the tie-break
//! agrees with the web's `localeCompare("de")` rather than approximating
//! it. `shared_search_fixtures.rs` pins the two against real title pairs a
//! folded-string comparison got wrong before this.

pub mod excerpt;
pub mod normalize;
pub mod rank;
