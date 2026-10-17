# Phase 1 — Limit data, sync and rule

## Context
- Template: the kids age limit, end to end (scout 2026-10-17).
- Web: `web/src/state/roles-schema.ts`, `profiles.ts` (`writeKidsAge`), `profiles-manage.ts:96` (`setKidsAge`), `profiles-routes.ts:77`, `roles-record.ts:31,56-59`, `roles-merge.ts`, `roles-exchange.ts`, `merged.ts`, `sync-record.ts`.
- Core: `crates/mediagram-core/src/state/{schema.rs, profiles.rs, profiles/manage.rs, profiles/role_rows.rs, record/roles_record.rs, merge/roles.rs, exchange/roles.rs}`, `api/state/profile_roles.rs`, `api/state/stats.rs`.
- Shared fixtures: `web/test/fixtures/watch-state/` held by `web/test/shared-watch-state-fixtures.test.ts`, `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`, `android/core/model/src/test/kotlin/ProfileRolesFixtureTest.kt`.

## Overview
Priority high, status pending. Store the limit on the profile, sync it as a role key, let a grown-up set it, compute "watched today" and the rule's state.

## Requirements
- Schema: `profiles.daily_limit INTEGER NULL`, `daily_limit_updated_at INTEGER NOT NULL DEFAULT 0` (web migration + core migration, next version each).
- Sync: optional role key `dailyLimit: { minutes: number | null, updatedAt }`; newest write wins, `null` included (so "no limit" can win over an older limit). `SYNC_FORMAT` stays 1; unknown/invalid values ignored on parse (lenient: positive integer ≤ 1440 or null).
- Manage action `setDailyLimit(actorId, pin, kidId, minutes|null)`: kid only; permission identical to `setKidsAge`; web route `PUT /api/profiles/{id}/daily-limit`; core `SetDailyLimit` request.
- Profile view gains `dailyLimit` (web `Profile`, core profile → Android `model.Profile`).
- Watched today: web server `watchedToday(profileId)`; core `watched_today(profile_id, day)`; both `SUM(seconds) FROM stats_days`.
- Rule `dailyLimitState(limitMinutes, watchedSeconds) → { state, leftSeconds }` with the 5 min warn threshold; fixture `daily-limit.json` (edge rows: null, exactly 5:00 left, exactly 0, over).

## Steps
1. Fixtures first: `daily-limit.json` (rule) and new rows in `profile-roles-merge.json` / `profile-roles-record-parse.json`.
2. Web: migration, row read/write, record parse/serialize, merge, exchange, manage + route, `watchedToday`, rule in `web/public/lib/` (browser-importable, as `together/` is).
3. Core: the same in Rust; expose through `api/state` and uniffi.
4. Tests on both sides go red on the fixtures first, then green.

## Todo
- [ ] fixtures
- [ ] web data + sync + manage + route + rule
- [ ] core data + sync + manage + API + rule
- [ ] `bun test`, `cargo test`, clippy

## Success criteria
All three fixture suites pass; a limit set on one surface reaches the other through a sync round (core/web exchange tests); an older limit never overwrites a newer "no limit".

## Risks
- Two implementations (TS, Rust) drift → the shared fixtures are the guard.
- Merge must treat `updatedAt: 0` (never set) as losing to any stamp, as `kidsAge` does.

## Security
Same PIN gate as the age limit; refusals map to the existing HTTP/outcome codes.
