//! `mediagram edit <set-id> --category "<name>"` / `--clear-category`, and
//! the `--category` `add-course`/`add-docu` write once, before their first
//! upload: a hand-set label on a course, a documentary collection or a
//! standalone documentary — the same unit its custom artwork already keys
//! (`mlib_spec::category_key`) — that files it into a row on its department
//! page. Index-only, like `--anime`: no caption rewrite, no Telegram.

use anyhow::{Result, bail};
use mlib_spec::Kind;
use rusqlite::Connection;

use crate::index::categories;
use crate::index::set_row::SetRow;

/// Where a category is written, and what to write there. `category = None`
/// clears it (kept as a row, like [`crate::index::categories::set`]
/// documents); `Some` is already trimmed, collapsed and refused-checked,
/// but not yet adopted into an existing spelling — that needs
/// [`categories::in_use`], a database [`write()`] has and [`planned`] does not.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Planned {
    pub department: &'static str,
    pub item_key: String,
    pub category: Option<String>,
}

/// Trims and collapses whitespace, refuses an empty or "Other" name (any
/// case), and adopts the spelling already used by another unit in the same
/// department when `raw` matches one case-insensitively — so "trading" next
/// to an existing "Trading" becomes "Trading" rather than a second row.
/// Returns the name to use and, when it differs from what `raw` trimmed to,
/// the spelling that was actually asked for — for a caller to note.
pub fn normalise(raw: &str, in_use: &[String]) -> Result<(String, Option<String>)> {
    let trimmed = raw.split_whitespace().collect::<Vec<_>>().join(" ");
    if trimmed.is_empty() {
        bail!("a category needs a name; use --clear-category to remove one");
    }
    if trimmed.eq_ignore_ascii_case("other") {
        bail!("uncategorised items already sit under Other");
    }
    for existing in in_use {
        if existing != &trimmed && existing.eq_ignore_ascii_case(&trimmed) {
            return Ok((existing.clone(), Some(trimmed)));
        }
    }
    Ok((trimmed, None))
}

/// The key a category on `name` (a course or collection title, or a
/// standalone documentary's own title) is filed under, and `raw` refused
/// empty or "Other" — everything decidable with no database open. Called by
/// `add-course`/`add-docu` before any upload starts, so an invalid
/// `--category` fails the same way whether or not `--dry-run` was given.
pub fn planned(kind: Kind, name: Option<&str>, raw: &str) -> Result<Planned> {
    let (department, item_key) = mlib_spec::category_key::category_key(kind.as_str(), name, None).ok_or_else(|| {
        anyhow::anyhow!(
            "{:?} has no letters or digits a category can be filed under",
            name.unwrap_or("")
        )
    })?;
    let (category, _) = normalise(raw, &[])?;
    Ok(Planned {
        department,
        item_key,
        category: Some(category),
    })
}

/// Persists `planned`, adopting an existing spelling used elsewhere in
/// `planned.department` if its category matches one — the check `planned`
/// itself cannot make without this connection. `planned.category`'s own
/// refusals (empty, "Other") were already checked by whoever built it, so
/// normalising it again here only ever adopts a spelling or leaves it as is.
pub fn write(conn: &Connection, planned: &Planned) -> Result<()> {
    let category = match &planned.category {
        None => None,
        Some(raw) => {
            let in_use = categories::in_use(conn, planned.department, &planned.item_key)?;
            let (name, _) = normalise(raw, &in_use)?;
            Some(name)
        }
    };
    categories::set(
        conn,
        planned.department,
        &planned.item_key,
        category.as_deref(),
        crate::clock::now_unix(),
    )?;
    Ok(())
}

/// The key a category on `row` is filed under, or why `row` cannot have
/// one: only a course, a course document or a documentary is a unit a
/// category files.
fn key_for(row: &SetRow) -> Result<(&'static str, String)> {
    if !matches!(row.kind, Kind::Tut | Kind::Doc | Kind::Docu) {
        bail!(
            "set {} is a {}; categories are for courses and documentaries",
            row.set_id,
            row.kind
        );
    }
    let name = row.show.as_deref().or(row.title.as_deref());
    mlib_spec::category_key::category_key(row.kind.as_str(), name, None)
        .ok_or_else(|| anyhow::anyhow!("set {} has no name a category can be filed under", row.set_id))
}

/// "(the course: every lesson and document in it)", "(the collection: every
/// documentary in it)" or "(this documentary)" — what a category on `row`
/// actually covers, spelled out so nobody mistakes it for a per-set label.
fn unit_label(kind: Kind, has_show: bool) -> &'static str {
    match kind {
        Kind::Tut | Kind::Doc => "the course: every lesson and document in it",
        Kind::Docu if has_show => "the collection: every documentary in it",
        Kind::Docu => "this documentary",
        Kind::Movie | Kind::Ep => unreachable!("key_for already refused this kind"),
    }
}

pub fn run(conn: &Connection, row: &SetRow, requested: Option<&str>, dry_run: bool) -> Result<()> {
    let (department, item_key) = key_for(row)?;
    let current = categories::get(conn, department, &item_key)?;

    let (new_category, adopted_from) = match requested {
        None => {
            if current.is_none() {
                bail!("set {} has no category; nothing to do", row.set_id);
            }
            (None, None)
        }
        Some(raw) => {
            let in_use = categories::in_use(conn, department, &item_key)?;
            let (name, adopted_from) = normalise(raw, &in_use)?;
            if current.as_deref() == Some(name.as_str()) {
                bail!("set {} already says {name}; nothing to do", row.set_id);
            }
            (Some(name), adopted_from)
        }
    };

    let name = row.show.as_deref().or(row.title.as_deref()).unwrap_or(&row.set_id);
    println!(
        "set {} — {name} ({})",
        row.set_id,
        unit_label(row.kind, row.show.is_some())
    );
    println!(
        "  category: {} -> {}",
        current.as_deref().unwrap_or("-"),
        new_category.as_deref().unwrap_or("-"),
    );
    if let Some(from) = &adopted_from {
        println!(
            "  using the existing spelling \"{}\" instead of \"{from}\"",
            new_category.as_deref().unwrap_or("")
        );
    }

    if dry_run {
        println!("\ndry run; nothing was written");
        return Ok(());
    }

    write(
        conn,
        &Planned {
            department,
            item_key,
            category: new_category,
        },
    )?;
    println!("wrote the category; run `mediagram push-index` to publish");
    Ok(())
}

#[cfg(test)]
#[path = "category_tests.rs"]
mod tests;
