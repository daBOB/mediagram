//! A local HTTP API exposing what is playable and the bytes of a set as one
//! virtual file. Kept as the reference twin of the web player's routes and
//! Range responses (`web/src/routes.ts`, `web/src/response.ts`,
//! `web/src/range.ts`); its router also backs the loopback server of
//! `subtitles backfill --channel` (`commands::subtitles::loopback`). The web
//! player and the Android app read Telegram themselves, not through this.

pub mod response;
pub mod routes;
