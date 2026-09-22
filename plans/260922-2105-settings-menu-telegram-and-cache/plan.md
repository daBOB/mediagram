---
title: "Settings menu: Telegram connection and cache size, web + Android"
description: "One Settings surface on both players: connection view, library switch, app id/hash, sign in/out, live cache budget."
status: pending
priority: P2
effort: 38h
branch: feat/settings-menu (cut from main once desloppify/code-health lands)
tags: [web, android, rust-core, telegram, cache, security, settings]
created: 2026-09-22
---

# Settings menu — Telegram and cache (web + Android)

Locked by user (do not re-decide): both surfaces together; Telegram = read-only connection
view + switch library without restart + edit API id/hash + sign in/out; cache size live,
evicts down, persisted in the state DB, overriding `MEDIAGRAM_CACHE_MAX`.

## Phases

| # | Phase | Track | Blocked by | Effort | Status |
|---|---|---|---|---|---|
| 01 | [Web settings storage + admin gate](phase-01-web-settings-storage-and-admin-gate.md) | web | – | 5h | pending |
| 02 | [Web swappable Telegram connection + runtime](phase-02-web-swappable-telegram-connection.md) | web | 01 | 6h | pending |
| 03 | [Web live cache budget](phase-03-web-live-cache-budget.md) | web | 02 | 2h | pending |
| 04 | [Web library switch from channel index](phase-04-web-library-switch-from-channel-index.md) | web+rust test | 02 | 5h | pending |
| 05 | [Web settings API + sign-in flow](phase-05-web-settings-api-and-sign-in.md) | web | 03, 04 | 5h | pending |
| 06 | [Web Settings page](phase-06-web-settings-page.md) | web UI | 05 | 4h | pending |
| 07 | [Rust core: account summary + sign out](phase-07-rust-core-account-and-sign-out.md) | rust | – | 3h | pending |
| 08 | [Android Settings screen](phase-08-android-settings-screen.md) | android | 07 | 6h | pending |
| 09 | [Docs, parity note, versions, validation](phase-09-docs-versions-and-validation.md) | all | 06, 08 | 2h | pending |

Tracks run in parallel: web (01→02→{03,04}→05→06) and Android (07→08). 03 and 04 own
disjoint files; all wiring into `web/src/index.ts`/runtime happens in 05 only.

## Key decisions (this plan)

- **Web admin gate** (01): settings exist only for own-network callers (`isOwnNetwork`,
  404 otherwise, same rule as `/api/status`) **and** require an unlocked admin cookie
  minted from a 0600 admin-token file / `MEDIAGRAM_ADMIN_TOKEN`. Same-origin + JSON-only
  writes (existing CSRF rule). Secrets are write-only: never in any response or log.
- **Where settings live**: cache budget in state DB v6 `settings` table (user decision).
  Account-bound Telegram values (api id/hash, session, chosen channel) in an owner-only
  `telegram.json` beside `state.db`, not in the DB — see Q1. File > env precedence; env
  stays the bootstrap. Android: existing encrypted prefs; cache budget in plain prefs.
- **One auth key, one client** (measured, `web/src/login.ts:4-8`): a client restart is
  gate → disconnect old → connect new → release; never two clients on one key. Channel
  switch needs no restart (same client, new `InputChannel`).
- **In-flight streams**: reads parked at the gate; a read that fails across a swap retries
  once on the new client. Cached chunks never touch Telegram. Cache is content-addressed
  (`set_hash.rs:1-3`), so it survives channel switches.
- **Parity**: web ports Android's already-decided library semantics (channel list →
  newest `#mlib-index` snapshot). `pick_index` pinned by one shared fixture read by both
  Rust and TS tests (`tasks/lessons.md` 2026-09-18). Deliberate difference: web has an
  admin gate, Android does not (device-local app). Written down in 09.

## Rollback

Every phase is additive and revertable by commit. v6 table is `CREATE TABLE IF NOT EXISTS`;
older builds skip unknown versions (`web/src/state/store.ts:643`). Deleting
`telegram.json` returns the web player to env config.

## Unresolved questions

1. Secrets in `telegram.json` (0600) vs in the state DB's v6 table? Plan chooses the file:
   the state DB is copied/backed-up casually and holds no secret today. Confirm.
2. Web sign-in: phone code + 2FA only (Android parity), or also QR login (scan with the
   Telegram app; needs a QR encoder)? Plan: phone code only.
3. Settings reachable only from own network even with the token — acceptable, or must a
   remote (reverse-proxied) admin work too?
4. Channel libraries carry no posters. Web keeps the env catalog's poster directory as a
   fallback when a channel is chosen; web TMDB "fetch missing" is out of scope. OK?
5. Live cache budget floor: plan uses 512 MiB, and "off" (0) stays env-only/restart. OK?
6. Android TV (`ui-tv`) gets no Settings in this plan (module has no screens yet). OK?
7. Should web sign-in of a different account also `auth.logOut` the previous session
   (plan: yes, best effort) — or keep it alive for the uploader-export path?
