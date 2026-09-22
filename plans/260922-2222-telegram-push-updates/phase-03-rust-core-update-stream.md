# Phase 03 — Rust core: update stream export

## Context links
- `crates/mediagram-core/src/api/session.rs:50` (updates receiver dropped), `api/mod.rs` (exports)
- grammers `Client::stream_updates`, `UpdatesConfiguration { catch_up }`; gap handling per 01's findings

## Overview
Priority P2. Status: **done 2026-09-22** (uncommitted). Keep the updates receiver and expose `next_library_event()` to Kotlin.

## What was built
- `api/session.rs`: `ClientHandle` keeps the pool's updates receiver; `updates_receiver()` hands it out once per connection.
- `api/events.rs`: raw grammers update → phase 02 `ChannelUpdate`, then `classify` → `Debouncer` (5 s) with a
  `timeout_at` for the open window. Waits under its own `Core.events` mutex, **never** `Core.state`, so reads don't stall.
  A `Dropped` stream (connection replaced / signed out) clears the listener; the next call listens on the live connection.
- `Core::next_library_event(handle, own_device) -> LibraryEvent` (`STATE` | `INDEX`), uniffi-exported; bindings regenerated
  (additions only), both ABIs rebuilt, `:app:assembleDebug` and `testDebugUnitTest` green.

## Found while validating (not in the spike)
The spike's grammers listener used the uploader's SQLite session, which knows its own user, so grammers issued
`updates.getState` and Telegram subscribed the connection. Android's `MemorySession` holds only the auth key → no
`getState` → **the stream opened and stayed silent** (measured: no event for a change 11 s later). Fix: `get_me()`
before `stream_updates` caches the self user, and the stream subscribes itself. Re-measured against the live channel with the
real `Core` on an in-memory session: own-device writes → no event; another device's new+edit → exactly one `State`
5.0 s after the send.

## Requirements
- F: `session` keeps the receiver; the first `next_library_event` call starts `stream_updates` (`catch_up: false`, per 01).
- F: `pub async fn next_library_event(&self, handle, own_device) -> Result<LibraryEvent, CoreError>`: waits, classifies (02), debounces,
  returns `StateChanged | IndexChanged`. Cancelling the Kotlin coroutine cancels the wait (uniffi async).
- F: sign-out / client restart ends the stream with a `CoreError` Kotlin treats as "stop listening".
- NF: `UpdatesConfiguration.update_queue_limit` bounded (e.g. 100); drops are harmless because events are only hints.

## Related code files
Modify: `api/session.rs`, `api/mod.rs`, `dto.rs` (`LibraryEvent` enum); regenerate bindings via `scripts/generate-android-bindings.sh`.

## Implementation steps
1. Hold the receiver; build the stream lazily; unit-test the classify→debounce→event path with a fake raw source.
2. Export; rebuild `.so` with `scripts/build-android-core.sh` (NDK at `/home/andre/android-sdk/ndk`).
3. `cargo test -p mediagram-core`, clippy.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| Stream holds the client alive after sign-out | M×M | end on client drop; test |
| Updates starve reads | L×M | stream runs on its own task; reads use the handle as today |
