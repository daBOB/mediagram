# Core: kids daily limit data layer (2026-10-17)

## Public API (uniffi, `Core`)
- `set_daily_limit(actor_id: String, pin: String, id: String, minutes: Option<u16>) -> ProfileOutcome` (async). `None` clears (stamped write of null). Same checks/outcomes as `set_kids_age` (WrongPin, NotAllowed, Wait, NotFound...). `Invalid` for minutes outside 1..=1440 (checked first, like age).
- `create_kid(actor_id, pin, name, kids_age: u8, daily_limit: Option<u16>) -> ProfileOutcome` — NEW 5th param (BREAKING for Kotlin callers; pass null for none). Invalid if limit outside 1..=1440.
- `watched_today(profile_id: String, day: String /* YYYY-MM-DD */) -> f64` (async; seconds, SUM stats_days over all devices; storage failure -> 0.0).
- uniffi `Profile` record gains `daily_limit: Option<u32>` (Kotlin `dailyLimit: UInt?`, default null); minutes for a kid, always None on a grown-up. No new outcome names.
- Rule (free/ok/warn/out) not in Rust.

## Data / sync
- Schema v10: `profiles.daily_limit INTEGER NULL`, `daily_limit_updated_at INTEGER NOT NULL DEFAULT 0` (`schema.rs`; upgrade test now expects 10).
- Sync key `dailyLimit {minutes|null, updatedAt}`: `record::DailyLimit` (parse per key: whole 1..1440 or null, key `minutes` must be present, updatedAt 0..2^53-1). Merge: newest wins, null kept, tie larger device id, kid-only, absent never unsays. Export: emitted for a kid once chosen (minutes set or stamp > 0); import: newer stamp wins, equal stamp differing value takes merge winner.

## Files (all under crates/mediagram-core)
src/state/schema.rs (also condensed the v1->v2 comment to stay <=200 lines), record.rs, record/roles_record.rs, merge/roles.rs, merge/tie_break.rs, exchange/roles.rs, profiles.rs, profiles/role_rows.rs, profiles/manage.rs, NEW profiles/manage/daily_limit.rs, api/state/profile_roles.rs, api/state/stats.rs; tests: roles_record_tests, exchange/roles_tests, profiles/manage_tests, api/state/stats_tests, upgrade_tests, tests/api_surface.rs, tests/state_seed/mod.rs (create_kid call updates).
Harness comparators needed no change (they compare serde-serialized roles); the 5 merge + 3 parse fixture rows pass.

## Results
- `cargo test --workspace`: all green (core lib 759+, fixtures incl. dailyLimit rows, code_standards 200-line test).
- `cargo clippy --all-targets --all-features -- -D warnings`: clean.
- `cargo fmt --check` already differs in untouched files; not enforced by check.sh.

## Open questions
- Android: `create_kid` signature change requires updating Kotlin call sites.
