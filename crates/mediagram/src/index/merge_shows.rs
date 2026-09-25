//! Filling `shows` from the attached channel snapshot: rows this index never
//! had, and NULL columns in rows both sides describe. What a provider says
//! about a title never conflicts between machines the way a set's own
//! metadata can — there is only ever more of it to learn — so there is
//! nothing here to resolve, only to complete.

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::merge_columns::shared_columns;

/// The table's key; never overwritten, never the target of a NULL fill.
const KEY_COLUMNS: [&str; 3] = ["source", "kind", "id"];

/// Inserts shows the channel has and this index lacks, then fills any NULL
/// column both sides carry with the channel's value. Returns `(rows_added,
/// rows_filled)`.
pub(super) fn merge(conn: &Connection) -> Result<(usize, usize)> {
    let cols = shared_columns(conn, "shows")?;
    let added = insert_missing(conn, &cols)?;
    let filled = fill_nulls(conn, &cols)?;
    Ok((added, filled))
}

/// Columns whose text is written in the row's `lang`.
const LANGUAGE_TEXT: &[&str] = &["overview", "tagline", "genres", "status"];

fn insert_missing(conn: &Connection, cols: &[String]) -> Result<usize> {
    let col_list = cols.join(", ");
    conn.execute(
        &format!(
            "INSERT INTO main.shows ({col_list})
             SELECT {col_list} FROM channel.shows ch
             WHERE NOT EXISTS (
                SELECT 1 FROM main.shows m
                WHERE m.source = ch.source AND m.kind = ch.kind AND m.id = ch.id)"
        ),
        [],
    )
    .context("inserting shows this index lacks")
}

fn fill_nulls(conn: &Connection, cols: &[String]) -> Result<usize> {
    let fillable: Vec<&String> = cols
        .iter()
        .filter(|c| !KEY_COLUMNS.contains(&c.as_str()))
        .collect();
    if fillable.is_empty() {
        return Ok(0);
    }
    // Text is in the language `lang` names, so it is only taken from a row in
    // the same language: an English overview under `lang = 'de-DE'` would be
    // a German library's page in English. Figures and dates read the same in
    // any language and are filled regardless.
    let same_lang = |c: &str| {
        if LANGUAGE_TEXT.contains(&c) {
            " AND m.lang = ch.lang"
        } else {
            ""
        }
    };
    let set_clause = fillable
        .iter()
        .map(|c| {
            if LANGUAGE_TEXT.contains(&c.as_str()) {
                format!(
                    "{c} = CASE WHEN m.lang = ch.lang THEN COALESCE(m.{c}, ch.{c}) ELSE m.{c} END"
                )
            } else {
                format!("{c} = COALESCE(m.{c}, ch.{c})")
            }
        })
        .collect::<Vec<_>>()
        .join(", ");
    // Only rows where a column would actually change count as filled: a
    // second merge must report zero here, or `pull-index --dry-run` twice in
    // a row would look like there was always more to do.
    let changed = fillable
        .iter()
        .map(|c| format!("(m.{c} IS NULL AND ch.{c} IS NOT NULL{})", same_lang(c)))
        .collect::<Vec<_>>()
        .join(" OR ");
    conn.execute(
        &format!(
            "UPDATE main.shows AS m SET {set_clause}
             FROM channel.shows AS ch
             WHERE ch.source = m.source AND ch.kind = m.kind AND ch.id = m.id
               AND ({changed})"
        ),
        [],
    )
    .context("filling missing show fields from the channel")
}
