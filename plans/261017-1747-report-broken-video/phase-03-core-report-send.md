# Phase 3 — Rust core `report_send` + Kotlin `ReportChannel`

**Context:** spec § How it is sent. Patterns: `crates/mediagram-core/src/api/together.rs` (`together_send`: `session::connection`, `checked_for`), `crates/mediagram-core/src/api/account/profile.rs` (`get_me`), `android/core/data/src/main/kotlin/TogetherChannel.kt` (`CoreTogetherChannel`, `coreOrNull`), `android/core/data/src/main/kotlin/di/DataModule.kt:161`. Verified 2026-10-17: `client.get_me().await?.to_ref().await` gives a peer that `send_message` accepts, and `InputMessage::schedule_date(Some(now + 60 s))` notified the phone.

**Status:** pending. Independent of phase 2.

### Task 3.1: `report_send`

**Files:** Create `crates/mediagram-core/src/api/report.rs`; Modify `crates/mediagram-core/src/api/mod.rs` (`mod report;`).

**Produces (uniffi):** `Core::report_send(&self, text: String) -> Result<(), CoreError>` → Kotlin `suspend fun reportSend(text: String)`.

- [ ] **Step 1: Implement**

```rust
//! A viewer's report of a broken video, to the account's own Saved Messages.
//!
//! Scheduled rather than sent: every screen is signed in to the same
//! account, and Telegram notifies an account of its own message only when it
//! is a reminder — a scheduled message in Saved Messages. 60 s, as the web's
//! `REPORT_DELAY_SECONDS`.

use std::time::{Duration, SystemTime};

use grammers_client::message::InputMessage;

use super::account::revoked::checked_for;
use super::account::session;
use super::{Core, CoreError};

const DELAY: Duration = Duration::from_secs(60);

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// Schedules `text`, as written, into Saved Messages a minute ahead.
    pub async fn report_send(&self, text: String) -> Result<(), CoreError> {
        let (client, owner) = session::connection(self).await;
        let unsent = || CoreError::network("the report could not be sent");
        let me = checked_for(self, &owner, client.get_me().await, |_| unsent()).await?;
        let peer = me.to_ref().await.ok().flatten().ok_or_else(unsent)?;
        let message = InputMessage::new().text(text).schedule_date(Some(SystemTime::now() + DELAY));
        let sent = client.send_message(peer, message).await.map(|_| ());
        checked_for(self, &owner, sent, |_| unsent()).await
    }
}
```

Adjust to the real `CoreError::network` shape (in `together.rs` it is `CoreError::network("…")(err)` — a constructor returning a closure over the error; follow that exactly). No unit test: `InputMessage`'s fields are crate-private, so the schedule and peer cannot be inspected from here; the behaviour is the one already verified by hand, and the device walk checks it end to end. Say so in the commit body.

- [ ] **Step 2:** `cargo clippy -p mediagram-core --all-targets -- -D warnings` and `cargo test -p mediagram-core` green.
- [ ] **Step 3:** Regenerate bindings `scripts/generate-android-bindings.sh`; rebuild `.so`: `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/build-android-core.sh`.
- [ ] **Step 4: Commit** `feat(core): report_send schedules a report into Saved Messages`.

### Task 3.2: `ReportChannel` (Kotlin)

**Files:** Create `android/core/data/src/main/kotlin/ReportChannel.kt`; Modify `android/core/data/src/main/kotlin/di/DataModule.kt` (provide it, singleton, beside `TogetherChannel`); Test `android/core/data/src/test/kotlin/ReportChannelTest.kt` only if `TogetherLiveTest` shows a cheap fake-core pattern — otherwise none (pure pass-through).

**Produces:**
```kotlin
/** Sends a report's text through the core. Throws while there is no core or sign-in, or Telegram can't be reached. */
interface ReportChannel { suspend fun send(text: String) }
class CoreReportChannel(private val coreProvider: CoreProvider) : ReportChannel {
    override suspend fun send(text: String) {
        val core = coreProvider.coreOrNull() ?: error("no core: not signed in")
        check(core.isAuthorized()) { "not signed in" }
        core.reportSend(text)
    }
}
```
No library handle: the peer is the account, not the channel.

- [ ] **Step 1:** Implement + DI. **Step 2:** `./gradlew :core:data:compileDebugKotlin :core:data:testDebugUnitTest` green. **Step 3: Commit** `feat(android): a ReportChannel over the core`.
