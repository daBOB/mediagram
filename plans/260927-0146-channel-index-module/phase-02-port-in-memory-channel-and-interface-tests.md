# Phase 02: port, in-memory channel, failing interface tests

## Context
- Existing seam to imitate: `crates/mediagram/src/upload/transport.rs` (`Transport`, static dispatch, `#[allow(async_fn_in_trait)]`)
- Existing fake to imitate: `crates/mediagram/tests/support/upload.rs` (`FakeTransport`)
- Telegram calls the port replaces: `telegram/download_index.rs`, `telegram/unpin.rs`,
  `telegram/index_publish.rs::send_and_pin`, `verify/download_hash.rs::fetch_messages`
  (used by `commands/pull_index/{keep_live,conflicts}.rs`)

## Overview
Priority high. Define the port and write the eight tests against the new
interface before implementing it. The interface is the test surface.

## The port (`channel_index::remote::ChannelRemote`, pub for integration tests)
- `pinned_indexes() -> Vec<PinnedIndex { id, caption, own_post }>`
- `download(id) -> Vec<u8>`
- `messages(ids) -> HashMap<i32, String>` (caption by id; absent = gone)
- `chat_id() -> i64`
- `send_index(path, caption) -> i32`
- `pin(id)`; `unpin(id) -> Unpin { Done, Gone }`; `is_pinned(id) -> bool`

## In-memory channel (`tests/support/channel.rs`)
Holds messages (id, caption, bytes, pinned, own_post). Knobs:
`on_next_list(|channel| …)` to land another machine's publish mid-publish,
`unpin_lies(id)` (reports success, stays pinned), `unpin_fails(id)`.

## Tests (`tests/channel_index.rs`, tempdir local index via `index::db::open`)
1. Another machine's publish is pulled before ours is sent, and nothing is dropped.
2. A publish landing mid-publish is pulled again, not refused.
3. A channel that changes three times in a row fails with a clear message.
4. A failed unpin stays recorded and is retried by the next publish.
5. An unpin that reports success but leaves the message pinned stays recorded.
6. A first publish to an empty channel works.
7. `Force` skips the pull.
8. A member's post, or a snapshot dated in the future, never counts as the channel index.
Plus: a second publish with nothing new in the channel downloads nothing.

## Todo
- [x] port trait + types
- [x] in-memory channel
- [x] nine tests, compiling against stubs, failing

## Success criteria
`cargo test -p mediagram --test channel_index` compiles and fails for the
right reasons (not implemented), not for setup errors.

## Risks
Fake drifting from Telegram. Mitigation: the fake models only what the port
promises; the Telegram adapter's quirks (`MESSAGE_ID_INVALID`, false-`Ok`
unpin) are kept in the adapter with their existing doc comments.
