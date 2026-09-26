//! Refusing a push that would take titles out of the channel's index.
//!
//! A push replaces the channel's index wholesale, and more than one machine
//! uploads to the same channel. A machine whose index lacks what another one
//! uploaded would, by pushing, remove those titles from every player and
//! phone until the other machine pushed again. So before sending, the
//! channel's newest index is read and compared: any *complete* set it holds
//! that this index does not stops the push, unless the caller forces it.
//! An unfinished upload listed there is no title any player shows, and the
//! machine uploading it keeps it in its own index, so losing it costs nothing.

use std::collections::HashSet;
use std::path::Path;

use anyhow::{Context, Result, bail};
use rusqlite::Connection;

use crate::telegram::client::Tg;
use crate::telegram::download_index::download_channel_index;

/// How many of the missing set ids a refusal names, so it says what it found
/// without printing hundreds of ids.
const NAMED: usize = 5;

/// The complete set ids `remote` holds that `local` does not, sorted.
pub fn missing_from(local: &Connection, remote: &Connection) -> Result<Vec<String>> {
    let here = set_ids(local, "SELECT set_id FROM sets")?;
    let mut missing: Vec<String> =
        set_ids(remote, "SELECT set_id FROM sets WHERE status = 'complete'")?
            .into_iter()
            .filter(|id| !here.contains(id))
            .collect();
    missing.sort();
    Ok(missing)
}

fn set_ids(conn: &Connection, sql: &str) -> Result<HashSet<String>> {
    let mut stmt = conn.prepare(sql).context("reading set ids")?;
    let ids = stmt.query_map([], |row| row.get::<_, String>(0))?;
    ids.collect::<rusqlite::Result<_>>()
        .context("reading set ids")
}

/// Fails when the channel's newest index holds sets `local` lacks. A channel
/// with no index yet, or one whose index cannot be read, is no reason to
/// refuse: the guard exists to keep titles, not to block the first push.
///
/// `removed` are sets a merge just proved gone from the channel (their part
/// messages were deleted); the channel's index still lists them only because
/// it predates the removal, so dropping them is the point, not a loss.
pub async fn refuse_if_channel_has_more(
    tg: &Tg,
    local: &Connection,
    scratch: &Path,
    removed: &HashSet<String>,
) -> Result<()> {
    let Some(path) = download_channel_index(tg, scratch).await? else {
        return Ok(());
    };
    let outcome = read_and_compare(&path, local);
    let _ = std::fs::remove_file(&path);
    let missing: Vec<String> = outcome?
        .into_iter()
        .filter(|id| !removed.contains(id))
        .collect();
    if missing.is_empty() {
        return Ok(());
    }
    let shown: Vec<&str> = missing.iter().take(NAMED).map(String::as_str).collect();
    bail!(
        "the channel's index holds {} set(s) this index does not (e.g. {}). Pushing would remove \
         them from every player and phone. Run `mediagram push-index --merge` to bring them in \
         and push both, or `mediagram push-index --force` to replace the channel's index anyway",
        missing.len(),
        shown.join(", ")
    )
}

fn read_and_compare(path: &Path, local: &Connection) -> Result<Vec<String>> {
    // Through `sqlite_init`, as every open here must: a bare open while the
    // Telegram session is live can abort the process.
    let remote = crate::index::sqlite_init::open(path).context("opening the channel's index")?;
    missing_from(local, &remote)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn index(ids: &[&str]) -> Connection {
        let conn = crate::index::sqlite_init::open(":memory:").unwrap();
        conn.execute(
            "CREATE TABLE sets(set_id TEXT PRIMARY KEY, status TEXT NOT NULL DEFAULT 'complete')",
            [],
        )
        .unwrap();
        for id in ids {
            conn.execute("INSERT INTO sets(set_id) VALUES (?1)", [id])
                .unwrap();
        }
        conn
    }

    #[test]
    fn what_the_channel_holds_and_this_index_lacks_is_missing() {
        let local = index(&["A", "B"]);
        let remote = index(&["A", "C", "D"]);
        assert_eq!(missing_from(&local, &remote).unwrap(), ["C", "D"]);
    }

    #[test]
    fn an_index_that_holds_everything_and_more_is_missing_nothing() {
        let local = index(&["A", "B", "C"]);
        let remote = index(&["A", "B"]);
        assert!(missing_from(&local, &remote).unwrap().is_empty());
    }

    #[test]
    fn an_unfinished_upload_in_the_channel_is_not_counted_missing() {
        let local = index(&["A"]);
        let remote = index(&["A", "B"]);
        remote
            .execute("UPDATE sets SET status = 'pending' WHERE set_id = 'B'", [])
            .unwrap();
        assert!(missing_from(&local, &remote).unwrap().is_empty());
    }
}
