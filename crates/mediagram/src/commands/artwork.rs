//! `mediagram artwork <set-id|title> --poster <file> --backdrop <file>`:
//! stores custom art the uploader supplies directly, overriding TMDB's own
//! where a title has one.
//!
//! Never pushes: like any other index write, it takes `push-index` to
//! publish. Resolving the target first tells the caller which key the art
//! landed under, which is worth knowing before that push.

use anyhow::{Context, Result, bail};

use super::args::ArtworkArgs;
use crate::config::Config;
use crate::index::set_row::SetRow;
use crate::index::{artwork, db, sets};

pub fn run(cfg: &Config, args: ArtworkArgs) -> Result<()> {
    if args.poster.is_none() && args.backdrop.is_none() && !args.clear {
        bail!("nothing to do; pass --poster and/or --backdrop, or --clear");
    }

    let conn = db::open(&cfg.data_dir()?)?;
    let key = resolve_key(&conn, &args.target)?;
    let backdrop = mlib_spec::package::backdrop_key(&key);

    if args.clear {
        let cleared = usize::from(artwork::clear(&conn, &key)?)
            + usize::from(artwork::clear(&conn, &backdrop)?);
        println!("cleared {cleared} artwork row(s) for {key}");
    }

    let mut stored = 0;
    if let Some(path) = &args.poster {
        artwork::put_file(&conn, &key, path).with_context(|| format!("poster for {key}"))?;
        stored += 1;
    }
    if let Some(path) = &args.backdrop {
        artwork::put_file(&conn, &backdrop, path).with_context(|| format!("backdrop for {key}"))?;
        stored += 1;
    }
    if stored > 0 {
        println!("stored {stored} image(s) under {key}; run `mediagram push-index` to publish");
    }
    Ok(())
}

/// The art key `target` resolves to: an exact set id first, else a set whose
/// `show` or `title` matches it exactly. Whichever is found, a TMDB id (if
/// any) wins over the title-slug fallback, matching how a reader looks art
/// up.
fn resolve_key(conn: &rusqlite::Connection, target: &str) -> Result<String> {
    let row = sets::get_set(conn, target)?
        .or(sets::find_by_name(conn, target)?)
        .with_context(|| format!("no set or title `{target}` found in the index"))?;
    Ok(art_key_for(&row))
}

fn art_key_for(row: &SetRow) -> String {
    if let Some(id) = row.tmdb {
        return mediagram_tmdb::posters::poster_key(row.kind, id);
    }
    let name = row
        .show
        .as_deref()
        .or(row.title.as_deref())
        .unwrap_or(&row.set_id);
    mlib_spec::package::title_art_key(name).unwrap_or_else(|| row.set_id.clone())
}
