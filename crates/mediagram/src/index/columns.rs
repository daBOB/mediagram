//! Reading text columns that hold a value in some encoding. A value that does
//! not decode is reported as a conversion failure of its column, the same as
//! any other bad value, rather than read as an empty default.
//!
//! Sizes and offsets need no helper: rusqlite reads a `u64` straight from an
//! integer column and rejects a negative one as out of range.

use rusqlite::Row;
use rusqlite::types::Type;

/// A text column decoded by `decode`.
pub(crate) fn decoded<T, E>(
    row: &Row<'_>,
    column: &str,
    decode: impl FnOnce(&str) -> Result<T, E>,
) -> rusqlite::Result<T>
where
    E: std::error::Error + Send + Sync + 'static,
{
    let text: String = row.get(column)?;
    let index = row.as_ref().column_index(column)?;
    decode(&text)
        .map_err(|err| rusqlite::Error::FromSqlConversionFailure(index, Type::Text, Box::new(err)))
}
