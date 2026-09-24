//! Persistent per-install identity for watch-state documents.

use rusqlite::{Connection, OptionalExtension, params};

const DEVICE_KEY: &str = "device_id";

/// This install's own id in the sync channel — a random string, made once
/// and kept in `state.db` for its life. Never the hostname: two installs on
/// identically named machines must not collide, and this id sits in plain
/// sight in every caption this device writes.
pub fn device_id(conn: &Connection) -> rusqlite::Result<String> {
    if let Some(id) = read_device_id(conn)? {
        return Ok(id);
    }
    let made = mint_device_id();
    conn.execute(
        "INSERT INTO state_meta(key, value) VALUES (?1, ?2) ON CONFLICT(key) DO NOTHING",
        params![DEVICE_KEY, made],
    )?;
    // Read back rather than trusting `made`: this proves the row exists
    // rather than assuming the insert landed ahead of whatever else may
    // have raced it to `ON CONFLICT DO NOTHING`.
    Ok(read_device_id(conn)?.unwrap_or(made))
}

fn read_device_id(conn: &Connection) -> rusqlite::Result<Option<String>> {
    conn.query_row(
        "SELECT value FROM state_meta WHERE key = ?1",
        [DEVICE_KEY],
        |row| row.get(0),
    )
    .optional()
}

/// 128 bits from the OS — the same shape `api::channel::library`'s handle
/// uses: opaque, unguessable, and unrelated to any hostname or serial
/// number that could otherwise leak into a caption on a shared channel.
fn mint_device_id() -> String {
    let mut bytes = [0u8; 16];
    getrandom::fill(&mut bytes).expect("the OS random source is available");
    hex::encode(bytes)
}

#[cfg(test)]
#[path = "device_tests.rs"]
mod tests;
