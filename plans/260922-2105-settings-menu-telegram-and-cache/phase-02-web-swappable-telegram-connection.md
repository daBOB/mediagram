# Phase 02 — Web swappable Telegram connection + rebuildable runtime

## Context links
- `web/src/telegram/client.ts:22-26` (`Telegram(client, channel)`), `:43-68` (`connect`, throws when unauthorized at `:54`)
- `web/src/telegram/source.ts` (`TelegramSource(telegram, reader)`), `web/src/telegram/state-channel.ts:66`
- `web/src/index.ts:111-117` (db, posters), `:123` (`Telegram.connect`), `:185` (`StateSync`), `:208` (`TelegramSource`), `:213` (`HeldSets`), `:257` (`startServer`)
- `web/src/server.ts:155` (`createRouter` built once), `web/src/routes.ts:332-376` (router closes over db/search index)
- `web/src/login.ts:4-8` — two MTProto clients on one auth key break each other (measured)

## Overview
Priority P1. Status: **done 2026-09-26**. Makes the Telegram client and the catalog
replaceable at runtime, with no settings UI yet. Pure refactor + signed-out mode; behaviour
with an unchanged `.env` is identical (the full existing `bun test` suite is the oracle and
passes unmodified in its assertions, only in its fixtures — see below).

Built substantially smaller than planned, because most of "library-runtime.ts" already
existed by 2026-09-26: `server.ts` already exposed `replaceCatalog`, and
`application/catalog-follow.ts`'s `CatalogFollower` already did the install→verify→swap→retire
dance this phase's `library-runtime.ts` was meant to extract. No `library-runtime.ts` or
`setRouter` were written; `CatalogFollower` instead gained one method, `retarget(root)`, so a
channel switch (05) can point it at a fresh per-channel directory without rebuilding it.
`Telegram.connect` stayed (CLI/tests still want the throwing form) and a new `Telegram.open`
returns `null` instead of throwing; `Telegram.withChannel(existing, chatId, accessHash)` reuses
a client's connection for a new channel. The holder is `telegram/connection.ts`'s
`TelegramConnection` (`current()`, `ready()`, `generation`, `withChannel()`, `restart()`).
`TelegramSource`/`TelegramStateChannel` take the holder and read it fresh per call rather than
holding a `Telegram`; every test that built one directly now wraps a fake in
`TelegramConnection.fixed(telegram)`. The update listener (`LibraryUpdates`) is the one thing
that still binds to one concrete client/channel at construction, so it is rebuilt via a new
`application/telegram-binding.ts` (`UpdatesBinding`) on every restart or channel switch — kept
apart from the holder because its `start()` must run *before* the HTTP server does (a listener
bound and torn down on a failed server bind must not depend on how far startup got — proven by
`application-startup.test.ts`'s EADDRINUSE case) while its `followCatalog()` must run *after*
the server and follower exist, unawaited, exactly as the original single-phase `updates.start()`
/ `updates.followCatalog()` split did.

## Key insights
- Every consumer holds the `Telegram` instance directly (source, state channel, index.ts
  status `telegramConnected`). Swapping needs one indirection, not edits everywhere.
- Channel switch ≠ client restart: `Telegram` = client + `InputChannel`, so a new channel is
  `new Telegram(sameClient, newChannel)` — no reconnect, no auth-key risk.
- Client restart (api id/hash, sign in/out) must be strictly sequential: old down before
  new up. Reads during that window must wait, not fail.
- `createRouter` is a pure factory (`routes.ts:332`); rebuilding it on a library change is
  cheaper and safer than making db/search index mutable inside a 737-line module.
- `index.ts` is 339 lines; the rebuildable part moves out, shrinking it.

## Requirements
- F: `TelegramConnection` holder: `current(): Telegram|null`, `generation: number`,
  `ready(): Promise<Telegram|null>` (waits while a swap is in progress),
  `withChannel(chatId, accessHash)` (no reconnect), `restart(open: () => Promise<Telegram|null>)`
  (gate → disconnect old → open new → on failure reopen previous creds → release).
- F: signed-out mode: no/unauthorized session → `current()` is `null`; catalog, state, cached
  chunks still served; uncached reads fail fast with a clear error; `/api/status` says signed out.
- F: `TelegramSource` takes the holder; a read that errors while `generation` changed retries
  once after `ready()`. Cancel is never retried.
- F: `TelegramStateChannel` takes the holder; sync round with no client = skipped, not failed.
- F: `server.ts` exposes `setRouter(route)`; in-flight responses keep the router they started with.
- F: `runtime/library-runtime.ts`: `openLibraryRuntime(indexPath, posterDirs, cache, …)` →
  `{db, posters, held, playableCount, catalogFacts, close()}`; index.ts uses it at start.
- NF: swap serialised by a single promise chain; no two restarts overlap.

## Architecture
```
               ┌──────── TelegramConnection (generation, gate) ────────┐
reads ─▶ TelegramSource ─▶ ready() ─▶ Telegram{client, channel}        │
sync  ─▶ TelegramStateChannel ─▶ current()                              │
status ─▶ connected / signedIn                                           │
restart(): close gate → old.disconnect() → open new → gen++ → open gate ┘
library change: openLibraryRuntime(new index) → createRouter(...) → server.setRouter → old.close() after drain
```
Old catalog `Database` closed only after in-flight requests on the old router finish
(count in router wrapper; close when zero or after 60 s).

## Related code files
Create: `web/src/telegram/connection.ts`, `web/src/runtime/library-runtime.ts`,
`web/test/telegram-connection.test.ts`, `web/test/library-runtime.test.ts`.
Modify: `web/src/telegram/client.ts` (`open(creds, channel|null)` returns `null` when
unauthorized instead of throwing; `withChannel`; `connected`), `web/src/telegram/source.ts`,
`web/src/telegram/state-channel.ts`, `web/src/server.ts` (`setRouter`), `web/src/index.ts`
(use holder + runtime; move blocks out), `web/test/telegram-source.test.ts`.
Delete: none.

## Implementation steps
1. `connection.ts`: holder with gate (a `Promise` resolved on release), `generation`, serialised `restart`.
2. `client.ts`: split `connect` into `open` (null when unauthorized; logs one line, no secret); add `withChannel`.
3. `source.ts`: take holder; on uncached miss `await holder.ready()`; null → error "signed out"; retry-once rule keyed on generation.
4. `state-channel.ts`: take holder; no client → `StateSync.once` reports skipped.
5. `server.ts`: keep `let route`; `setRouter(next)`; count in-flight per router instance.
6. `library-runtime.ts`: move db/posters/held/playableCount/facts assembly from index.ts; `close()`.
7. `index.ts`: build holder + runtime; everything else unchanged. Target < 250 lines.
8. Tests: restart ordering (old disconnected before new opened — fake clients record order); read parked during swap resumes; read failing across generation retries once; signed-out read fails fast; router swap leaves in-flight response intact.

## Todo
- [ ] connection holder + tests
- [ ] client open/withChannel
- [ ] source + state channel via holder
- [ ] server setRouter
- [ ] library runtime extraction
- [ ] index.ts slimmed; full `bun test` green

## Success criteria
- Existing test suite unchanged and green; new tests cover the 5 scenarios above.
- Stub harness (`stub-offline.ts` pattern) still plays fully-held sets with Telegram absent.
- Starting with no `MEDIAGRAM_SESSION` no longer exits; log says "telegram: signed out".

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| Two clients on one key during restart | M×H | strict order enforced + tested with fake client recorder |
| ffmpeg transcodes read via own HTTP route; a failed read kills a transcode | M×M | parked reads + retry-once; transcode reads go through the same source |
| Closing old catalog db under a live request | L×H | in-flight counter per router, close after drain |
| Behaviour drift in refactor | M×M | no logic change; existing tests are the oracle |

## Security
`open()` error text never includes session/hash; signed-out status exposes only a boolean.

## Next steps
03 and 04 build on the holder/runtime; 05 wires them to routes.
