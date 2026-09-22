//! Lifecycle states of a set and of a part, as the index spells them.
//!
//! Typed so that a misspelt state is a compile error rather than a comparison
//! that is silently never true. The spellings are part of the schema:
//! `mlib_spec::schema::PLAYABLE_SQL` and the player both read them, so they
//! are written here once and never re-typed at a call site.

use rusqlite::ToSql;
use rusqlite::types::{FromSql, FromSqlError, FromSqlResult, ToSqlOutput, ValueRef};

/// Where a set is in its upload.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SetStatus {
    /// Planned, with parts still to send.
    Pending,
    /// Every part is in the channel and the set hash is recorded.
    Complete,
}

/// Where one part is in its upload.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PartStatus {
    /// Not yet in the channel.
    Pending,
    /// Sent (or adopted from an existing message), with its hash recorded.
    Done,
}

impl SetStatus {
    pub fn as_str(self) -> &'static str {
        match self {
            SetStatus::Pending => "pending",
            SetStatus::Complete => "complete",
        }
    }
}

impl PartStatus {
    pub fn as_str(self) -> &'static str {
        match self {
            PartStatus::Pending => "pending",
            PartStatus::Done => "done",
        }
    }
}

impl std::fmt::Display for SetStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.as_str())
    }
}

impl std::fmt::Display for PartStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.as_str())
    }
}

impl ToSql for SetStatus {
    fn to_sql(&self) -> rusqlite::Result<ToSqlOutput<'_>> {
        Ok(self.as_str().into())
    }
}

impl ToSql for PartStatus {
    fn to_sql(&self) -> rusqlite::Result<ToSqlOutput<'_>> {
        Ok(self.as_str().into())
    }
}

impl FromSql for SetStatus {
    fn column_result(value: ValueRef<'_>) -> FromSqlResult<Self> {
        match value.as_str()? {
            "pending" => Ok(SetStatus::Pending),
            "complete" => Ok(SetStatus::Complete),
            other => Err(unknown("set", other)),
        }
    }
}

impl FromSql for PartStatus {
    fn column_result(value: ValueRef<'_>) -> FromSqlResult<Self> {
        match value.as_str()? {
            "pending" => Ok(PartStatus::Pending),
            "done" => Ok(PartStatus::Done),
            other => Err(unknown("part", other)),
        }
    }
}

fn unknown(what: &str, spelling: &str) -> FromSqlError {
    FromSqlError::Other(format!("unknown {what} status `{spelling}`").into())
}
