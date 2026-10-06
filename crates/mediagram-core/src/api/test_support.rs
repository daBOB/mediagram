//! Builders for tests, split out of `mod.rs` to keep that file under the
//! line limit `code_standards.rs` enforces.

use std::path::Path;
use std::sync::Arc;

use rusqlite::Connection;

use super::Core;

impl Core {
    /// A core over `dir` with placeholder credentials, for tests that never connect.
    pub(crate) fn at(dir: &Path) -> Arc<Self> {
        Core::new(
            dir.display().to_string(),
            1,
            "test-hash".into(),
            "test-device".into(),
        )
    }
}

/// `<dir>/catalog/current/library.db`, migrated to this crate's current
/// schema. `current_dir` reads a plain join, not a symlink, so a test
/// directory serves as well as a real install.
pub(crate) fn index_at(dir: &Path) -> Connection {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}
