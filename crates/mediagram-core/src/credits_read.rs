//! Reading who is credited: a title's cast and crew, one person's own
//! titles, and the fuzzy name search a Cast row or a search box needs — all
//! from the `credits` table [`super`] writes. Split out to keep that file
//! under the line limit.
//!
//! Ported from the web player's `web/src/catalog/credits.ts`
//! (`creditsFor`/`personFor`/`peopleSearch`): the same table, the same
//! ordering, the same umlaut-tolerant match, so a name typed either way
//! finds the same person on both surfaces.
//!
//! Never merged with a device's own fetched sidecar: nothing on this device
//! ever writes to that copy's `credits` table, so a lookup there would only
//! ever answer empty — the same reason [`crate::franchises::get`] reads
//! only the index.

use mlib_spec::Kind;
use rusqlite::{Connection, params};

use crate::search::normalize::{terms, variants};

use super::SOURCE;

/// One person credited on a title: their id, name, and the character they
/// played (cast) or their job (crew — a fixed label like `Director`).
#[derive(Debug, Clone, PartialEq)]
pub struct Credited {
    pub person_id: u64,
    pub name: String,
    pub role: Option<String>,
}

/// A title's credited people, cast apart from crew — the same split the web
/// player's `creditsFor` makes.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct TitleCredits {
    pub cast: Vec<Credited>,
    pub crew: Vec<Credited>,
}

/// A title's cast, in billing order, and its crew — empty for an index with
/// no `credits` table (v8 and older).
pub fn for_title(conn: &Connection, kind: Kind, id: u64) -> rusqlite::Result<TitleCredits> {
    if !has_table(conn)? {
        return Ok(TitleCredits::default());
    }
    let kind = mediagram_tmdb::posters::kind_key(kind);
    let mut stmt = conn.prepare(
        "SELECT person_id, name, role, dept FROM credits
          WHERE source = ?1 AND kind = ?2 AND id = ?3 ORDER BY ord",
    )?;
    let rows = stmt.query_map(params![SOURCE, kind, id], |row| {
        let dept: String = row.get(3)?;
        let role: Option<String> = row.get(2)?;
        Ok((
            dept,
            Credited {
                person_id: row.get(0)?,
                name: row.get(1)?,
                role: role.filter(|r| !r.trim().is_empty()),
            },
        ))
    })?;
    let mut credits = TitleCredits::default();
    for row in rows {
        let (dept, credited) = row?;
        if dept == "cast" {
            credits.cast.push(credited);
        } else {
            credits.crew.push(credited);
        }
    }
    Ok(credits)
}

/// One person's own name and the keys of every title they are credited on,
/// ordered by id — the same order the web player's `personFor` reads them.
#[derive(Debug, Clone, PartialEq)]
pub struct PersonCredits {
    pub name: String,
    pub title_keys: Vec<String>,
}

/// A person's own titles, or `None` when nothing credits this id — either
/// nobody by it exists, or the index has no `credits` table.
pub fn for_person(conn: &Connection, person_id: u64) -> rusqlite::Result<Option<PersonCredits>> {
    if !has_table(conn)? {
        return Ok(None);
    }
    let mut stmt = conn
        .prepare("SELECT kind, id, name FROM credits WHERE source = ?1 AND person_id = ?2 ORDER BY id")?;
    let rows = stmt.query_map(params![SOURCE, person_id], |row| {
        let kind: String = row.get(0)?;
        let id: i64 = row.get(1)?;
        let name: String = row.get(2)?;
        Ok((format!("tmdb-{kind}-{id}"), name))
    })?;
    let mut name = None;
    let mut title_keys = Vec::new();
    for row in rows {
        let (key, row_name) = row?;
        if name.is_none() {
            name = Some(row_name);
        }
        if !title_keys.contains(&key) {
            title_keys.push(key);
        }
    }
    Ok(name.map(|name| PersonCredits { name, title_keys }))
}

/// One name found by [`people_matching`]: who they are and the keys of
/// every title they are credited on.
#[derive(Debug, Clone, PartialEq)]
pub struct PeopleHit {
    pub person_id: u64,
    pub name: String,
    pub title_keys: Vec<String>,
}

/// How many of the closest matches [`people_matching`] returns — the web
/// player's own `peopleSearch` default.
const PEOPLE_LIMIT: usize = 12;

/// People whose name contains every word of `query`, in either spelling of
/// an umlaut — the same fold a title search matches with — most-credited
/// first, then alphabetically. Empty for a query with no words, or an index
/// with no `credits` table.
pub fn people_matching(conn: &Connection, query: &str) -> rusqlite::Result<Vec<PeopleHit>> {
    let words = terms(Some(query));
    if words.is_empty() || !has_table(conn)? {
        return Ok(Vec::new());
    }
    let mut stmt = conn.prepare(
        "SELECT person_id, MIN(name) AS name,
                GROUP_CONCAT(DISTINCT 'tmdb-' || kind || '-' || id) AS keys
           FROM credits WHERE source = ?1 GROUP BY person_id",
    )?;
    let rows = stmt.query_map(params![SOURCE], |row| {
        let person_id: u64 = row.get(0)?;
        let name: String = row.get(1)?;
        let keys: String = row.get(2)?;
        Ok((person_id, name, keys))
    })?;

    let collator = crate::search::rank::collator();
    let mut hits: Vec<PeopleHit> = Vec::new();
    for row in rows {
        let (person_id, name, keys) = row?;
        let forms = variants(Some(&name));
        if !words.iter().all(|word| forms.iter().any(|form| form.contains(word.as_str()))) {
            continue;
        }
        hits.push(PeopleHit {
            person_id,
            name,
            title_keys: keys.split(',').map(str::to_string).collect(),
        });
    }
    hits.sort_by(|a, b| {
        b.title_keys.len().cmp(&a.title_keys.len()).then_with(|| collator.compare(&a.name, &b.name))
    });
    hits.truncate(PEOPLE_LIMIT);
    Ok(hits)
}

/// Whether this index carries a `credits` table at all — absent in v8 and
/// older, so every reader here treats it as optional rather than erroring
/// on a table that does not exist.
pub(super) fn has_table(conn: &Connection) -> rusqlite::Result<bool> {
    conn.query_row(
        "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'credits')",
        [],
        |row| row.get(0),
    )
}

#[cfg(test)]
#[path = "credits_read_tests.rs"]
mod tests;
