# Phase 01 — Web settings storage + admin gate

## Context links
- `web/src/config.ts:144-189` (`load`, env-only, `required()` throws), `:192` (`describe`, redaction)
- `web/src/state/schema.ts:14` (`STATE_SCHEMA = 5`), `:19-160` (`GROUPS`)
- `web/src/state/store.ts:604-670` (`open`, `migrate`, `versionOf`)
- `web/src/client-reach.ts` (`isOwnNetwork`, `clientAddress`, trustProxy rule)
- `web/src/state/routes.ts:244-259` (`refuseUnsafe`: same-origin + JSON-only write guard)
- `web/src/status/routes.ts:108` (own-network → 404 rule)
- `web/src/index.ts:306-316` warning: "this API has no authentication"
- `docs/code-standards.md` § Security; `docs/system-architecture.md` § 10 On-disk layout

## Overview
Priority P1 (everything else depends on it). Status: pending.
Adds the two stores settings persist to and the gate every settings route passes.

## Key insights
- **Viewer auth today: none.** `login.ts` is a one-time CLI that writes `web/.env`, not
  viewer auth. Only rule in the API is `isOwnNetwork` for `/api/status`. Anyone reaching
  the port streams the library (`client-reach.ts:60-66`, `index.ts` warning).
- Settings raise the stakes: they can list every channel/group the account is in,
  sign the account out, point the player at another account, or evict the cache.
- State DB is the player's own, migrated by grouped versions; adding v6 is idiomatic.
- `state_meta` exists (`schema.ts:50`) but is internal (schema version, device id); a
  named `settings` table keeps user-set values apart from bookkeeping.

## Threat model
| Actor | Reach | Wants | Stopped by |
|---|---|---|---|
| Internet caller via reverse proxy | port | list channels, sign out, swap account | own-network 404 (trustProxy rules unchanged) |
| Guest/neighbour on LAN or CGNAT (`client-reach.ts:57-61`) | port | same | admin cookie (token) |
| Malicious web page in viewer's browser | CSRF | POST settings | SameSite=Strict cookie + same-origin + JSON-only |
| Log/response reader | logs, JSON | session, api hash | write-only secrets, `describe()` redaction, never echoed |
| Brute force on token | unlock route | token | 256-bit token, 5 tries/min/address, constant-time compare |
Out of scope: LAN sniffing of plain HTTP (documented: use TLS proxy); local root.

## Requirements
- F: `settings` table (v6): `name TEXT PK, value TEXT NOT NULL, updated_at INTEGER`.
- F: `Settings` reader/writer over the state DB handle; tolerant (degrades to env) like `WatchState`.
- F: `telegram.json` store: `{apiId, apiHash, session|null, chatId, accessHash, title}`;
  written atomically (tmp + rename), mode 0600, dir 0700. Key present (even `null`) wins over env.
- F: config precedence helper: `resolveTelegram(env, file)`; `session` no longer `required()`.
- F: admin gate: token from `MEDIAGRAM_ADMIN_TOKEN`, else generated (32 random bytes,
  base64url) into `admin-token` beside `state.db`, 0600; startup prints the *path*, never the token.
- F: `POST /api/settings/unlock {token}` → HttpOnly, SameSite=Strict, Path=/api/settings,
  `Secure` when the request came through a trusted proxy on https; 12 h in-memory session.
- NF: constant-time compare (`crypto.timingSafeEqual`), per-address rate limit, 404 for non-own-network.

## Architecture
```
request ─▶ settings router ─▶ isOwnNetwork? ─no─▶ 404
                               └yes─▶ path == unlock ? verify token ─▶ Set-Cookie
                                      └ cookie session valid? ─no─▶ 401 {locked:true}
                                                              └yes─▶ handler (phase 05)
```
Stores: `state.db` (settings table) · `telegram.json` (secrets+channel) · `admin-token`.

## Related code files
Create: `web/src/settings/admin-gate.ts`, `web/src/settings/telegram-file.ts`,
`web/src/state/settings.ts`, tests `web/test/settings-admin-gate.test.ts`,
`web/test/settings-telegram-file.test.ts`, `web/test/state-settings.test.ts`.
Modify: `web/src/state/schema.ts` (v6 group, `STATE_SCHEMA = 6`), `web/src/state/store.ts`
(one accessor exposing a `Settings` over its db — no other change), `web/src/config.ts`
(session/chat optional; `telegramFilePath`, `adminTokenPath`, `channelCatalogDir` (`MEDIAGRAM_CHANNEL_CATALOG_DIR`, default `~/.cache/mediagram-channel-catalog`, used by 04); `describe` covers new fields),
`web/test/state-migration.test.ts` (v5→v6).
Delete: none.

## Implementation steps
1. Append v6 group to `GROUPS`; bump `STATE_SCHEMA`; migration test from a v5 fixture.
2. `state/settings.ts`: `cacheMaxBytes(): number|null`, `setCacheMaxBytes(n)`; validation on read (hostile row → null).
3. `settings/telegram-file.ts`: `read(path)`, `write(path, value)` atomic, chmod; unit tests for mode, atomicity, `null` session semantics, malformed file → treated as absent + warning.
4. `config.ts`: make `MEDIAGRAM_SESSION`, `_CHAT_ID`, `_CHANNEL_ACCESS_HASH` optional; add file-over-env merge; `describe` redacts apiHash/session from either source.
5. `settings/admin-gate.ts`: load-or-create token; `unlock(req)`, `isUnlocked(req)`, `lock(req)`; cookie parse; session map with expiry sweep; rate limiter map bounded (like `ReadaheadTracker` TRACKED cap).
6. Tests: wrong token, right token, rate limit, expiry, non-own-network 404, cookie flags.

## Todo
- [ ] v6 migration + test
- [ ] Settings accessor + test
- [ ] telegram.json store + tests
- [ ] config precedence + describe redaction test
- [ ] admin gate + tests

## Success criteria
- `bun test` green; migration test proves v5 → v6 keeps all rows.
- `grep -rn "apiHash\|session" ` in any JSON response builder returns nothing new.
- Token file created 0600; startup log shows path only.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| Session made optional breaks startup assumptions | M×H | phase 02 adds signed-out mode before anything reads `null` |
| Token printed to journald | L×H | print path only; test asserts log line |
| Cookie without TLS sniffed on LAN | M×M | documented; `Secure` behind https proxy |

## Security
Secrets write-only; file 0600/dir 0700; no secret in error messages (messages are this app's own, as Android's `SetupViewModel.settle` does).

## Next steps
Phase 02 consumes the telegram file + optional session.
