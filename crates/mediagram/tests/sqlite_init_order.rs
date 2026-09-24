//! This binary links one SQLite library used by two crates. `libsql`, which
//! backs the Telegram session store, calls `sqlite3_config` the first time it
//! opens a database and asserts the call succeeded. `sqlite3_config` fails
//! with MISUSE once SQLite has been initialized, and `rusqlite` initializes it
//! the moment it opens `library.db`.
//!
//! Every command opens the index before connecting to Telegram, which is the
//! order that aborted the process. Opening the index now lets libsql
//! configure SQLite first, so that order is safe for any caller.
//!
//! One test per binary on purpose: the configuration happens once per
//! process, so a second test here would only ever see it already done.

use grammers_session::storages::SqliteSession;
use mediagram::index::db;

#[tokio::test]
async fn the_session_store_opens_after_the_index_without_any_setup_call() {
    let dir = tempfile::tempdir().unwrap();

    let _conn = db::open(dir.path()).unwrap();

    // Before the index configured SQLite on the session store's behalf, this
    // is where libsql asserted and took the process down.
    SqliteSession::open(dir.path().join("session.sqlite"))
        .await
        .unwrap();
}
