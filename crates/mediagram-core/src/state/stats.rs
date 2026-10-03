//! Viewing stats: how long each profile watched what, and when — recorded
//! by this device's own position writes, synced as rows each device owns,
//! and summed for the stats page. Pinned to the web by the `stats-*.json`
//! fixtures under `web/test/fixtures/watch-state/`.

mod calendar;
pub mod summary;
