//! Refusing a push that would take titles out of the channel's index.
//!
//! A push replaces the channel's index wholesale, and more than one machine
//! uploads to the same channel. A machine whose index lacks what another one
//! uploaded would, by pushing, remove those titles from every player and
//! phone until the other machine pushed again. So before sending, the
//! channel's newest index is read and compared: any set it holds that this
//! index does not stops the push, unless the caller forces it.

use std::collections::HashSet;
use std::path::Path;

use anyhow::{Context, Result, bail};
use grammers_client::message::Message;
use rusqlite::Connection;

use crate::telegram::client::Tg;
use mediagram_core::transport::document::message_document;

/// How many of the missing set ids a refusal names, so it says what it found
/// without printing hundreds of ids.
const NAMED: usize = 5;

/// The set ids `remote` holds that `local` does not, sorted.
pub fn missing_from(local: &Connection, remote: &Connection) -> Result<Vec<String>> {
    let here = set_ids(local)?;
    let mut missing: Vec<String> = set_ids(remote)?.into_iter().filter(|id| !here.contains(id)).collect();
    missing.sort();
    Ok(missing)
}

fn set_ids(conn: &Connection) -> Result<HashSet<String>> {
    let mut stmt = conn.prepare("SELECT set_id FROM sets").context("reading set ids")?;
    let ids = stmt.query_map([], |row| row.get::<_, String>(0))?;
    ids.collect::<rusqlite::Result<_>>().context("reading set ids")
}

/// Fails when the channel's newest index holds sets `local` lacks. A channel
/// with no index yet, or one whose index cannot be read, is no reason to
/// refuse: the guard exists to keep titles, not to block the first push.
pub async fn refuse_if_channel_has_more(tg: &Tg, local: &Connection, scratch: &Path) -> Result<()> {
    let Some(message) = newest_pinned_index(tg).await? else {
        return Ok(());
    };
    let Some((document, _)) = message_document(&message) else {
        return Ok(());
    };
    let path = scratch.join(format!("library.guard.{}.db", std::process::id()));
    let outcome = compare(tg, &document, &path, local).await;
    let _ = std::fs::remove_file(&path);
    let missing = outcome?;
    if missing.is_empty() {
        return Ok(());
    }
    let shown: Vec<&str> = missing.iter().take(NAMED).map(String::as_str).collect();
    bail!(
        "the channel's index holds {} set(s) this index does not (e.g. {}). Pushing would remove \
         them from every player and phone. Push from the machine that has them, or run \
         `mediagram push-index --force` to replace the channel's index anyway",
        missing.len(),
        shown.join(", ")
    )
}

async fn compare(
    tg: &Tg,
    document: &grammers_client::media::Document,
    path: &Path,
    local: &Connection,
) -> Result<Vec<String>> {
    let mut bytes = Vec::new();
    let mut chunks = tg.client.iter_download(document);
    while let Some(chunk) = chunks.next().await.context("downloading the channel's index")? {
        bytes.extend_from_slice(&chunk);
    }
    std::fs::write(path, &bytes).with_context(|| format!("writing {}", path.display()))?;
    // Through `sqlite_init`, as every open here must: a bare open while the
    // Telegram session is live can abort the process.
    let remote = crate::index::sqlite_init::open(path).context("opening the channel's index")?;
    missing_from(local, &remote)
}

/// The newest pinned index by the time its caption says it was pushed, the
/// rule every reader uses; the message id breaks a tie.
async fn newest_pinned_index(tg: &Tg) -> Result<Option<Message>> {
    let mut newest: Option<(i64, i32, Message)> = None;
    let mut pinned = tg
        .client
        .search_messages(tg.channel)
        .filter(grammers_tl_types::enums::MessagesFilter::InputMessagesFilterPinned);
    while let Some(message) = pinned.next().await.context("listing pinned messages")? {
        if !mlib_spec::index_caption::is_index(message.text()) {
            continue;
        }
        let at = mlib_spec::index_caption::pushed_at(message.text()).unwrap_or(0);
        let key = (at, message.id());
        if newest.as_ref().is_none_or(|(a, id, _)| key > (*a, *id)) {
            newest = Some((at, message.id(), message));
        }
    }
    Ok(newest.map(|(_, _, message)| message))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn index(ids: &[&str]) -> Connection {
        let conn = crate::index::sqlite_init::open(":memory:").unwrap();
        conn.execute("CREATE TABLE sets(set_id TEXT PRIMARY KEY)", []).unwrap();
        for id in ids {
            conn.execute("INSERT INTO sets(set_id) VALUES (?1)", [id]).unwrap();
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
}
