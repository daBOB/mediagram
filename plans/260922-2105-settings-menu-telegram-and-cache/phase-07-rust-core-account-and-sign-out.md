# Phase 07 — Rust core: account summary + sign out (for Android)

## Context links
- `crates/mediagram-core/src/api/mod.rs:127-133` (`Core` fields), `:135-247` (exported methods), `:149` (`is_authorized`)
- `crates/mediagram-core/src/api/session.rs:21-23` (`session.key` = dc id + 256-byte key), `:45-55` (`load_auth_key`), `ClientHandle` drop quits the pool
- `crates/mediagram-core/src/api/auth.rs` (sign-in via grammers)
- `android/core/data/src/main/kotlin/CoreStorage.kt` (Kotlin deletes `session.key` itself: "core's surface has no call that removes it")
- `scripts/generate-android-bindings.sh`, `scripts/build-android-core.sh`
- `docs/code-standards.md` § Security (mask phone to last 2 digits — not needed: phone not shown)

## Overview
Priority P2. Status: **done 2026-09-23** (uncommitted). Built in `api/account/profile.rs` (own `#[uniffi::export]`
block, as `events.rs` does — `api/mod.rs` has no room); `AccountSummary` in `dto.rs`; `session::forget_auth_key` already
existed, so no `delete_auth_key`. `sign_out`: `auth.logOut` capped at 10 s (offline still forgets), then state reset
(drops the pool; a waiting update listener ends itself — its lock is never taken), then key removed; idempotent.
Kotlin wrappers are 08's. Not live-tested: minting a throwaway login needs the account's 2FA password (a QR attempt
left a password-pending session, revoked at once via `account.resetAuthorization`, which that exercised live).
Original text: Two new exports so Android can show the connection and sign
out properly (server-side `auth.logOut`, not just deleting the key file).

## Key insights
- DC id is already on disk (`session.key` first 4 bytes) → `dc_id()` needs no network.
- Account name needs a round trip (`get_me`). Must never fail the screen: return `Option`.
- Today "sign out" on Android = deleting `session.key`; the auth key stays valid at Telegram
  (listed in the account's sessions) until it expires. A real `auth.logOut` fixes that.
- Web gets the same facts from teleproto (05); no shared code needed — the *fields* match.

## Requirements
- F: `pub fn dc_id(&self) -> Option<i32>` (from `load_auth_key`).
- F: `pub async fn account(&self) -> Result<AccountSummary, CoreError>`; `AccountSummary { name: String, username: Option<String> }` (uniffi Record). No phone.
- F: `pub async fn sign_out(&self) -> Result<(), CoreError>`: best-effort `auth.logOut`
  (grammers `Client::sign_out` or raw `tl::functions::auth::LogOut` — [UNVERIFIED] which the pinned grammers exposes),
  then drop the live `ClientHandle`, then delete `session.key`; key deletion happens even if logOut fails (offline).
- NF: `Debug` on nothing that carries secrets; errors use existing `CoreError` sentences.

## Architecture
Kotlin `CoreClient.account()/dcId()/signOut()` → uniffi → `Core` → `session::client` / `session` file ops.
`sign_out` takes the state mutex so no read races a half-torn-down client.

## Related code files
Create: `crates/mediagram-core/src/api/account.rs` (account + sign_out logic, <120 lines).
Modify: `crates/mediagram-core/src/api/mod.rs` (3 exports), `crates/mediagram-core/src/dto.rs` (`AccountSummary`),
`crates/mediagram-core/src/api/session.rs` (`delete_auth_key`), regenerated
`android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt` (via script, never hand-edited).
Delete: none.

## Implementation steps
1. `session::delete_auth_key(data_dir)` — `NotFound` is success.
2. `account.rs`: `account()` via `client.get_me()`; `sign_out()` as above.
3. Exports + dto; unit tests: `dc_id` reads fixture file; `sign_out` with no client still deletes key; deletion idempotent.
4. Run `scripts/generate-android-bindings.sh` and `scripts/build-android-core.sh`; `cargo test -p mediagram-core`; `scripts/check.sh`.

## Todo
- [x] delete_auth_key + test
- [x] account()/dc_id()/sign_out() + tests
- [x] bindings regenerated, core rebuilt

## Success criteria
- `cargo test -p mediagram-core` green; clippy clean; bindings diff contains only the new symbols.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| grammers API name differs | M×L | raw TL `auth.LogOut` fallback |
| logOut succeeds, file delete fails | L×H | return error; Kotlin `CoreStorage.clear` also deletes (belt and braces) |

## Security
Server-side logout is the improvement; no secret crosses FFI in the new calls.

## Next steps
08 consumes these.
