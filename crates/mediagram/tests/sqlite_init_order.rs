//! This binary links one SQLite library used by two crates. `libsql`, which
//! backs the Telegram session store, calls `sqlite3_config` the first time it
//! opens a database and asserts the call succeeded. `sqlite3_config` fails
//! with MISUSE once SQLite has been initialized, and `rusqlite` initializes it
//! the moment it opens `library.db`.
//!
//! Every command opens the index before connecting to Telegram, so without
//! the fix below every one of them panics. Only `smoke-upload`, which never
//! touches the index, escaped it, which is why the single live test that ever
//! ran passed.

use mediagram::index::db;
use mediagram::telegram::client::preinit_session_store;

#[tokio::test]
async fn the_session_store_can_still_start_after_the_index_is_open() {
    let dir = tempfile::tempdir().unwrap();

    // The order every command uses: the session store is configured at
    // startup, then the index is opened.
    preinit_session_store(dir.path()).await.unwrap();
    let _conn = db::open(dir.path()).unwrap();

    // Opening the session store again must not panic; before the fix, this is
    // where libsql asserted.
    preinit_session_store(dir.path()).await.unwrap();
}

#[tokio::test]
async fn preinit_is_safe_to_call_repeatedly() {
    let dir = tempfile::tempdir().unwrap();
    for _ in 0..3 {
        preinit_session_store(dir.path()).await.unwrap();
    }
    let _conn = db::open(dir.path()).unwrap();
}
