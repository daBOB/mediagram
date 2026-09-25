//! Reading the stored schema version and applying ordered migrations.

use anyhow::{Context, Result};
use rusqlite::Connection;

use super::db::{get_meta, set_meta};

/// Only absent version storage means an uninitialized database. A query or
/// parse failure must be reported before any migration changes the schema.
pub(super) fn version(conn: &Connection) -> Result<i64> {
    let has_meta: bool = conn
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM sqlite_schema WHERE name = 'meta')",
            [],
            |row| row.get(0),
        )
        .context("checking schema version storage")?;
    if !has_meta {
        return Ok(0);
    }
    let Some(stored) = get_meta(conn, "schema_version")? else {
        return Ok(0);
    };
    let version: i64 = stored
        .parse()
        .context("invalid schema version in library index")?;
    anyhow::ensure!(
        version >= 0,
        "invalid schema version in library index: {version}"
    );
    Ok(version)
}

/// Applies every group above the recorded version atomically. Advancing the
/// version in the same transaction makes interrupted upgrades retryable.
pub(super) fn apply(conn: &Connection) -> Result<()> {
    let current = version(conn)?;
    if current >= mlib_spec::schema::SCHEMA_VERSION {
        return Ok(());
    }
    let transaction = conn
        .unchecked_transaction()
        .context("starting the migration transaction")?;
    for (index, group) in mlib_spec::schema::GROUPS.iter().enumerate() {
        let version = index as i64 + 1;
        if version <= current {
            continue;
        }
        for statement in group.iter() {
            transaction
                .execute(statement, [])
                .with_context(|| format!("migrating to v{version}: {statement}"))?;
        }
    }
    set_meta(
        &transaction,
        "schema_version",
        &mlib_spec::schema::SCHEMA_VERSION.to_string(),
    )
    .context("recording schema version")?;
    transaction.commit().context("committing the migration")
}
