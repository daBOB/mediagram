//! `mediagram posters`: cover art for the films and series in the index,
//! written beside `library.db` where a player running on this machine finds
//! it.
//!
//! The published package carries its own artwork, so this command is for the
//! other arrangement: a player reading the local index directly, which has no
//! package to unpack and therefore nothing else to show. Both look in the
//! same place relative to the index they opened.
//!
//! Only films and series have artwork here. A course has no provider id to
//! key a poster by, so `titles::distinct_titles` never yields one and nothing
//! in this file has to know about the distinction.

use anyhow::{Result, bail};

use crate::config::Config;
use mediagram_tmdb::posters::{already_held, download_into, resolve_posters};
use crate::export::stage::POSTER_DIR;
use crate::export::titles::distinct_titles_in;
use mediagram_tmdb::tmdb_client::TmdbClient;

pub async fn run(cfg: &Config) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    let live = data_dir.join("library.db");
    if !live.exists() {
        bail!(
            "no library.db in {}; nothing to illustrate",
            data_dir.display()
        );
    }

    let titles = distinct_titles_in(&live)?;
    if titles.is_empty() {
        println!("no films or series in the index; nothing to fetch");
        return Ok(());
    }

    // Built once and used for both the lookup and the download below —
    // `TmdbClient` takes this same client rather than building its own.
    let http = reqwest::Client::new();
    // Works with no key at all when the cache is warm, which is the normal
    // case: `add` cached these payloads when it resolved each title.
    let api = TmdbClient::with_cache(
        http.clone(),
        cfg.tmdb_key.as_deref().unwrap_or(""),
        &data_dir,
        &cfg.tmdb_language,
    );
    let refs = resolve_posters(&api, &titles).await;
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
        "{} poster(s) in {}: {fetched} fetched, {held} already held",
        written.len(),
        dir.display()
    );
    if missing > 0 {
        println!("{missing} could not be downloaded and were skipped");
    }
    Ok(())
}
