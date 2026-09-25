//! `mediagram posters`: cover art for the films and series in the index,
//! written beside `library.db` where a player running on this machine finds
//! it.
//!
//! The published package carries its own artwork, so this command is for the
//! other arrangement: a player reading the local index directly, which has no
//! package to unpack and therefore nothing else to show. Both look in the
//! same place relative to the index they opened.
//!
//! Backdrops come too, for the web player's cover story. They are fetched
//! here and nowhere else: the package and the phone leave them out.
//!
//! Only films and series have artwork here. A course has no provider id to
//! key a poster by, so `titles::distinct_titles` never yields one and nothing
//! in this file has to know about the distinction.

use std::path::Path;

use anyhow::Result;
use mediagram_tmdb::poster_files::{already_held, download_into};
use mediagram_tmdb::posters::{resolve_backdrops, resolve_posters};

use crate::config::Config;
use crate::export::stage::POSTER_DIR;
use crate::export::titles::distinct_titles;
use crate::index::db;

pub async fn run(cfg: &Config, index: Option<&Path>) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    let conn = match index {
        // A snapshot from the uploading machine, which may be a schema behind
        // this build; the titles this reads have been there since long before.
        Some(path) => db::open_snapshot(path)?,
        None => db::open_read_only(&data_dir, "illustrate")?,
    };
    let titles = distinct_titles(&conn)?;
    if titles.is_empty() {
        println!("no films or series in the index; nothing to fetch");
        return Ok(());
    }

    // Built once and used for both the lookup and the download below —
    // `TmdbClient` takes this same client rather than building its own.
    let http = mediagram_core::http::client()?;
    let api = cfg.tmdb_client(http.clone())?;
    // Both read the same cached details payload, so the second pass costs
    // no request.
    let mut refs = resolve_posters(&api, &titles).await;
    // The widest TMDB serves short of the original: this command fills the
    // web player's cover story, drawn desktop-wide.
    refs.extend(resolve_backdrops(&api, &titles, 1280).await);
    if refs.is_empty() {
        println!(
            "{} title(s), none with artwork recorded at TMDB",
            titles.len()
        );
        return Ok(());
    }

    let dir = data_dir.join(POSTER_DIR);
    let held = already_held(&refs, &dir);
    let written = download_into(&http, &refs, &dir).await?;

    let fetched = written.len().saturating_sub(held);
    let missing = refs.len() - written.len();
    println!(
        "{} image(s) in {}: {fetched} fetched, {held} already held",
        written.len(),
        dir.display()
    );
    if missing > 0 {
        println!("{missing} could not be downloaded and were skipped");
    }
    Ok(())
}
