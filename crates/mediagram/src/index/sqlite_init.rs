//! Letting the session store configure SQLite before the index touches it.
//!
//! This binary links a single SQLite library used by two crates. `libsql`,
//! under the Telegram session store, calls `sqlite3_config(SERIALIZED)` the
//! first time it opens any database and asserts the call succeeded.
//! `sqlite3_config` returns MISUSE once SQLite has been initialized, and
//! `rusqlite` initializes it the moment it opens `library.db`. So whichever
//! of the two opens second decides whether the process aborts.
//!
//! Every way into the index goes through [`configure`] first, so the order
//! holds for any caller — a command, a test, a future entry point — rather
//! than only for those that remember to call something at startup.

use std::path::Path;
use std::sync::Once;

use grammers_session::storages::SqliteSession;
use rusqlite::Connection;

/// Has libsql run its one-time configuration, if it has not already.
///
/// An in-memory session is enough: libsql configures SQLite on its first
/// open of anything, so there is no reason to create or touch the real
/// session file just to get there. Opening a local database does no I/O on
/// an async runtime, so blocking on it here is safe from sync and async
/// callers alike.
pub fn configure() {
    static CONFIGURED: Once = Once::new();
    CONFIGURED.call_once(|| {
        if let Err(err) = futures::executor::block_on(SqliteSession::open(":memory:")) {
            tracing::warn!(error = %err, "the session store could not configure SQLite first");
        }
    });
}

/// A plain connection to `path` (`":memory:"` included), opened only after
/// [`configure`]. Every open in this crate goes through here or through
/// `db::open`: a bare `Connection::open` initializes SQLite, and a session
/// opened on another thread afterwards aborts the process.
pub fn open(path: impl AsRef<Path>) -> rusqlite::Result<Connection> {
    configure();
    Connection::open(path)
}
