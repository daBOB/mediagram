//! Which version of a film a file is: its cut, guessed from the file name and
//! confirmed, and the versions of the same film the library already holds.
//!
//! Settled before the remux, which can spend minutes on a file that is then
//! refused as a copy of one already uploaded.

use std::io::IsTerminal;

use anyhow::{Context, Result, bail};
use dialoguer::{Confirm, Input};
use mlib_spec::Kind;
use rusqlite::Connection;

use crate::index::set_lookup;
use crate::index::set_row::SetRow;
use crate::media::file_names::cut_from_file_name;
use crate::metadata::resolve::ResolvedItem;
use crate::upload::new_set::NewSet;

/// The `variant` to record. For anything but a film, whatever was asked for.
/// For a film, `--variant` as given, else the cut its file name says it is;
/// and when the library already holds the film, which versions it has, with
/// a question before uploading one it already holds.
pub fn settle(
    conn: &Connection,
    new: &NewSet,
    resolved: &ResolvedItem,
    file_name: &str,
    quality: Option<&str>,
    hdr: &str,
) -> Result<Option<String>> {
    if resolved.kind != Kind::Movie {
        return Ok(new.variant.clone());
    }
    let cut = match &new.variant {
        Some(variant) => Some(variant.clone()),
        None => confirm_cut(cut_from_file_name(file_name))?,
    };
    if let Some(tmdb) = resolved.ids.tmdb {
        let title = resolved.title.as_deref().unwrap_or("this film");
        let name = match resolved.year {
            Some(year) => format!("{title} ({year})"),
            None => title.to_string(),
        };
        let held = set_lookup::film_versions(conn, tmdb)?;
        refuse_a_held_version(&name, &held, quality, hdr, cut.as_deref())?;
    }
    Ok(cut)
}

/// Shows the guessed cut and, at a terminal, lets it be corrected: a title
/// with no year can carry a word that reads as a tag. Emptied, it is the
/// standard cut.
fn confirm_cut(guess: Option<&str>) -> Result<Option<String>> {
    let Some(guess) = guess else {
        return Ok(None);
    };
    println!("Cut: {guess} (from the filename)");
    if !someone_can_answer() {
        return Ok(Some(guess.to_string()));
    }
    let edited: String = Input::new()
        .with_prompt("Cut (Enter keeps it, empty for the standard cut)")
        .with_initial_text(guess)
        .allow_empty(true)
        .interact_text()
        .context("interactive input failed")?;
    let edited = edited.trim();
    Ok((!edited.is_empty()).then(|| edited.to_string()))
}

/// Says which versions are held, and asks before uploading one of them
/// again: the same quality, HDR and cut is almost certainly the same film
/// twice, at the cost of gigabytes of upload. The answer defaults to no, and
/// where nobody can answer it is no.
fn refuse_a_held_version(
    name: &str,
    held: &[SetRow],
    quality: Option<&str>,
    hdr: &str,
    cut: Option<&str>,
) -> Result<()> {
    if held.is_empty() {
        return Ok(());
    }
    println!("{}", notice(name, held));
    let Some(same) = held.iter().find(|row| same_version(row, quality, hdr, cut)) else {
        return Ok(());
    };
    let again = someone_can_answer()
        && Confirm::new()
            .with_prompt("This version is already held. Upload it again?")
            .default(false)
            .interact()
            .context("interactive confirmation failed")?;
    if !again {
        bail!(
            "not uploaded: \"{name}\" is already held as {} (set {}). Name a different cut \
             with --variant, or run this at a terminal to upload it anyway",
            describe(same),
            same.set_id
        );
    }
    Ok(())
}

/// Whether a prompt can be answered: dialoguer reads stdin and draws on
/// stderr, and a script that pipes the output but keeps a terminal's stdin
/// would otherwise fail on the prompt instead of taking its default.
fn someone_can_answer() -> bool {
    std::io::stdin().is_terminal() && std::io::stderr().is_terminal()
}

/// `Adds a version to "Blade Runner (1982)" — already held: 1080p · standard`.
pub(crate) fn notice(name: &str, held: &[SetRow]) -> String {
    let versions: Vec<String> = held.iter().map(describe).collect();
    format!(
        "Adds a version to \"{name}\" — already held: {}",
        versions.join(", ")
    )
}

/// `2160p HDR10 · Director's Cut`: quality with any HDR, then the cut.
pub(crate) fn describe(row: &SetRow) -> String {
    let quality = match (row.quality.as_deref(), row.hdr.as_deref()) {
        (Some(q), Some(hdr)) if hdr != "SDR" => format!("{q} {hdr}"),
        (Some(q), _) => q.to_string(),
        (None, _) => "unknown quality".to_string(),
    };
    let cut = row.variant.as_deref().unwrap_or("standard");
    format!("{quality} · {cut}")
}

pub(crate) fn same_version(
    row: &SetRow,
    quality: Option<&str>,
    hdr: &str,
    cut: Option<&str>,
) -> bool {
    row.quality.as_deref() == quality
        && row.hdr.as_deref() == Some(hdr)
        && row.variant.as_deref() == cut
}

#[cfg(test)]
#[path = "film_version_tests.rs"]
mod tests;
