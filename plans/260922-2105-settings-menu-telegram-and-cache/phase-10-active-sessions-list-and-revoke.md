# Phase 10 — Active sessions: list and revoke (web + Android)

## Context links
- Research: `plans/reports/research-260922-2212-telegram-api-premium-opportunities-report.md` § 6
- TL (layer 227, grammers-tl-types 0.10 `tl/api.tl`): `account.getAuthorizations = account.Authorizations`,
  `account.resetAuthorization hash:long = Bool`, `authorization{current, official_app, unconfirmed, hash, device_model, platform, system_version, api_id, app_name, app_version, date_created, date_active, ip, country, region}`
- Session naming today: grammers `ConnectionParams::default()` (`grammers-mtsender-0.10.0/src/configuration.rs:66`) → `device_model = "<os> <bitness>"`, `app_version = grammers' own crate version`; teleproto defaults on web
- `crates/mediagram/src/commands/accept_login.rs` (already reads `auth.device_model/platform`)
- Phases 05 (settings router, gate), 06 (web page), 07 (core account exports), 08 (Android screen)

## Overview
Priority P2. Status: **done 2026-09-26** (step 1 was already done 2026-09-23; steps 2-7 built once
05/06/08 landed).

Built as specified. `crates/mediagram-core/src/api/sessions.rs` (`Core::sessions()`,
`Core::revoke_session(id)`) and `web/src/settings/sessions.ts` (`listSessions`, `revokeSession`)
both port `shape`/`revokeError` from the same pure logic, pinned to
`web/test/fixtures/authorizations/cases.json` — read by a Rust unit test
(`api::sessions::tests::shape_matches_the_shared_fixture`, inline beside the source rather than
under `crates/mediagram-core/tests/`, since `shape`/`RawAuthorization` are `pub(crate)` and an
external integration test cannot see them — the same reason `channel/index_tests.rs` reads its
fixture from inside the crate) and a TS one (`settings-sessions-fixture.test.ts`). Both raw TL
guesses (`tl::enums::Authorization::Authorization(a)`, `tl::enums::account::Authorizations::Authorizations(list)`,
`tl::functions::account::{GetAuthorizations,ResetAuthorization}`) compiled and passed on the
first `cargo check` — verified against the real grammers-tl-types 0.10 source (`tl/api.tl`) and
a local `cargo check`, never against a live account. Android: `SessionsSection.kt` composable +
`SettingsViewModel.loadSessions()`/`revokeSession(id)`, bindings regenerated for all four ABIs.
Not verified: revoking a real throwaway web session on the phone (hard constraint — no real
Telegram sign-in/revoke in this work); the password/2FA branch of sign-in is likewise untested
live (05/06's own note).

Step 1 as built: `mediagram-core::connection_params` (`device_model`, `connection_params`, `host_name`) used by the
uploader (`uploader · <hostname>`) and the core (`Android · <manufacturer model>`, passed into `Core::new` as
`device_name` — only Kotlin can read `Build`); web `src/telegram/session-name.ts` (`web · <hostname>`) in both
`Telegram.connect` and `login.ts`. App version = project version everywhere. Verified with `account.getAuthorizations`
on the live account: all three listed under their new names. `LibraryChoice` moved to `dto.rs` (re-exported) to keep
`api/mod.rs` under 200 lines. Settings shows which devices are signed in to the library account through
mediagram, and lets the viewer sign one out remotely. It's for a lost phone or a retired server. Every
mediagram surface holds its own auth key (`login.ts:4-8`), so these sessions pile up quietly today and
are named "Linux 64-bit" / "Android 64-bit", which tells nobody which is which.

## Key insights
- **Naming comes first.** A list is only useful if each row names its device. Each surface sets
  `device_model`/`app_version` at connect. It only takes effect for sessions that connect after the
  change (initConnection runs on every connect, so existing keys are renamed on their next start).
- **Scope to this app.** Filter rows to `api_id == configured api_id`, plus the current row. Revoking the
  user's official Telegram apps from a media player is out of proportion. They keep doing that in Telegram.
- **Telegram guards revocation.** `resetAuthorization` fails with `FRESH_RESET_AUTHORISATION_FORBIDDEN`
  while the *current* session is under 24 h old. Surface it as a sentence and don't retry.
- The current session has `hash = 0` and can't be revoked here. Sign-out (05/07) covers it.

## Requirements
- F: connection params: web `deviceModel: "mediagram web · <hostname>"`, Android `"mediagram Android · <Build.MODEL>"`,
  uploader `"mediagram uploader · <hostname>"`; `appVersion` = project version on all three.
- F: list → `[{id, device, platform, app, appVersion, location, lastActive, created, current, unconfirmed}]`.
  `id` = the authorization `hash` as a decimal string (i64 must not pass through JS numbers).
  `location` = `country` (+ `region`), **never `ip`**.
- F: revoke(id) → `ok` | sentence: fresh-session guard, unknown session (already gone → treat as ok), network.
- F: web routes (admin-gated, own-network, same-origin JSON per 05):
  `GET /api/settings/sessions`, `POST /api/settings/sessions/revoke {id}`.
- F: Android core exports `sessions() -> Vec<SessionSummary>`, `revoke_session(id: String)`.
- NF: no IP, auth key or api hash in any answer or log; the list is not cached (it's the answer to "is it gone?").

## Architecture
```
web  settings/sessions.ts ── invoke(GetAuthorizations / ResetAuthorization) ── filter+shape ── routes (05 gate)
core api/sessions.rs ─────── same two calls via client.invoke ─────────────── uniffi Record ── Kotlin screen (08)
```
Shaping (filter by api_id, pick fields, map errors to sentences) is pure on both sides and tested against one
shared fixture: `crates/mediagram-core/tests/fixtures/authorizations.json`, read by Rust and TS tests
(same pattern as `pick_index`, `tasks/lessons.md` 2026-09-18).

## Related code files
Create: `web/src/settings/sessions.ts`, `web/test/settings-sessions.test.ts`,
`crates/mediagram-core/src/api/sessions.rs` (<150 lines), the shared fixture,
`android/feature/settings/.../SessionsSection.kt` (in 08's module).
Modify: `web/src/telegram/client.ts` + `web/src/settings/sign-in.ts` (connection params),
`crates/mediagram-core/src/api/session.rs` + `crates/mediagram/src/telegram/client.rs` (`ConnectionParams`),
`crates/mediagram-core/src/api/mod.rs` + `dto.rs` (exports, `SessionSummary`), `web/src/settings/routes.ts`,
web settings page (06), regenerated uniffi Kotlin (script only).

## Implementation steps
1. Connection params on all three clients; check `accept_login` output now prints the new name.
2. Shared fixture: one current row, two mediagram rows, one official-app row, one foreign api_id, one `unconfirmed`.
3. Rust `sessions.rs`: pure `shape(raw, api_id)` + `revoke_error(rpc) -> CoreError`, then the two invokes. Tests on the fixture.
4. TS `sessions.ts`: same pure shape/error mapping + teleproto `Api.account.GetAuthorizations/ResetAuthorization`
   (confirm names in typings). Tests on the same fixture: identical rows, same order (last active, newest first).
5. Web routes + page section: list with "This device" badge, a Sign out button per other row, and confirm-in-place
   (no browser `confirm()`). After revoking, list again rather than removing the row locally.
6. Android: section in the Settings screen, same wording as web; bindings + core rebuilt via the scripts.
7. Stub harness for the web UI (never the real player: memory `verify-player-ui-with-a-stub-harness`).
   Device check on the phone: revoke a throwaway web session and see the web player drop to signed-out.

## Todo
- [x] connection params (web, core, uploader)
- [x] shared fixture
- [x] Rust shape + revoke + tests
- [x] TS shape + revoke + tests
- [x] web routes + page section
- [x] Android section + bindings
- [x] stub + device validation (web only; Android device revoke not attempted, see status note)

## Success criteria
- Rust and TS produce identical rows from the fixture; no test output or answer contains the fixture's IP.
- A revoked session's surface fails its next call with `AUTH_KEY_UNREGISTERED` and shows signed-out
  (existing handling; `grammers files.rs:127`).

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| Revoking the device you're using | L×M | current row has no button; hash 0 is refused before any call |
| 24 h fresh-session guard confuses | M×L | explicit sentence: "This device signed in less than a day ago; Telegram allows removing other sessions after 24 hours." |
| Old keys keep generic names until restart | H×L | note it in the section's help text; they rename on next connect |
| i64 hash precision in JS | M×H | string id end to end; teleproto takes `BigInteger` |

## Security
Revoking is a destructive account action → admin gate + same-origin as every settings write. It's scoped to
this app's api_id. IP isn't shown or logged. Nothing new is stored.

## Next steps
09 documents it (parity note: identical on both surfaces).
