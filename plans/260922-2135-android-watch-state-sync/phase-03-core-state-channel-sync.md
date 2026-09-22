# Phase 03 — Core channel sync

## Context links

- `web/src/state/sync.ts:52-125` (`StateSync.once`, `take`, `give`) — the logic to port
- `web/src/telegram/state-channel.ts:40-128` — caption, pin-list discovery, edit-in-place, pin once
- `crates/mediagram-core/src/api/channel.rs:119-200` — pin-list read + bounded download, already in core
- `crates/mediagram/src/commands/push_index.rs:98-135` — `upload_stream` with a name, send as document, `pin_message`
- `grammers-client-0.10.0/src/client/messages.rs:812-826` — `edit_message` carries `media`
- `crates/mediagram-core/src/api/library.rs:42,125` — handle → `PeerRef`

## Overview

- Priority: P1. Status: done. Blocked by 02.
- `Core::sync_state(handle) -> SyncOutcome`: list pinned state docs in the library's channel, merge (own export included), import, push own doc if changed. Never returns an error.

## Key insights

- Same channel as the library index: the web writes to `telegram.peer`, the channel it reads the index from. Android resolves it from the chosen library handle — the only channel it knows.
- **Pins, not search** — search never finds a fresh message and `#mlib-state` matches `#mlib` hashtags (`state-channel.ts:13-31`). Mirror it.
- The core already filters pins on `#mlib-index` (`channel_index.rs:71`), and reads 100 pins (`channel.rs:32`), so state pins cannot hide the index.
- `client()` clones and releases the core lock (`session.rs:125-131`): a sync round runs beside playback reads without blocking them.
- Account without post/pin rights (a viewer's own Telegram, not the owner's) → `put` fails, pull still works. Degrades to read-only, logged once.

## Requirements
- **Pin failure (web parity, 33d5312).** A first send whose pin is refused must delete the sent document and
  fail the round; rounds must not overlap. Pins are flood-limited (`FLOOD_WAIT_633` measured), and an unpinned
  document is invisible, so without this every round sends another. See `web/src/telegram/state-channel.ts`
  `put` and `web/src/state/sync.ts` `once`; tests `web/test/state-channel-put.test.ts`, `state-sync.test.ts`.

- Functional: `list()` = pinned messages whose caption starts `#mlib-state ` with `device=(\S+)`, media present, downloaded one at a time, capped at 1 MiB each (a state doc is KB; cap stops a hostile pin). `put()` = edit own message's media+caption when its id is known, else upload `watch-state.json`, send as document with caption, pin silently.
- Own doc recognised by device id, its message id learnt, excluded from parsing (own export used instead) — `sync.ts:93-105`.
- Skip `put` when the body with `writtenAt: 0` equals the last sent (in-memory, per process) — `sync.ts:110-121`.
- `SyncOutcome { pulled: u64, pushed: bool, failed: Option<String> }`; every error mapped into `failed`.
- Concurrency: one round at a time per `Core` (`tokio::sync::Mutex<SyncMemo>` holding `mine`, `last_sent`); a second caller waits, never overlaps.

## Architecture

```
state/sync.rs                       trait StateChannel { list, put }, SyncMemo (per-handle), once(state_db, channel, device, memo) -> SyncOutcome
state/channel.rs                    caption only: state_caption(device), device_from_caption(caption) — grammers-free
api/state_sync.rs                   #[uniffi::export] state_device_id(&self), sync_state(self: Arc<Self>, handle) -> SyncOutcome
api/state_sync/telegram_channel.rs  TelegramStateChannel<'a> { core, client, peer } impl StateChannel (grammers)
```

Deviates from the file split first sketched here: the grammers adapter
lives under `api::state_sync` rather than `state::channel`, because it
needs `api::account::revoked::{checked, unless_revoked}` and
`api::channel::index::channel_error` for the revoked-login handling in the
Requirements, and those are `pub(in crate::api)` — unreachable from
`crate::state`. `state::channel` stays grammers-free (caption parsing
only), matching the crate's existing convention that everything touching a
live connection lives under `api::`.

Flow: Kotlin → `sync_state(handle)` → lookup handle → peer → `once`: `list` → parse each (drop nulls) + own export → `merge_states` → `import_merged` → export → compare → `put` → outcome.

## Related code files

- Create: `crates/mediagram-core/src/state/sync.rs`, `state/channel.rs`, `state/sync_tests.rs` (fake channel), `api/state_sync.rs`, `api/state_sync/telegram_channel.rs`.
- Modify: `state/mod.rs` (`pub(crate) mod channel;`, `pub(crate) mod sync;`), `api/mod.rs` (`mod state_sync;`, `sync_memo: AsyncMutex<state::sync::SyncMemo>` field on `Core`), regenerated `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`.
- Delete: none.

## Implementation steps

1. Port `once/take/give` against a `FakeChannel`; tests mirror `web/test/state-sync.test.ts` (pull-then-push, unchanged doc not resent, own doc recognised, channel failure → `failed`, import corrective).
2. Caption helpers `state_caption(device)` / `device_from_caption` with tests copied from `web/test/state-channel-caption.test.ts`.
3. grammers adapter: `search_messages(peer).filter(InputMessagesFilterPinned).limit(100)`; `iter_download` with byte cap; `upload_stream(&mut Cursor, len, "watch-state.json")`; `InputMessage::new().text(caption).document(uploaded)`; `edit_message(peer, id, …)`; `pin_message(peer, id)`. If edit answers MESSAGE_ID_INVALID, forget `mine` and send fresh.
4. Two-machine test: two `StateDb`s, one `FakeChannel`, converge in ≤2 rounds (port of `web/test/state-two-machines.test.ts`).
5. Real-channel smoke is in 09, not here.

## Todo

- [x] sync.rs + fake-channel tests
- [x] caption helpers + tests
- [x] grammers adapter
- [x] two-machine convergence test
- [x] UniFFI export + regenerated binding
- [x] clippy + cargo test

## Success criteria

- `cargo test` green; `sync_state` has no `Result` in its signature.
- A document produced by the Rust export parses with the web's `parseRecord` to the same value (golden from 02).

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| Pin succeeds but edit later drops the document | L×M | Edit always carries media+caption together; test asserts both passed |
| Send ok, pin fails → unpinned orphan, next round sends again | L×M | Superseded by the web's own fix (33d5312): a refused pin deletes the just-sent document and fails the round without keeping `mine`, so the next round sends fresh rather than editing a message nobody can find |
| FLOOD_WAIT on busy start | L×L | One round at a time, sequential downloads, 5-min cadence |
| Wrong channel (library switched in settings plan) | L×M | Handle passed per call; nothing cached across handles except `mine`, which is keyed by handle |

## Security

Writes only to the channel the user chose as their library, which the web already writes to. Caption carries a random device UUID, no hostname, no profile name. Downloaded docs are parsed as hostile (`record.rs`), size-capped. No auth material in the doc.

## Next steps

04 schedules the calls.
