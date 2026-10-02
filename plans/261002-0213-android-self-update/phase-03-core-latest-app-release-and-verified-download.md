# Phase 03 — Core: find and download the newest release

## Context
- Spec § Components › Core. Templates: `crates/mediagram-core/src/api/channel/search.rs` (`newest_index_with`, pinned search + `checked_for`), `api/subtitles_cache.rs` (`fetch_into`: `library::peer_for_chat` → `transport::stream::part_document` → `download_with` → verify → rename), `api/channel/download.rs` (`download_with(core, owner, path, max_bytes, what, oversize, chunks)`).
- Test helpers: `api/channel/responses.rs` `Scripted<T>(vec::IntoIter<Result<T, InvocationError>>)`; `api::account::session::fixture::{Fixture, rpc}` (see `search_tests.rs`).
- Every file under `crates/` stays under 200 lines.

## Task 3.1: `latest_app_release`

**Files:**
- Create: `crates/mediagram-core/src/api/channel/app_release.rs`
- Create: `crates/mediagram-core/src/api/channel/app_release_tests.rs`
- Modify: `crates/mediagram-core/src/api/channel/mod.rs` (add `pub(super) mod app_release;`)
- Modify: `crates/mediagram-core/src/api/mod.rs` (two exports in the `#[uniffi::export]` `impl Core` beside `refresh_library`)

**Interfaces:**
- Consumes: `mlib_spec::app_caption::newest`.
- Produces (uniffi → Kotlin `uniffi.mediagram_core.AppRelease(versionName: String, versionCode: Long, bytes: ULong, sha256: String, chatId: Long, messageId: Long)`):
  - `Core::latest_app_release(handle: String) -> Result<Option<AppRelease>, CoreError>`
  - `Core::download_app_release(release: AppRelease, path: String) -> Result<(), CoreError>` (Task 3.2)

- [ ] **Step 1: Failing tests** — `crates/mediagram-core/src/api/channel/app_release_tests.rs`

```rust
use super::super::responses::Scripted;
use super::*;
use crate::api::account::session::fixture::{Fixture, rpc};

#[tokio::test]
async fn a_channel_with_no_pins_has_no_release() {
    let fixture = Fixture::new().await;
    let found = latest_with(&fixture.core, &fixture.owner, -100, Scripted(vec![].into_iter())).await;
    assert_eq!(found.unwrap(), None);
}

#[tokio::test]
async fn a_refused_pin_read_is_an_error_not_an_empty_answer() {
    let fixture = Fixture::new().await;
    let found = latest_with(&fixture.core, &fixture.owner, -100, Scripted(vec![Err(rpc(500))].into_iter())).await;
    assert!(found.is_err());
}
```
(Which pinned caption wins is `mlib_spec::app_caption::newest`, already tested in Task 2.1; building real grammers `Message`s in a unit test is not done anywhere in this crate.)

- [ ] **Step 2: Run, expect a compile failure**

Run: `cargo test -p mediagram-core app_release`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement** — `crates/mediagram-core/src/api/channel/app_release.rs`

```rust
//! The newest Android app release pinned in the library channel (spec §7a),
//! and fetching its APK verified against its caption.

use std::path::Path;

use grammers_client::message::Message;
use grammers_mtsender::SenderPoolFatHandle;
use grammers_tl_types::enums::MessagesFilter;
use sha2::{Digest, Sha256};

use super::download::download_with;
use super::responses::Responses;
use super::{index, library};
use crate::api::account::revoked::checked_for;
use crate::api::account::session;
use crate::api::{Core, CoreError};
use crate::transport::stream::part_document;

/// The most an app release may weigh. Today's is about 50 MiB; a pin far
/// larger than this is not one `publish-app` sent.
const MAX_APK_BYTES: u64 = 256 * 1024 * 1024;

/// The same window the index lookup reads pins through.
const MAX_PINNED: usize = 100;

/// One release, as the channel declares it, and where its APK is.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AppRelease {
    pub version_name: String,
    pub version_code: i64,
    pub bytes: u64,
    pub sha256: String,
    pub chat_id: i64,
    pub message_id: i64,
}

/// The newest release pinned in the library `handle` names, or `None`.
pub(in crate::api) async fn latest(core: &Core, handle: &str) -> Result<Option<AppRelease>, CoreError> {
    let entry = library::lookup(core, handle)?;
    let peer = entry
        .peer()
        .ok_or_else(|| CoreError::NotFound("this device no longer has that library stored".into()))?;
    let (client, owner) = session::connection(core).await;
    let pinned = client
        .search_messages(peer)
        .filter(MessagesFilter::InputMessagesFilterPinned)
        .limit(MAX_PINNED);
    latest_with(core, &owner, entry.chat, pinned).await
}

/// Only the channel's own posts count, as for the index: a member's message
/// is not a release anyone published.
async fn latest_with(
    core: &Core,
    owner: &SenderPoolFatHandle,
    chat_id: i64,
    mut pinned: impl Responses<Item = Message>,
) -> Result<Option<AppRelease>, CoreError> {
    let mut posts: Vec<(String, i64)> = Vec::new();
    while let Some(message) = checked_for(core, owner, pinned.next_response().await, index::channel_error).await? {
        if message.post() {
            posts.push((message.text().to_string(), i64::from(message.id())));
        }
    }
    let candidates: Vec<(&str, i64)> = posts.iter().map(|(text, id)| (text.as_str(), *id)).collect();
    Ok(mlib_spec::app_caption::newest(&candidates).map(|(position, release)| AppRelease {
        version_name: release.version,
        version_code: release.code,
        bytes: release.bytes,
        sha256: release.sha256,
        chat_id,
        message_id: candidates[position].1,
    }))
}

#[cfg(test)]
#[path = "app_release_tests.rs"]
mod tests;
```
`entry.chat` is the channel's bot-API id `LibraryEntry` already stores (`mod.rs` `entry_of`). Add `pub(super) mod app_release;` to `api/channel/mod.rs` beside `mod search;`.

In `crates/mediagram-core/src/api/mod.rs`, inside the `#[uniffi::export]` `impl Core` block, after `refresh_library`:

```rust
    /// The newest Android app release pinned in the chosen library's
    /// channel, or `None` when it holds none.
    pub async fn latest_app_release(
        &self,
        handle: String,
    ) -> Result<Option<channel::app_release::AppRelease>, CoreError> {
        channel::app_release::latest(self, &handle).await
    }
```

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mediagram-core app_release`
Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/api
git commit -m "feat(core): find the newest app release pinned in the library channel"
```

## Task 3.2: `download_app_release`, verified

**Files:**
- Modify: `crates/mediagram-core/src/api/channel/app_release.rs`
- Modify: `crates/mediagram-core/src/api/channel/app_release_tests.rs`
- Modify: `crates/mediagram-core/src/api/mod.rs`

- [ ] **Step 1: Failing tests** — append to `app_release_tests.rs`

```rust
fn release_of(bytes: &[u8]) -> AppRelease {
    AppRelease {
        version_name: "0.93.0".into(),
        version_code: 93_000,
        bytes: bytes.len() as u64,
        sha256: hex::encode(Sha256::digest(bytes)),
        chat_id: -100,
        message_id: 7,
    }
}

#[tokio::test]
async fn a_matching_download_lands_at_its_path() {
    let fixture = Fixture::new().await;
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("93000.apk");
    let apk = b"a whole apk".to_vec();
    let chunks = Scripted(vec![Ok(apk[..5].to_vec()), Ok(apk[5..].to_vec())].into_iter());
    write_verified(&fixture.core, &fixture.owner, &release_of(&apk), &path, chunks).await.unwrap();
    assert_eq!(std::fs::read(&path).unwrap(), apk);
    assert!(!path.with_extension("part").exists());
}

#[tokio::test]
async fn a_corrupt_or_short_download_leaves_nothing_behind() {
    let apk = b"a whole apk".to_vec();
    for arrived in [b"a whole apX".to_vec(), b"a whole".to_vec()] {
        let fixture = Fixture::new().await;
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("93000.apk");
        let chunks = Scripted(vec![Ok(arrived)].into_iter());
        let result = write_verified(&fixture.core, &fixture.owner, &release_of(&apk), &path, chunks).await;
        assert!(result.is_err());
        assert!(!path.exists() && !path.with_extension("part").exists());
    }
}

#[tokio::test]
async fn a_download_longer_than_its_caption_is_cut_off() {
    let fixture = Fixture::new().await;
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("93000.apk");
    let chunks = Scripted(vec![Ok(b"far more than eleven bytes".to_vec())].into_iter());
    let result = write_verified(&fixture.core, &fixture.owner, &release_of(b"a whole apk"), &path, chunks).await;
    assert!(result.is_err());
    assert!(!path.exists() && !path.with_extension("part").exists());
}

#[tokio::test]
async fn a_release_this_build_will_not_fetch_is_refused_before_any_network() {
    let fixture = Fixture::new().await;
    let mut huge = release_of(b"x");
    huge.bytes = MAX_APK_BYTES + 1;
    assert!(download(&fixture.core, &huge, Path::new("/nonexistent/93000.apk")).await.is_err());
    let mut unshaped = release_of(b"x");
    unshaped.sha256 = "nope".into();
    assert!(download(&fixture.core, &unshaped, Path::new("/nonexistent/93000.apk")).await.is_err());
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cargo test -p mediagram-core app_release`
Expected: FAIL — `write_verified`/`download` not found.

- [ ] **Step 3: Implement** — append to `app_release.rs` (above the `#[cfg(test)]`):

```rust
/// Downloads `release`'s APK to `path`. Nothing appears at `path` unless the
/// whole file arrived and matches the caption's size and sha256: it is
/// written beside it as `.part` first and renamed only once checked.
pub(in crate::api) async fn download(core: &Core, release: &AppRelease, path: &Path) -> Result<(), CoreError> {
    if release.bytes > MAX_APK_BYTES || !mlib_spec::subtitle_bundle::valid_sha256(&release.sha256) {
        return Err(CoreError::Library("that app release is not one this build will download".into()));
    }
    let (client, owner) = session::connection(core).await;
    let handles = library::read(&library::path(core))?;
    let channel = library::peer_for_chat(&handles, release.chat_id).ok_or_else(|| {
        CoreError::NotFound("this device has no way to reach the channel the app release is in".into())
    })?;
    let document = part_document(&client, channel, release.message_id)
        .await
        .map_err(CoreError::network("resolving the app release"))?;
    write_verified(core, &owner, release, path, client.iter_download(&document)).await
}

async fn write_verified(
    core: &Core,
    owner: &SenderPoolFatHandle,
    release: &AppRelease,
    path: &Path,
    chunks: impl Responses<Item = Vec<u8>>,
) -> Result<(), CoreError> {
    let partial = path.with_extension("part");
    let result = download_with(
        core,
        owner,
        &partial,
        release.bytes,
        "downloading the app release",
        || CoreError::Io("the app release is larger than its caption says".into()),
        chunks,
    )
    .await
    .and_then(|()| verify(&partial, release));
    match result {
        Ok(()) => std::fs::rename(&partial, path).map_err(CoreError::io("keeping the downloaded app release")),
        Err(err) => {
            let _ = std::fs::remove_file(&partial);
            Err(err)
        }
    }
}

/// Size and sha256 against the caption, then durable — both before the
/// rename that makes the file look ready to install.
fn verify(path: &Path, release: &AppRelease) -> Result<(), CoreError> {
    const VERIFYING: &str = "verifying the downloaded app release";
    let mut file = std::fs::File::open(path).map_err(CoreError::io(VERIFYING))?;
    let mut hasher = Sha256::new();
    let copied = std::io::copy(&mut file, &mut hasher).map_err(CoreError::io(VERIFYING))?;
    if copied != release.bytes || hex::encode(hasher.finalize()) != release.sha256 {
        return Err(CoreError::Io("the downloaded app release does not match its caption".into()));
    }
    file.sync_all().map_err(CoreError::io(VERIFYING))
}
```
In `api/mod.rs`, after `latest_app_release`:

```rust
    /// Downloads `release`'s APK to `path`, verified against its caption;
    /// nothing is left at `path` unless it matched.
    pub async fn download_app_release(
        &self,
        release: channel::app_release::AppRelease,
        path: String,
    ) -> Result<(), CoreError> {
        channel::app_release::download(self, &release, std::path::Path::new(&path)).await
    }
```
If `app_release.rs` passes 200 lines, move `download`/`write_verified`/`verify` into `app_release_download.rs` (`#[path]` submodule) — the code-standards test enforces it.

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mediagram-core app_release`
Expected: 6 passed.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/api
git commit -m "feat(core): download an app release verified against its caption"
```

## Task 3.3: Kotlin bindings and the fake core

**Files:**
- Modify (generated): `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`
- Modify: `android/core/testing/src/main/kotlin/testing/FakeCore.kt`

- [ ] **Step 1: Regenerate**

```bash
ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh
grep -n 'fun `latestAppRelease`\|fun `downloadAppRelease`\|data class AppRelease' android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt
```
Expected: the two interface methods and the record class are present.

- [ ] **Step 2: Fake core** — in `FakeCore`, add fields beside the other answers and the overrides beside `refreshLibrary`:

```kotlin
    /** What [latestAppRelease] answers, or throws when [releaseFailure] is set. */
    var latestRelease: AppRelease? = null
    var releaseFailure: Throwable? = null
    var releaseChecks = 0

    /** Bytes [downloadAppRelease] writes to the path it is given, or the failure it throws instead. */
    var releaseApk: ByteArray = ByteArray(0)
    var downloadFailure: Throwable? = null
    val downloadedPaths = mutableListOf<String>()

    override suspend fun latestAppRelease(handle: String): AppRelease? {
        releaseChecks++
        releaseFailure?.let { throw it }
        return latestRelease
    }

    override suspend fun downloadAppRelease(release: AppRelease, path: String) {
        downloadedPaths += path
        downloadFailure?.let { throw it }
        java.io.File(path).writeBytes(releaseApk)
    }
```
(import `uniffi.mediagram_core.AppRelease`). Add the same two methods to `FakeCoreHandle`/`CoreContract` only if the compiler asks for them there.

- [ ] **Step 3: Compile every Android module**

Run: `cd android && ./gradlew -q testDebugUnitTest`
Expected: green.

- [ ] **Step 4: Bump (minor) by pattern, check, commit** — same bump block as Task 2.4 Step 6, then:

```bash
ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/build-android-core.sh
scripts/check.sh
git add android/core crates Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts
git commit -m "feat(core): app release lookup and verified download for the Android app; release $V"
```

## Success criteria
`cargo test -p mediagram-core` green; Kotlin sees `latestAppRelease`/`downloadAppRelease`/`AppRelease`; all Android unit tests green.
