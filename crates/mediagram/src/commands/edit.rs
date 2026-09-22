//! `mediagram edit`: correct a set's metadata after it is in the channel.
//!
//! Metadata is a guess that can be wrong — a title in the wrong language, a
//! show matched to the wrong id — and re-uploading gigabytes to fix a word is
//! absurd. Every part carries the whole record, so a correction rewrites one
//! caption per part.
//!
//! Both sides are corrected together. `rescan` rebuilds the index from
//! captions, so an index fixed on its own would be quietly undone the next
//! time anyone ran it.

use anyhow::{Context, Result, bail};

use crate::commands::args::EditArgs;
use crate::config::Config;
use crate::edit::apply::write_captions;
use crate::edit::plan::{Clearable, Edits, apply_checked, captions, editable_kind};
use crate::index::{db, parts, sets};
use crate::telegram::client::Tg;
use mediagram_tmdb::tmdb_client::{TmdbApi, TmdbClient};
use mlib_spec::Kind;

pub async fn run(cfg: &Config, args: EditArgs) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    let conn = db::open(&data_dir)?;

    let row = sets::get_set(&conn, &args.set_id)?
        .with_context(|| format!("no set {} in the index", args.set_id))?;

    let clear = args
        .clear
        .iter()
        .map(|name| {
            Clearable::parse(name.trim())
                .with_context(|| format!("cannot clear {name:?}; see --help for the field names"))
        })
        .collect::<Result<Vec<_>>>()?;

    let mut edits = Edits {
        kind: args.kind.as_deref().map(editable_kind).transpose()?,
        tmdb: args.tmdb,
        clear,
        title: args.title.clone(),
        show: args.show.clone(),
        year: args.year,
        season: args.season,
        episode: args.episode,
        chap: args.chap.clone(),
        path: args.path.clone(),
    };

    // `--refresh` asks the provider again, which is the whole point after
    // changing `tmdb_language`: the ids are right, the words were not.
    if args.refresh {
        // Against the row as it will be, not as it was: a set being moved to
        // another shelf must be looked up on that shelf's endpoint.
        let provisional = apply_checked(&row, &edits)?;
        let fetched = refresh_from_tmdb(cfg, &data_dir, &provisional).await?;
        edits.title = edits.title.or(fetched.title);
        edits.show = edits.show.or(fetched.show);
        edits.year = edits.year.or(fetched.year);
    }

    if edits.is_empty() {
        bail!("nothing to change; pass --title/--show/--year/… or --refresh");
    }

    let edited = apply_checked(&row, &edits)?;
    if edited == row {
        println!("set {} already says that; nothing to do", args.set_id);
        return Ok(());
    }

    let part_rows = parts::all_parts(&conn, &args.set_id)?;
    // Rendered before anything is sent: an edit that overflows the caption
    // budget must fail with nothing written, not halfway through a set.
    let writes = captions(&edited, &part_rows)?;

    println!("set {}", args.set_id);
    describe_change("kind", Some(row.kind.as_str()), Some(edited.kind.as_str()));
    describe_change("title", row.title.as_deref(), edited.title.as_deref());
    describe_change("show", row.show.as_deref(), edited.show.as_deref());
    describe_change("chap", row.chap.as_deref(), edited.chap.as_deref());
    describe_change("path", row.path.as_deref(), edited.path.as_deref());
    describe_change(
        "year",
        row.year.map(|y| y.to_string()).as_deref(),
        edited.year.map(|y| y.to_string()).as_deref(),
    );
    describe_change(
        "season",
        row.season.map(|s| s.to_string()).as_deref(),
        edited.season.map(|s| s.to_string()).as_deref(),
    );
    describe_change("episode", row.episode.as_deref(), edited.episode.as_deref());
    describe_change(
        "tmdb",
        row.tmdb.map(|v| v.to_string()).as_deref(),
        edited.tmdb.map(|v| v.to_string()).as_deref(),
    );
    println!("  {} caption(s) to rewrite", writes.len());

    if args.dry_run {
        println!("\ndry run; nothing was written");
        return Ok(());
    }

    let tg = Tg::connect(cfg).await?;
    let written = write_captions(&tg.client, tg.channel, &writes, cfg.max_attempts).await;
    tg.shutdown().await;
    let written = written?;

    // The channel first, the index second. If the run dies between them, a
    // `rescan` reconciles from the channel, which now holds the truth.
    sets::update_metadata(&conn, &edited)?;
    println!("rewrote {written} caption(s) and updated the index");
    Ok(())
}

fn describe_change(field: &str, before: Option<&str>, after: Option<&str>) {
    if before != after {
        println!(
            "  {field}: {} -> {}",
            before.unwrap_or("-"),
            after.unwrap_or("-")
        );
    }
}

#[derive(Default)]
struct Fetched {
    title: Option<String>,
    show: Option<String>,
    /// A release year the row may be missing, taken from the same answer.
    year: Option<u16>,
}

/// Asks TMDB again, in the configured language, for what this set already
/// knows it is. Only the words are taken; the ids and the numbers stay.
async fn refresh_from_tmdb(
    cfg: &Config,
    data_dir: &std::path::Path,
    row: &sets::SetRow,
) -> Result<Fetched> {
    let Some(key) = cfg.tmdb_key.as_deref() else {
        bail!("--refresh needs tmdb_key in the config");
    };
    let Some(tmdb) = row.tmdb else {
        bail!("set {} has no tmdb id to refresh from", row.set_id);
    };
    let api = TmdbClient::with_cache(reqwest::Client::new(), key, data_dir, &cfg.tmdb_language);

    if row.kind == Kind::Movie {
        let movie = api.get_json(&format!("/movie/{tmdb}"), &[]).await?;
        return Ok(Fetched {
            title: movie["title"].as_str().map(str::to_string),
            show: None,
            year: year_of(movie["release_date"].as_str()),
        });
    }

    let show = api.get_json(&format!("/tv/{tmdb}"), &[]).await?;
    let show_name = show["name"].as_str().map(str::to_string);
    let first_aired = year_of(show["first_air_date"].as_str());

    // An episode title needs both numbers; without them the show name is
    // still worth correcting on its own.
    let (Some(season), Some(episode)) = (row.season, row.episode.as_deref()) else {
        return Ok(Fetched {
            title: None,
            show: show_name,
            year: first_aired,
        });
    };
    let number: u32 = episode
        .split(['-', 'x'])
        .next()
        .unwrap_or(episode)
        .parse()
        .with_context(|| format!("episode {episode} is not a number"))?;

    let detail = api
        .get_json(&format!("/tv/{tmdb}/season/{season}/episode/{number}"), &[])
        .await?;
    Ok(Fetched {
        title: detail["name"].as_str().map(str::to_string),
        show: show_name,
        year: first_aired,
    })
}

/// The year from a TMDB date like `2004-12-08`.
fn year_of(date: Option<&str>) -> Option<u16> {
    date?.get(..4)?.parse().ok()
}
