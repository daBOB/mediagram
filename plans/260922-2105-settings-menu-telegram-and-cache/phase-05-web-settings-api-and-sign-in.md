# Phase 05 — Web settings API, sign-in flow, wiring

## Context links
- Phase 01 (gate, stores), 02 (holder, runtime, `setRouter`), 03 (budget), 04 (libraries)
- `web/src/routes.ts:378-392` (state/status routers answer before the method gate), `PlayerRequest` type
- `web/src/server.ts:60-86` (`describe` builds `PlayerRequest` — no cookie today)
- `web/src/login.ts` (phone/code/2FA callbacks; `floodSleepThreshold: 0`; "never AUTH_USER_CANCEL")
- Android flow: `android/feature/catalog/src/main/kotlin/login/LoginViewModel.kt:67-104` (phone → code → password)

## Overview
Priority P1. Status: **done 2026-09-26**. One admin-gated router under `/api/settings`, the
sign-in state machine, and all wiring into `index.ts`.

Built as specified with these differences. `refuseUnsafeBrowserWrite` (same-origin + JSON-only
guard) already existed in `web/src/http/browser-write.ts` and is reused directly — no
`request-guard.ts` extraction was needed, it was already the one shared implementation
`state/routes.ts` used. The settings route is not wired into `createRouter`'s option list as a
plain field checked once: `index.ts` builds `follower` and the `SettingsRuntime` *after*
`startServer` (they need `server.replaceCatalog` and the follower needs the running server),
so `routes.ts` gained a `settings` option slot backed by a mutable box
(`{ route: SettingsRoute | null }`) the running server already closes over — filled in once
`runtime`/`gate` exist, the same way `replaceCatalog` lets the catalog itself become live after
the fact. `PlayerRequest.cookie` was added to `http/contracts.ts` and read in `server.ts`.
Business logic lives in `settings/context.ts` (`SettingsRuntime`: view, cache, library) and
`settings/account-actions.ts` (`AccountActions`: app id/hash, sign-in, sign-out) — split in two
because the combined file would not fit this project's 200-line ceiling for a new file, not for
any architectural reason. Sign-in (`settings/sign-in.ts`) uses raw `auth.SendCode` /
`auth.SignIn` / `account.GetPassword` + `computeCheck` (from `teleproto/Password`) /
`auth.CheckPassword` rather than `client.start()` — the plan flagged the exact teleproto method
names as unverified, and the state-machine shape `client.start()` wants (one interactive
callback-driven call) does not fit an HTTP request per step; this was verified structurally
against teleproto's TL types at write time (field names, response classes) but never against a
live account — see phase report. Sign-in of a different account does **not** clear the
previously chosen channel outright (a nullable `chatId`/`accessHash` would have meant threading
`null` through `Telegram.open`/`bareChannelId` everywhere): the channel is left in place, the
next read against it fails exactly as it would if the channel had changed ownership, and the
sign-in answer carries a `differentAccount` flag so the page can prompt "Change library" without
the backend forcing the state. Everything else (routes, request/response shapes, prove-then-
persist-then-swap order, rollback on a bad app id/hash, one pending sign-in with a 10-minute
TTL) matches the plan.

## Key insights
- Sign-in uses a **separate** pending client on an empty session → a new auth key, so it
  never collides with the live client. Only the final swap touches the live one.
- `phone_code_hash` and the pending client never leave the server; the browser only sees steps.
- Sign-in of another account invalidates the chosen channel (access hash is per account):
  compare user ids; different → clear channel, answer `step: "library"`.
- teleproto sleeps through flood waits unless `floodSleepThreshold: 0` (`login.ts`); surface them.

## Requirements (routes; all own-network + unlocked, writes same-origin JSON)
| Method path | Body | Answer |
|---|---|---|
| GET/HEAD `/api/settings` | – | `{locked}` when locked (401); else `{telegram:{signedIn, connected, account:{name, username}, dc, library:{title}\|null, app:{apiId, apiHashSet}}, cache:{budget, heldBytes, source, min}}` |
| POST `/unlock` · `/lock` | `{token}` · – | cookie set/cleared |
| PUT `/cache` | `{maxBytes}` | `{budget, heldBytes, freedBytes}` |
| GET `/libraries` | – | `[{handle, title, current}]` |
| POST `/library` | `{handle}` | `{title, sets}` or `{error}` (04 sentences) |
| PUT `/telegram/app` | `{apiId, apiHash}` | restart; `{signedIn, connected}`; rollback on failure |
| POST `/telegram/sign-in/phone` · `/code` · `/password` | `{phone}` · `{code}` · `{password}` | `{step: "code"\|"password"\|"library"\|"done", viaApp?, error?}` |
| POST `/telegram/sign-out` | – | `auth.LogOut` best effort, session → `null`, holder restart to null |
- Never in any answer: `apiHash`, session, access hash, phone number, code hash.
- F: `PlayerRequest.cookie`; responses may carry `set-cookie`.
- F: one pending sign-in at a time, 10 min TTL, disconnected on expiry/replace.
- F: startup: cache budget via 03 precedence; telegram via 01 precedence; runtime via 02.

## Architecture
```
settings/routes.ts ── gate(01) ──┬─ read view  ← holder, settings, runtime facts, getMe (cached 60 s)
                                 ├─ cache      → budget.applyBudget (03)
                                 ├─ libraries  → libraries.ts + channel-install (04) → runtime swap (02)
                                 ├─ app creds  → holder.restart(open(newCreds, session)) → persist on success
                                 └─ sign-in/out → sign-in.ts (pending client) → persist → holder.restart
```
Persistence order everywhere: **prove, then persist, then swap** (install/connect first; write `telegram.json` only on success).

## Related code files
Create: `web/src/settings/routes.ts` (dispatch + read view, <200 lines),
`web/src/settings/telegram-actions.ts` (app creds, library, sign-out),
`web/src/settings/sign-in.ts` (pending client state machine),
`web/test/settings-http.test.ts`, `web/test/settings-sign-in.test.ts`.
Modify: `web/src/routes.ts` (`PlayerRequest.cookie`; settings router option consulted before method gate — ~10 lines),
`web/src/server.ts` (read `cookie` header), `web/src/index.ts` (wire everything),
`web/src/state/routes.ts` (use extracted `web/src/request-guard.ts`; create that file),
`web/src/telegram/client.ts` (`account()`: getMe name/username + `session.dcId`).
Delete: none. (`login.ts` stays: CLI path still valid, writes env the file overrides.)

## Implementation steps
1. Add `cookie` to request; settings router slot in `createRouter`.
2. `settings/routes.ts`: 404 off-network, unlock/lock, 401 locked, dispatch; same-origin+JSON via a shared guard (extract `refuseUnsafe` from `state/routes.ts` into `web/src/request-guard.ts`, DRY).
3. Read view; cache handler.
4. Library handlers: list; choose → 04 install → `openLibraryRuntime` → `createRouter` → `setRouter` → persist channel → close old runtime after drain.
5. App creds: validate (id positive int, hash 32 hex) → `holder.restart` with new creds + current session → authorised? persist : rollback to old creds, answer error.
6. `sign-in.ts`: phone → `sendCode` (pending client), code → `auth.SignIn`, `SESSION_PASSWORD_NEEDED` → password step (SRP via teleproto helper). Verify teleproto method names in its typings first [UNVERIFIED].
7. On success: session string; same user id? keep channel : clear; best-effort `auth.LogOut` of old session (Q7); persist; `holder.restart`.
8. Sign-out: `auth.LogOut` (ignore failure), persist `session: null`, restart to null.
9. Tests with fake holder/fake teleproto seam: gate (404/401/200), CSRF (cross-origin 403, form content-type 403), no secret in any JSON (snapshot scan for hash/session substrings), rollback on bad creds, sign-in step transitions incl. wrong code retry, flood wait surfaced, TTL expiry.

## Todo
- [ ] request cookie + router slot
- [ ] shared request guard
- [ ] settings router + read view + cache
- [ ] library switch wiring
- [ ] app creds restart with rollback
- [ ] sign-in state machine
- [ ] sign-out
- [ ] index.ts wiring
- [ ] tests

## Success criteria
- `bun test` green; secret-scan test asserts no response contains the configured hash/session.
- Stub run (phase 06) walks every step without a real Telegram.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| Bad api hash leaves player offline | M×H | restart rollback to previous creds, tested |
| Code sent to phone, attacker completes | L×H | gate + code never leaves Telegram app; pending attempt bound to unlocking cookie |
| Flood wait on repeated codes | M×M | surface wait seconds; no auto-retry |
| Library switch during playback | M×M | old router drains; set ids content-addressed; new catalog may lack title → 404 on next request |

## Security
Pending sign-in tied to the admin session id; password never logged; error text is the app's own.

## Next steps
06 renders it; 09 documents.
