# Phase 05 — Core (Rust): together I/O and push

**Spec:** `../spec.md` §4 (Core). **Status:** pending. **Depends on:** phase 03 (`channel-updates/together.json`).

## Context

- **The core only does I/O for watch together.** The session rules live in Kotlin (phase 06), ported from the web and checked against the same fixtures. The spec's §4 says "the core ports it". This plan moves the port to Kotlin, because Android already ports web rules there (`ProfileRolesFixtureTest` reads `web/test/fixtures/watch-state/profile-rules.json`). It also avoids a dozen pure functions crossing UniFFI. This is flagged to the user in the plan's review.
- **Facts from mapping `crates/mediagram-core`:**
  - `LibraryEvent` (`src/updates.rs:48-53`) is a C-style `uniffi::Enum`. Adding a **fieldless** `Together` keeps it a Kotlin `enum class`. A variant with fields would turn it into a sealed class and break every `LibraryEvent.STATE` use.
  - `channel_update` (`src/api/events.rs:40`) drops the message id. `Listener::wait` (`events/listener.rs:91-123`) offers every event to the 5 s `Debouncer`.
  - `next()` (`listener.rs:39`) holds `core.events` while it waits. A heard message must therefore live **outside** that lock, on `Core`.
  - The state document's calls (`api/state_sync/telegram_channel.rs:91-115`): `client.edit_message(peer, id, InputMessage::new().text(..))`, `send_message(peer, InputMessage)`, `delete_messages(peer, &[id])`. Peer and client come from `library::peer_of(core, &handle)?` and `session::connection(core).await` (`state_sync.rs:55-58`). Errors are wrapped with `checked_for(core, &owner, result, fallback)` (`account/revoked.rs:91`).
  - grammers `AutoSleep` sleeps through flood waits of up to 60 s. A longer one comes back as `InvocationError::Rpc` with `code == 420`, `name == "FLOOD_WAIT"` and `value = Some(seconds)`.
  - Don't use `channel_error` on edits: it reports any 400 as "no longer visible", and `MESSAGE_NOT_MODIFIED` is a 400.
  - Message reads: `client.get_messages_by_id(peer, &[id])` returns `Vec<Option<Message>>` (`transport/stream.rs:81`). `Message` has `id()`, `text()` and `post()`.
- **Line limit.** Every non-test `.rs` file must be under 200 lines (`crates/mediagram/tests/code_standards.rs`, run by `cargo test -p mediagram`).
- **The native core is gitignored and must be regenerated with the bindings** (memory: rebuild the native core after Rust changes). Use `ANDROID_NDK_HOME=… scripts/generate-android-bindings.sh`. It rebuilds the `.so`s, rewrites `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`, and that file is committed. `testing.FakeCore` must implement every new method.

## Files

- Modify: `crates/mediagram-core/src/updates.rs`: `ChannelUpdate.id`, `LibraryEvent::Together`, `classify`, and `Debouncer` skipping together.
- Modify: `crates/mediagram-core/src/api/events.rs`: `channel_update` keeps the id.
- Modify: `crates/mediagram-core/src/api/events/listener.rs`: together goes straight to `ready`, and the latest message is kept.
- Modify: `crates/mediagram-core/src/api/mod.rs`: `Core.together_heard`, and `mod together;`.
- Create: `crates/mediagram-core/src/api/together.rs`: the UniFFI exports.
- Create: `crates/mediagram-core/src/api/together_tests.rs`
- Modify: `crates/mediagram-core/tests/shared_channel_update_fixtures.rs`: run `together.json`.
- Modify: `crates/mediagram-core/src/api/events/listener_tests.rs`: delivered at once.
- Regenerate: `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`
- Modify: `android/core/testing/src/main/kotlin/testing/FakeCore.kt`
- Modify: every exhaustive Kotlin `when` over `LibraryEvent`. Find them with `grep -rn "LibraryEvent\." android --include='*.kt'`.

## Interfaces produced (Kotlin names after UniFFI)

```kotlin
enum class LibraryEvent { STATE, INDEX, TOGETHER }
data class TogetherMessage(val messageId: Int, val text: String)
sealed class TogetherEdit { object Ok; object Gone; data class Wait(val seconds: UInt) }
// on CoreInterface:
suspend fun togetherRecent(handle: String): List<TogetherMessage>   // newest first, newest 100 channel messages
suspend fun togetherRead(handle: String, messageId: Int): String?
suspend fun togetherSend(handle: String, text: String): Int
suspend fun togetherEdit(handle: String, messageId: Int, text: String): TogetherEdit
suspend fun togetherDelete(handle: String, messageId: Int)
fun togetherHeard(): TogetherMessage?                                // the newest pushed together message
```

## Tasks

### Task 5.1: Classify together and deliver it at once

- [ ] **Add the failing fixture loop.** In `shared_channel_update_fixtures.rs`, add `fn together_fixtures()`, the same as `classify_fixtures` but reading `"together.json"`. Run `cargo test -p mediagram-core --test shared_channel_update_fixtures`. Expected: FAIL to deserialize `"together"` (unknown variant) and `id` (unknown field, unless the struct ignores unknown fields; if it ignores them, it fails on the variant).
- [ ] **Implement in `updates.rs`:**
  - `const TOGETHER_PREFIX: &str = "#mlib-together ";` next to `STATE_PREFIX`.
  - `ChannelUpdate` gains `#[serde(default)] pub id: Option<i32>,`, documented as "the message id, for new and edit".
  - `LibraryEvent` gains `Together`, documented as "a watch-together session message was sent or edited; delivered at once, never debounced; the message itself is `Core::together_heard`".
  - In `classify`, after the service-message check: `if caption.starts_with(TOGETHER_PREFIX) { return Some(LibraryEvent::Together); }`.
  - `slot_of` returns `Option<usize>`, with `Together => None`. `offer` does `let Some(slot) = slot_of(event) else { return };`. `ORDER` and `due` stay at two.
- [ ] **`events.rs` `channel_update`:** the `Message::Message(m)` arm also yields `Some(m.id)`, and the struct literal sets `id`. The `Pinned` arm sets `id: None`.
- [ ] **Run the fixtures.** Expected: PASS.
- [ ] **Write a failing test** in `listener_tests.rs`, `together_is_delivered_without_waiting_for_a_window`. With the existing harness:
  - `post(CHANNEL, "#mlib-together v=1\n{}", 1)`;
  - `wait` returns `LibraryEvent::Together` **without** `tokio::time::advance`;
  - after `next`, `core.together_heard()` is `Some(TogetherMessage { message_id: 1, text })`.
- [ ] **Implement in `listener.rs`.** Add `heard: Option<TogetherMessage>` to `Listener`. In `wait`, replace the final `if let Some(event)` with:

```rust
            let update = channel_update(&raw.0);
            let event = update.as_ref().and_then(|update| classify(update, channel, own_device));
            match (event, update) {
                // A session's edit is its whole state and the newest wins, so it is never held for a window.
                (Some(LibraryEvent::Together), Some(update)) => {
                    if let (Some(id), Some(text)) = (update.id, update.caption) {
                        self.heard = Some(TogetherMessage { message_id: id, text });
                    }
                    if !self.ready.contains(&LibraryEvent::Together) {
                        self.ready.push_back(LibraryEvent::Together);
                    }
                }
                (Some(event), _) => {
                    let now = self.now_ms();
                    self.debouncer.offer(event, now);
                }
                (None, _) => {}
            }
```

  In `next()`, before `finish_wait`, move the message out under the lock it already holds:

```rust
    if let Some(message) = slot.as_mut().and_then(|listener| listener.heard.take()) {
        *core.together_heard.lock().unwrap_or_else(std::sync::PoisonError::into_inner) = Some(message);
    }
```

  `Core` gains `together_heard: std::sync::Mutex<Option<TogetherMessage>>`, documented as "the newest watch-together message the listener heard, apart from `events` so reading it never waits on a listen". Initialise it in the constructor.
- [ ] **Run:** `cargo test -p mediagram-core`. Expected: PASS.
- [ ] **Commit:** `feat(core): hear watch-together edits at once`

### Task 5.2: The together I/O exports

- [ ] **Write failing tests** in `together_tests.rs`, against the pure mapping `edit_outcome(result: Result<(), InvocationError>) -> Result<TogetherEdit, InvocationError>`:
  - `Ok` → `Ok(TogetherEdit::Ok)`;
  - RPC `MESSAGE_NOT_MODIFIED` (400) → `Ok`;
  - `MESSAGE_ID_INVALID` (400) → `Gone`;
  - `FLOOD_WAIT` (420, value 90) → `Wait { seconds: 90 }`;
  - any other RPC → `Err`.

  Build `InvocationError::Rpc(RpcError { code, name, value, caused_by: None })` as `revoked.rs:150` does in its tests.
- [ ] **Implement `src/api/together.rs`:**

```rust
//! Watch-together session messages: one unpinned text message per session in
//! the library channel, edited in place. Only I/O lives here. The rules of a
//! session are in Kotlin (`android/core/model/.../together/`), ported from
//! the web (`web/public/lib/together/`) and held to its fixtures.

use grammers_client::{InputMessage, InvocationError};

use super::Core;
use super::account::revoked::checked_for;
use super::account::session;
use super::channel::library;
use crate::error::CoreError;

const PREFIX: &str = "#mlib-together ";
/// One history read. Search is never used: it misses fresh messages and reads the marker as the hashtag `#mlib`.
const RECENT: usize = 100;

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TogetherMessage {
    pub message_id: i32,
    pub text: String,
}

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum TogetherEdit {
    Ok,
    /// The message is gone: the session is over.
    Gone,
    /// Telegram asked for a pause longer than grammers sleeps through on its own.
    Wait { seconds: u32 },
}

pub(crate) fn edit_outcome(result: Result<(), InvocationError>) -> Result<TogetherEdit, InvocationError> {
    match result {
        Ok(()) => Ok(TogetherEdit::Ok),
        Err(InvocationError::Rpc(rpc)) if rpc.name == "MESSAGE_NOT_MODIFIED" => Ok(TogetherEdit::Ok),
        Err(InvocationError::Rpc(rpc)) if rpc.name == "MESSAGE_ID_INVALID" => Ok(TogetherEdit::Gone),
        Err(InvocationError::Rpc(rpc)) if rpc.code == 420 => Ok(TogetherEdit::Wait { seconds: rpc.value.unwrap_or(30) }),
        Err(other) => Err(other),
    }
}

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// Session messages among the newest channel messages, newest first.
    pub async fn together_recent(&self, handle: String) -> Result<Vec<TogetherMessage>, CoreError> {
        let (_, peer) = library::peer_of(self, &handle)?;
        let (client, owner) = session::connection(self).await;
        let mut found = Vec::new();
        let mut messages = client.iter_messages(peer).limit(RECENT);
        loop {
            let next = checked_for(self, &owner, messages.next().await, CoreError::network("reading recent messages")).await?;
            let Some(message) = next else { break };
            if message.post() && message.text().starts_with(PREFIX) {
                found.push(TogetherMessage { message_id: message.id(), text: message.text().to_owned() });
            }
        }
        Ok(found)
    }

    pub async fn together_read(&self, handle: String, message_id: i32) -> Result<Option<String>, CoreError> {
        let (_, peer) = library::peer_of(self, &handle)?;
        let (client, owner) = session::connection(self).await;
        let read = client.get_messages_by_id(peer, &[message_id]).await;
        let messages = checked_for(self, &owner, read, CoreError::network("reading a session")).await?;
        Ok(messages.into_iter().next().flatten().map(|message| message.text().to_owned()))
    }

    pub async fn together_send(&self, handle: String, text: String) -> Result<i32, CoreError> {
        let (_, peer) = library::peer_of(self, &handle)?;
        let (client, owner) = session::connection(self).await;
        let sent = client.send_message(peer, InputMessage::new().text(text).silent(true)).await.map(|m| m.id());
        checked_for(self, &owner, sent, CoreError::network("starting a session")).await
    }

    pub async fn together_edit(&self, handle: String, message_id: i32, text: String) -> Result<TogetherEdit, CoreError> {
        let (_, peer) = library::peer_of(self, &handle)?;
        let (client, owner) = session::connection(self).await;
        let edited = edit_outcome(client.edit_message(peer, message_id, InputMessage::new().text(text)).await);
        checked_for(self, &owner, edited, CoreError::network("updating a session")).await
    }

    pub async fn together_delete(&self, handle: String, message_id: i32) -> Result<(), CoreError> {
        let (_, peer) = library::peer_of(self, &handle)?;
        let (client, owner) = session::connection(self).await;
        let deleted = client.delete_messages(peer, &[message_id]).await.map(|_| ());
        checked_for(self, &owner, deleted, CoreError::network("ending a session")).await
    }

    /// The newest watch-together message the listener heard. Read it after a `LibraryEvent::Together`.
    pub fn together_heard(&self) -> Option<TogetherMessage> {
        self.together_heard.lock().unwrap_or_else(std::sync::PoisonError::into_inner).clone()
    }
}

#[cfg(test)]
#[path = "together_tests.rs"]
mod tests;
```

  Match `checked_for`'s real signature (`revoked.rs:91`). It may take the fallback as `|err| CoreError::network("…")(err)`, and may or may not be `async`. Follow `state_sync/publish.rs:25-45`. Check the grammers 0.10 names `iter_messages` and `InputMessage::silent`; if `iter_messages` is named differently, use `client.iter_messages(peer)` from grammers' `MessageIter` docs.
- [ ] **Register the module.** Add `mod together;` to `src/api/mod.rs`. If `lib.rs` re-exports api types for bindgen the way other records are, `pub use` the two types.
- [ ] **Run:** `cargo test -p mediagram-core && cargo test -p mediagram --test code_standards && cargo clippy -p mediagram-core -- -D warnings`. Expected: PASS.
- [ ] **Commit:** `feat(core): watch-together session I/O`

### Task 5.3: Bindings, fake, and the new enum value in Kotlin

- [ ] **Regenerate.** Run `ANDROID_NDK_HOME=<ndk> scripts/generate-android-bindings.sh`, which takes a few minutes. Confirm that `mediagram_core.kt` has `TOGETHER`, `TogetherMessage`, `TogetherEdit` and the six methods.
- [ ] **`FakeCore`.** Add an in-memory channel:
  - `val togetherMessages = linkedMapOf<Int, String>()`;
  - a `var nextTogetherId = 900`;
  - `var togetherHeardMessage: TogetherMessage? = null`;
  - `var togetherEditOutcome: TogetherEdit = TogetherEdit.Ok`.

  Then the methods:
  - `togetherRecent` returns entries newest-first;
  - `togetherRead` and `togetherSend` work against the map;
  - `togetherEdit` returns `Gone` when the id is absent, else stores the text and returns `togetherEditOutcome`;
  - `togetherDelete` removes the entry;
  - `togetherHeard` returns `togetherHeardMessage`.
- [ ] **Exhaustive `when`s.** `grep -rn "LibraryEvent\." android --include='*.kt'`. Wherever a `when (event)` has no `else`, add `LibraryEvent.TOGETHER -> Unit` with a one-line comment ("watch together is handled by the player"). Filters like `== LibraryEvent.STATE` need nothing.
- [ ] **Run:** `cd android && ./gradlew testDebugUnitTest lint`. Expected: PASS. Then `./gradlew :app:assembleDebug`. Expected: PASS, and the `verifyNativeCore` guard accepts the rebuilt `.so`.
- [ ] **Commit:** `feat(android): bindings for watch together`. It holds the regenerated Kotlin, `FakeCore` and the `when` branches. The `.so`s are gitignored.

## Todo

- [ ] 5.1 Classify and immediate delivery
- [ ] 5.2 I/O exports
- [ ] 5.3 Bindings, fake and the enum value

## Success criteria

- `cargo test --all`, clippy and the Android unit tests pass.
- `together.json` passes in both ports.
- A together edit reaches Kotlin without the 5 s window.

## Risks

- **UniFFI's checksum.** A stale `.so` crashes the app at launch, so regenerate the bindings and the `.so`s together, and reinstall on the devices in phase 09.
- **`iter_messages` on a busy channel during an upload.** It reads one page of 100. A session older than 100 messages is found only through the push. This is accepted in the spec.

## Pre-flight rulings (binding; they override the text above where they differ)

- **`crates/mediagram-core/src/api/mod.rs` is already exactly 200 lines** (the `code_standards.rs` limit). Before adding `mod together;` and the `together_heard` field, move one cohesive existing group out of `mod.rs` into a submodule with no behaviour change (your choice of which; say which in the report), so `mod.rs` stays ≤ 200.
- Tasks 5.1–5.3 are one dispatch. Create the `TogetherMessage` record and the `together_heard` getter in 5.1, where they're first used.
- Reuse `crate::updates::TOGETHER_PREFIX` (`pub(crate)`) in `together.rs`, not a second constant. `Debouncer::take` calls `slot_of` too (updates.rs ~134): update it for the `Option` return.
- **FakeCore** gets failure knobs (`togetherFailure`, or one per call) that phase 07's tests use for "Telegram unreachable" and flood/gone outcomes.
- There's no exhaustive Kotlin `when` over `LibraryEvent` today. Add a `TOGETHER` branch only where a `when` would otherwise fail to compile.
