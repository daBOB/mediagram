# Phase 03: `channel_index` implementation

## Context
- Moves in: `commands/pull_index/{backup_path,conflicts,keep_live}.rs` and the
  body of `commands/pull_index/mod.rs`; `telegram/{index_publish,index_guard,
  download_index,unpin}.rs`
- Stays: `index::{merge, merge_candidates, merge_conflicts, pins, snapshot}` (pure, tested)
- Lock pattern: `upload/lock.rs` (flock in the data dir)

## Overview
Priority high. Make phase 02's tests pass. One connection, one download when
the channel moved, none when it did not.

## Layout (each file < 200 lines, per `docs/code-standards.md`)
- `channel_index/mod.rs`: `ChannelIndex`, `Mode { AfterPull, Force }`, doc comment naming the only module that touches Telegram (`telegram_remote`)
- `remote.rs`: the port and its types
- `telegram_remote.rs`: Telegram adapter over `&Tg` (unpin read-back and `message_is_gone` move here unchanged)
- `pull.rs`: current-index choice (own posts + `mlib_spec::index_caption::newest`), download, backup, live sets, merge, conflicts
- `publish.rs`: lock, pull, snapshot, re-check loop (max 3), send, pin, unpin, record
- `lock.rs` or a shared `lockfile` helper: `publish.lock` beside `upload.lock` (extract the flock code once, used by both)
- `pins::record_pulled` / `pins::pulled`: new meta key for the last-pulled channel index id; set by pull and by our own publish

## Steps
1. Extract the flock helper from `upload/lock.rs`; `UploadLock` keeps its interface.
2. Port the pull path onto `ChannelRemote` (tests 1, 8).
3. Publish with re-check loop and lock (tests 2, 3, 6, 7, no-download).
4. Unpin bookkeeping through the port (tests 4, 5).
5. Unit tests for the pulled-id bookkeeping in `index_pin_bookkeeping.rs`.
6. `cargo test -p mediagram`, `cargo clippy -p mediagram`.

## Todo
- [x] flock helper shared
- [x] pull on the port
- [x] publish with re-check + lock
- [x] unpin via port
- [x] pulled-id bookkeeping + tests
- [x] all nine interface tests green

## Success criteria
All tests green; no `Tg::connect` inside `channel_index` (connection is passed in).

## Risks
- SQLite opened while a Telegram session is live can abort the process
  (`index_guard.rs` comment). Mitigation: every open goes through `sqlite_init`, as today.
- Re-check loop hides a flapping channel. Mitigation: cap at 3; the error says another machine kept publishing and that nothing was sent.
