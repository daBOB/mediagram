//! The stats page's one read. Split out to keep `state.rs` under the line
//! limit; every rule lives in `crate::state::stats`, pinned to the web by
//! the shared fixtures.

use std::sync::Arc;

use crate::state::rows;
use crate::state::stats::{exchange, summary};

use super::super::Core;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// `profile_id`'s watch time and history, summed over every device, as
    /// of `today` (`YYYY-MM-DD` where the viewer is). Nothing watched reads
    /// as 30 empty days and no history; a storage failure as an empty
    /// summary — never an error.
    pub async fn stats(
        self: Arc<Self>,
        profile_id: String,
        today: String,
    ) -> summary::StatsSummary {
        self.blocking(move |core| {
            core.state_db
                .with(|conn| {
                    let (titles, days) = exchange::export(conn, &profile_id)?;
                    let watched = rows::watched_for(conn, &profile_id)?;
                    let input = summary::SummaryInput {
                        today,
                        titles,
                        days,
                        watched,
                    };
                    Ok(summary::summarize(&input))
                })
                .unwrap_or_default()
        })
        .await
    }
}

#[cfg(test)]
#[path = "stats_tests.rs"]
mod tests;
