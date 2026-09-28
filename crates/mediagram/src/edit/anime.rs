//! `mediagram edit <set-id> --anime yes|no|auto`: force a title in or out of
//! the Anime department, or drop back to the automatic genre-and-language
//! rule. Index-only — no caption rewrite, no Telegram — because the decision
//! belongs to the title, not to any one set's message.

use anyhow::{Result, bail};
use mlib_spec::Kind;
use rusqlite::Connection;

use crate::index::anime_overrides;
use crate::index::set_row::SetRow;

/// What `--anime` accepts. `Auto` clears a prior override rather than
/// deleting its row: see [`crate::index::anime_overrides`] on why a clear
/// has to be a value, not an absence.
#[derive(clap::ValueEnum, Debug, Clone, Copy, PartialEq, Eq)]
pub enum AnimeChoice {
    Yes,
    No,
    Auto,
}

impl AnimeChoice {
    fn as_override(self) -> Option<bool> {
        match self {
            AnimeChoice::Yes => Some(true),
            AnimeChoice::No => Some(false),
            AnimeChoice::Auto => None,
        }
    }
}

/// How an override reads out, whether it came from a row or from asking
/// `--anime` what it is about to write.
fn label(value: Option<bool>) -> &'static str {
    match value {
        Some(true) => "yes",
        Some(false) => "no",
        None => "automatic",
    }
}

/// The TMDB title an override on `row` would apply to, or why `row` cannot
/// have one. An override is per title, not per set: it needs a TMDB id, and
/// only a film or a series can be anime.
fn target(row: &SetRow) -> Result<(Kind, u64)> {
    let Some(tmdb) = row.tmdb else {
        bail!(
            "set {} has no TMDB id; an anime override belongs to a TMDB title — give it one with `--tmdb` first",
            row.set_id
        );
    };
    match row.kind {
        Kind::Movie | Kind::Ep => Ok((row.kind, tmdb)),
        Kind::Tut | Kind::Doc | Kind::Docu => {
            bail!("set {} is a {}; only films and series can be anime", row.set_id, row.kind)
        }
    }
}

pub fn run(conn: &Connection, row: &SetRow, choice: AnimeChoice, dry_run: bool) -> Result<()> {
    let (kind, tmdb) = target(row)?;
    let current = anime_overrides::get(conn, kind, tmdb)?;
    let requested = choice.as_override();
    if current == requested {
        bail!("already says {}; nothing to do", label(current));
    }

    let name = row.show.as_deref().or(row.title.as_deref()).unwrap_or(&row.set_id);
    println!(
        "set {} — {name} (tmdb-{}-{tmdb}: every set of this title)",
        row.set_id,
        mediagram_tmdb::posters::kind_key(kind),
    );
    println!("  anime: {} -> {}", label(current), label(requested));

    if dry_run {
        println!("\ndry run; nothing was written");
        return Ok(());
    }

    anime_overrides::set(conn, kind, tmdb, requested, crate::clock::now_unix())?;
    println!("wrote the override; run `mediagram push-index` to publish");
    Ok(())
}

#[cfg(test)]
#[path = "anime_tests.rs"]
mod tests;
