//! The records Kotlin receives. A set's episode arrives as the index's JSON
//! text; [`summary::summary_from`] hands the boundary two plain numbers
//! instead.

mod credits;
mod summary;
pub use credits::{CreditRecord, FranchiseRecord, PeopleHitRecord, PersonRecord, TitleCreditsRecord};
pub use summary::{SetSummary, summary_from};

use mediagram_tmdb::details::TitleDetailsRow;

/// What a provider said about a title, flattened for the binding surface.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TitleInfo {
    pub overview: Option<String>,
    pub tagline: Option<String>,
    pub genres: Option<String>,
    pub rating: Option<f64>,
    pub network: Option<String>,
    pub status: Option<String>,
}

impl From<TitleDetailsRow> for TitleInfo {
    fn from(record: TitleDetailsRow) -> Self {
        TitleInfo {
            overview: record.overview,
            tagline: record.tagline,
            genres: record.genres,
            rating: record.rating,
            network: record.network,
            status: record.status,
        }
    }
}

/// What the installed catalog is, for the System screen's "Catalogue" block:
/// where it came from, how much it holds, and which schema it was written
/// with. `schema` is this build's own `SCHEMA_VERSION`, not a value read out
/// of the database — it says what the reader understands, not what any one
/// file happens to claim.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CatalogFacts {
    /// Where the installed catalogue came from: `"channel"` or `"package"`,
    /// or empty when none is installed or its record cannot be read.
    pub origin: String,
    pub sets: u64,
    pub posters: u64,
    pub schema: u32,
    /// Seconds since the epoch when the installed catalogue was pushed,
    /// read from the installed version's own name. `None` when nothing is
    /// installed, or the name cannot be read.
    pub published_at: Option<i64>,
}

/// What one fetch did, for the screen that reports it.
///
/// Every count is a number of titles — what a shelf shows as one card, so a
/// series or a course is one however many episodes it holds. Six counts
/// rather than a verdict, because most of what happens to a title is not a
/// failure and "0 fetched" needs to say which it was.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, uniffi::Record)]
pub struct FetchReport {
    pub posters_fetched: u32,
    pub posters_already_held: u32,
    /// A title's backdrop, fetched at the width the caller asked for — see
    /// `enrich::fetch::fetch_into`. Zero throughout for a run the caller
    /// asked no width for.
    pub backdrops_fetched: u32,
    pub backdrops_already_held: u32,
    pub details_recorded: u32,
    /// Titles something already describes — the index's own row, or one an
    /// earlier run on this device fetched. Left alone for the same reason a
    /// poster already held is not downloaded again.
    pub details_already_known: u32,
    /// Titles the provider numbers nothing of, so neither half could be
    /// asked. A course is one of these, not a failure.
    pub no_provider_id: u32,
    /// Titles this run could not finish: the provider would not describe
    /// them, or their artwork would not download. One title that lost both
    /// is counted once, because these are titles.
    pub failed: u32,
}

/// One library the signed-in account could choose, as the caller sees it.
///
/// A title to render and a handle to send back, and nothing else. The handle
/// is a random name this data directory minted for the channel — see
/// `api::channel::library` — so a caller holding one learns nothing about where the
/// bytes live, which is the same rule the byte path is held to.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct LibraryChoice {
    pub handle: String,
    pub title: String,
}

/// Who the signed-in account is, for a screen that shows the connection.
/// Never the phone number: nothing here needs it, so nothing carries it.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AccountSummary {
    pub name: String,
    pub username: Option<String>,
}

/// Outcome of a completed sign-in step.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AuthOutcome {
    Done,
    PasswordNeeded,
}

/// One of this app's sessions signed in to the account — or the current
/// one, which shares no `api_id` with the rest to compare. `id` is the
/// authorization's hash as a decimal string: an `i64` does not survive the
/// FFI boundary into a Kotlin `Long` without one, and a hash of `0` (never a
/// real authorization) marks the current row, which cannot be revoked from
/// itself. Never the IP address — see `web/src/settings/sessions.ts`, the
/// same shape read from the other surface.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SessionSummary {
    pub id: String,
    pub device: String,
    pub platform: String,
    pub app: String,
    pub app_version: String,
    /// `country`, or `country, region` — `None` when Telegram reports neither.
    pub location: Option<String>,
    pub last_active: i64,
    pub created: i64,
    pub current: bool,
    pub unconfirmed: bool,
}
