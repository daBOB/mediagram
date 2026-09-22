//! `mediagram metadata`: record what the provider says about each show.
//!
//! A sibling to `posters`, and for the same reason: both read payloads that
//! `add` already fetched and cached when it identified a title, so filling a
//! library that predates either costs no API key and no network.
//!
//! Films and series only. A course has no provider entry, so
//! `titles::distinct_titles` never yields one and nothing here has to know
//! about the distinction.

use anyhow::Result;

use crate::config::Config;
use crate::export::titles::distinct_titles;
use crate::index::{db, shows};
use crate::metadata::title_details;

pub async fn run(cfg: &Config) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    db::require_index(&data_dir, "describe")?;

    // Opened once, for writing, because this command writes: asking the same
    // database for the titles through a second read-only handle would open it
    // twice to save nothing.
    let conn = db::open(&data_dir)?;
    let titles = distinct_titles(&conn)?;
    if titles.is_empty() {
        println!("no films or series in the index; nothing to describe");
        return Ok(());
    }

    // Works with no key at all when the cache is warm, which is the normal
    // case: `add` cached these payloads when it resolved each title.
    let api = cfg.tmdb_client(mediagram_core::http::client()?)?;

    let (mut recorded, mut skipped) = (0usize, 0usize);
    for (kind, id) in &titles {
        match title_details::fetch(&api, *kind, *id, &cfg.tmdb_language).await {
            Ok(row) => {
                shows::upsert(&conn, &row)?;
                recorded += 1;
            }
            // One title the provider will not answer for costs that title its
            // description and nothing else.
            Err(err) => {
                tracing::warn!(id, error = %err, "no description for this title");
                skipped += 1;
            }
        }
    }

    println!(
        "{recorded} title(s) described, {} held in total",
        shows::count(&conn)?
    );
    if skipped > 0 {
        println!("{skipped} could not be read and were left alone");
    }
    Ok(())
}
