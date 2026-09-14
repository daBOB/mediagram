---
phase: 2
title: "Config and Telegram auth"
status: in-progress
priority: P1
effort: "0.5d"
dependencies: [1]
---

# Phase 2: Config and Telegram auth

## Overview
CLI skeleton with clap, XDG config, grammers client construction, persistent session, `login` command, channel resolution, and a retry wrapper that honors FLOOD_WAIT. Ends with the phase-1 gate: a real single-document upload smoke test.

## Requirements
- Functional: `mediagram login` (phone → code → optional 2FA password); session persisted; `mediagram whoami` prints account + resolved channel title; all later commands reuse the client.
- Non-functional: secrets never logged; config via file + `MEDIAGRAM_*` env overrides; session file mode 0600.

## Architecture
```
crates/mediagram/src/
  main.rs           # clap Cli enum → dispatch to commands::*
  config.rs         # Config { api_id, api_hash, channel, tmdb_key, part_size, throttle_ms, tmp_dir, data_dir }; load from ~/.config/mediagram/config.toml + env
  paths.rs          # XDG dirs via `directories`: config, data (library.db, session)
  telegram/client.rs  # connect(): SqliteSession::open(data_dir/session.sqlite) → SenderPool::new(session, api_id) → Client::new(handle); spawns runner task; returns Client + resolved channel PeerRef + pool handle for quit()
  telegram/retry.rs   # with_retry(op): sleeps FLOOD_WAIT seconds from RpcError, exponential backoff for net errors, max attempts from config
  commands/login.rs
  commands/whoami.rs
```
<!-- Updated: Validation Session 1 - grammers 0.10 API corrections -->
grammers 0.10.0 API (verified against crate source): `grammers_session::storages::SqliteSession::open(path)` persists the session automatically (no save call); `grammers_mtsender::SenderPool::new(Arc<session>, api_id)` returns `{runner, updates, handle}`; `tokio::spawn(runner.run())`; `Client::new(handle)`; `is_authorized`, `request_login_code`, `sign_in`, `check_password` on `SignInError::PasswordRequired`; `iter_dialogs` to find the private channel by id or title; `handle.quit()` on exit. There is no admin-rights query; the first send fails loudly if the account cannot post.

## Related Code Files
- Create: files listed above; `crates/mediagram/Cargo.toml` deps: grammers-client = "0.10", grammers-mtsender = "0.10", grammers-session = "0.10" (sqlite storage), tokio (rt-multi-thread, macros, fs, io-util), clap (derive), serde, toml, directories, dialoguer, anyhow, thiserror, tracing, tracing-subscriber
- Create: `config.example.toml` at repo root

## Implementation Steps
1. Define `Cli` with subcommands `login`, `whoami`, `add`, `resume`, `push-index`, `verify`, `rescan` (later phases fill bodies; unimplemented ones return "not yet implemented").
2. `config.rs`: load TOML, overlay env, validate `part_size % 1MiB == 0` and `<= 4 GiB - 1 MiB`, default 3,758,096,384.
3. `telegram/client.rs`: open SqliteSession, build SenderPool + Client, spawn runner; if not authorized run login flow; resolve channel: accept numeric id (`-100...`) or exact title match over `iter_dialogs`. Admin requirement is documented in README; not checked programmatically.
4. `telegram/retry.rs`: match `InvocationError::Rpc(RpcError { name, value, .. })` where `name == "FLOOD_WAIT"`; grammers already strips the number into `value: Option<u32>`; sleep value+1 s and retry; max 5 attempts with exponential backoff for other transient errors.
5. `commands/login.rs`: dialoguer prompts; save session; print success.
6. Smoke test (manual, documented in README): `mediagram smoke-upload <small.mp4>` hidden subcommand that uploads one file with caption `#mlib smoke` to the channel and prints message id, then deletes it. Use it to observe FLOOD_WAIT behavior and confirm document + caption land correctly.

## Success Criteria
- [ ] `mediagram login` on a fresh machine ends with a saved session; re-run prints "already authorized" (implemented, not yet run live — no credentials in this environment)
- [ ] `mediagram whoami` prints user and channel title (implemented, not yet run live — no credentials in this environment)
- [ ] Smoke upload of a 3.5 GiB file succeeds on the Premium account (confirms 4 GB enforcement assumption); record result in plan notes (not run — requires live account and a 3.5 GiB test file)
- [x] Simulated FLOOD_WAIT (unit test with a fake error) sleeps and retries

## Risk Assessment
- grammers may not expose admin-rights check simply → document the requirement, let send fail loudly.
- FLOOD_WAIT shape verified (`RpcError.name`/`value` in grammers-mtsender 0.10.0 errors.rs:79). Remaining unknown is only how often Telegram issues it for multi-GB uploads; the smoke test measures that.

## Completion notes

Implemented `telegram/client.rs` (`Tg::connect`/`shutdown`, `open_client`, `ensure_login`,
`resolve_channel`), `telegram/retry.rs` (`with_retry`, `flood_wait_secs`), and
`commands/{login,whoami,smoke_upload}.rs` against the verified grammers 0.10.0 API
(`SqliteSession::open` → `SenderPool::new` → `Client::new(handle)`, `iter_dialogs` for
channel resolution, `RpcError{name,value}` FLOOD_WAIT parsing). Session file is created
under `cfg.data_dir()/session.sqlite` with `0600` permissions. Channel resolution accepts
either numeric id form (bare or `-100…`) or an exact title match via `PeerId::bot_api_dialog_id`/`bare_id`.
`with_retry` sleeps `FLOOD_WAIT` seconds (server value + 1s slack) without exhausting the
attempt budget differently from other errors — all error kinds count toward `max_attempts`,
and non-FLOOD_WAIT errors back off exponentially (`200ms * 2^(attempt-1)`).

Verification run in this environment: `cargo fmt --all -- --check` clean, `cargo clippy
--all-targets -- -D warnings` clean, `cargo test` green (8/8, including
`flood_wait_secs` unit tests and two `with_retry` async tests using a fake
`InvocationError` closure — no network). `cargo build --workspace` also green.

Not run: the live smoke test (`mediagram smoke-upload`) and the interactive `login`/`whoami`
flows — no Telegram `api_id`/`api_hash`/channel credentials are available in this sandboxed
worktree. These need to be exercised manually once `config.toml` is populated with real
credentials, per the phase-1 gate.
