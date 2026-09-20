# Phase 4: The TMDB key, and fetching posters

**Deliverable:** a viewer can give the app a TMDB key and fetch artwork for
their library, and the cards that have shown initials since the catalog moved
to the channel start showing posters.

## Context

- Spec §6 (the key and the fetch), §1 (the decision this reopens)
- `plans/260919-0034-android-foundation-phone-tablet-tv/phase-08-catalog-from-the-channel.md` — where posters were given up, and on what terms
- `android/core/data/src/main/kotlin/settings/TelegramSettings.kt` — the pattern every stored credential follows
- `crates/mediagram-tmdb/src/posters.rs` (after phase 1) — resolution, download, size limits, key validation

## Key insight

Nothing here draws a poster. `SetSummary.poster_key` → `CoreClient.posterPath()`
→ `MediaSet.posterPath` → `AsyncImage` has been wired since the catalog screen
was built, with an initials fallback for when it answers `None`. This phase
only puts files where `poster_path()` already looks.

Phase 8 gave posters up on specific terms — the package path cost a URL and a
44-character key typed by hand during first-run setup. Those terms are not
being accepted now; a settings screen is a different place from a wizard, and
the device fetches its own art rather than downloading a package.

---

### Task 1: Somewhere to keep the key

**Files:**
- Create: `android/core/data/src/main/kotlin/settings/TmdbSettings.kt`
- Modify: `android/core/data/src/main/kotlin/CoreStorage.kt`, `android/core/data/src/main/kotlin/CoreProvider.kt`, `android/core/data/src/main/kotlin/di/DataModule.kt`, `android/ui-mobile/src/main/kotlin/StartOverAction.kt`
- Test: `android/core/data/src/test/kotlin/TmdbSettingsTest.kt`

**Interfaces — Produces:** `interface TmdbSettings { suspend fun read(): String?; suspend fun write(key: String); suspend fun clear() }`,
with `InMemoryTmdbSettings` and `EncryptedTmdbSettings(context)`. Task 3 reads it.

- [ ] **Step 1: Write the failing test**

```kotlin
package settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The credential a viewer pastes from themoviedb.org. Stored like the api
 * hash, because it is a secret of the same kind: it identifies this
 * installation to a third party and is worth taking off a stolen phone.
 */
class TmdbSettingsTest {

    @Test
    fun nothingIsStoredUntilSomethingIsWritten() = runTest {
        assertNull(InMemoryTmdbSettings().read())
    }

    @Test
    fun aWrittenKeyComesBack() = runTest {
        val settings = InMemoryTmdbSettings()

        settings.write("0123456789abcdef0123456789abcdef")

        assertEquals("0123456789abcdef0123456789abcdef", settings.read())
    }

    /**
     * Blank is what an empty text field submits, and a blank key would fail
     * at TMDB with an error about the key rather than here with the truth,
     * which is that none was given.
     */
    @Test
    fun aBlankKeyIsNoKey() = runTest {
        val settings = InMemoryTmdbSettings()

        settings.write("   ")

        assertNull(settings.read())
    }

    @Test
    fun startingOverForgetsIt() = runTest {
        val settings = InMemoryTmdbSettings()
        settings.write("0123456789abcdef0123456789abcdef")

        settings.clear()

        assertNull(settings.read())
    }
}
```

- [ ] **Step 2: Run the test**

```bash
cd /home/andre/Workspace/mediagram-android/android
./gradlew :core:data:testDebugUnitTest --tests '*TmdbSettingsTest*'
```

Expected: FAIL to compile — `unresolved reference: InMemoryTmdbSettings`.

- [ ] **Step 3: Implement**

`TmdbSettings.kt` follows `TelegramSettings.kt` exactly: an interface, an
in-memory double, an encrypted implementation opening its preferences
`by lazy` with the comment explaining why a keystore failure must not reach a
constructor, and its own file name and key in a `private companion object`:

```kotlin
    private companion object {
        const val PREFS_FILE_NAME = "tmdb_settings"
        const val KEY_TMDB = "tmdb_key"
    }
```

Its own file, not merged into `telegram_settings`, for the reason the existing
stores give: a store added later must not quietly end up on a weaker footing.

Blank-is-absent is shared the way `credentialsOrNull` is — one function both
implementations call, so the in-memory double cannot accept what the real one
would reject.

Provide it from `DataModule` as the other stores are, add `clear()` to
whatever `CoreStorage`/`StoredCoreProvider.forget()` already clears, and add
the key to `StartOverAction`'s dialog text. That dialog names what it
destroys; a credential it silently dropped would make it a lie.

- [ ] **Step 4: Run the test**

```bash
./gradlew :core:data:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/core/data android/ui-mobile/src/main/kotlin/StartOverAction.kt
git commit -m "feat(android): keep a TMDB key the way the other secrets are kept"
```

---

### Task 2: The core fetches the artwork

**Files:**
- Create: `crates/mediagram-core/src/api/artwork.rs`
- Modify: `crates/mediagram-core/Cargo.toml`, `crates/mediagram-core/src/api/mod.rs`, `crates/mediagram-core/src/dto.rs`
- Test: `crates/mediagram-core/tests/artwork_fetch.rs`

**Interfaces — Consumes:** `mediagram_tmdb::posters::{resolve_posters, download_into, already_held}`, `mediagram_tmdb::tmdb_client::TmdbClient` (phase 1).
**Produces:** `Core::fetch_posters(tmdb_key: String, language: String) -> Result<PosterReport, CoreError>`
where `PosterReport { fetched: u32, already_held: u32, no_provider_id: u32, failed: u32 }`.
Task 3 renders it.

The key is passed per call and never stored in the core. Kotlin owns storage,
Rust owns use — so there is exactly one place a key lives, and it is the one
the start-over dialog already promises to clear.

- [ ] **Step 1: Write the failing test**

```rust
//! Fetching artwork, without reaching TMDB. The client is a trait, so these
//! drive a stub: CI has no API key, and a test that needed one would be a
//! test that does not run.

use mediagram_tmdb::tmdb_client::TmdbApi;

/// Answers the details payload for any id, so `resolve_posters` finds a path.
struct StubApi {
    poster_path: Option<String>,
}

impl TmdbApi for StubApi {
    async fn get_json(&self, _path: &str, _query: &[(&str, String)]) -> anyhow::Result<serde_json::Value> {
        Ok(serde_json::json!({ "id": 1, "poster_path": self.poster_path }))
    }
}

#[tokio::test]
async fn a_title_with_no_provider_id_is_counted_rather_than_failed() {
    // A course has no TMDB entry at all, and a library of them must not
    // report a failure for every one.
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("tut", None), ("movie", Some(11225))]);

    let report = fetch_with(dir.path(), StubApi { poster_path: Some("/a.jpg".into()) }).await;

    assert_eq!(report.no_provider_id, 1);
}

#[tokio::test]
async fn artwork_already_on_disk_is_not_fetched_again() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(11225))]);
    write_existing_poster(dir.path(), "tmdb-movie-11225");

    let report = fetch_with(dir.path(), StubApi { poster_path: Some("/a.jpg".into()) }).await;

    assert_eq!(report.already_held, 1);
    assert_eq!(report.fetched, 0);
}

/// Every episode of a series shares one poster key, so a season of eight is
/// one download rather than eight.
#[tokio::test]
async fn a_series_is_one_poster_however_many_episodes_it_has() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("ep", Some(1399)), ("ep", Some(1399)), ("ep", Some(1399))]);

    let report = fetch_with(dir.path(), StubApi { poster_path: Some("/a.jpg".into()) }).await;

    assert_eq!(report.fetched + report.failed, 1);
}

/// A provider that answers without a path is not an error; that title simply
/// has no artwork.
#[tokio::test]
async fn a_title_the_provider_has_no_art_for_is_not_a_failure() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(11225))]);

    let report = fetch_with(dir.path(), StubApi { poster_path: None }).await;

    assert_eq!(report.failed, 0);
    assert_eq!(report.fetched, 0);
}
```

Write `catalog_with_kinds`, `write_existing_poster` and `fetch_with` as
helpers in this file, following the fixture style in
`crates/mediagram-core/tests/api_surface.rs`. `fetch_with` calls the internal
function that takes an `impl TmdbApi`, not the public `fetch_posters`, which
constructs a real client from a key.

- [ ] **Step 2: Run the test**

```bash
cargo test -p mediagram-core --test artwork_fetch
```

Expected: FAIL to compile.

- [ ] **Step 3: Implement**

Add to `crates/mediagram-core/Cargo.toml`:

```toml
mediagram-tmdb = { path = "../mediagram-tmdb" }
```

`api/artwork.rs` splits in two, so the half that matters is testable without a
key:

```rust
/// Everything except constructing the client, so a test can drive a stub.
pub(super) async fn fetch_into(
    api: &impl TmdbApi,
    http: &reqwest::Client,
    posters_dir: &Path,
    titles: &[(Kind, u64)],
    without_id: u32,
) -> PosterReport
```

and the public entry point, which resolves the catalog directory **once**,
before any request:

```rust
    /// Fetches artwork for every title the provider numbers.
    ///
    /// The catalog directory is resolved before the first request and not
    /// looked up again: a refresh landing mid-run swaps `current` underneath
    /// this, and writing into the directory that was current when the run
    /// started wastes the work rather than scattering files across two
    /// installs.
    pub async fn fetch_posters(
        &self,
        tmdb_key: String,
        language: String,
    ) -> Result<crate::dto::PosterReport, CoreError>
```

A rejected key is reported as `CoreError::NotAuthorized`, not `Network` — a
viewer retrying forever against a wrong key is the failure this distinction
prevents. **The key never appears in the error string.**

`already_held` comes from `mediagram_tmdb::posters::already_held` before the
download, so the report can distinguish "was there" from "fetched now".

- [ ] **Step 4: Run the test**

```bash
cargo test -p mediagram-core
```

Expected: PASS, all four, plus everything existing.

- [ ] **Step 5: Regenerate bindings and build the Android side**

```bash
./scripts/generate-android-bindings.sh
cd android && ./gradlew :core:data:testDebugUnitTest
```

Add `suspend fun fetchPosters(tmdbKey: String, language: String): PosterReport`
to `CoreClient` and `DefaultCoreClient`.

- [ ] **Step 6: Commit**

```bash
git add crates/ android/
git commit -m "feat(core): fetch the artwork a channel index does not carry"
```

---

### Task 3: Asking for it, and hearing what happened

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/TmdbKeyScreen.kt`, `android/ui-mobile/src/main/kotlin/PosterFetchReport.kt`
- Modify: `android/feature/system/` (a `PostersViewModel`), `android/ui-mobile/src/main/kotlin/LibraryFlow.kt`
- Test: `android/ui-mobile/src/test/kotlin/PosterFetchReportTest.kt`

**Interfaces — Consumes:** `TmdbSettings` (Task 1), `CoreClient.fetchPosters` (Task 2), `LibraryScaffold` and `MenuActions` (phase 3 task 2).

- [ ] **Step 1: Write the failing test**

```kotlin
package ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a fetch says it did. "Done" over a library of 540 titles tells a
 * viewer nothing about the eight that did not work, and the eight are the
 * only part worth reading.
 */
class PosterFetchReportTest {

    @Test
    fun aRunThatFetchedEverythingSaysSo() {
        assertEquals("Fetched 9 posters.", posterReportLine(fetched = 9, alreadyHeld = 0, noProviderId = 0, failed = 0))
    }

    @Test
    fun artworkAlreadyHeldIsNotReportedAsFetched() {
        assertEquals(
            "Fetched 2 posters. 7 were already held.",
            posterReportLine(fetched = 2, alreadyHeld = 7, noProviderId = 0, failed = 0),
        )
    }

    /** A course has no provider entry; saying so stops it reading as a fault. */
    @Test
    fun titlesWithNoProviderEntryAreExplained() {
        assertEquals(
            "Fetched 2 posters. 171 titles have no provider entry.",
            posterReportLine(fetched = 2, alreadyHeld = 0, noProviderId = 171, failed = 0),
        )
    }

    @Test
    fun failuresAreNamedLast() {
        assertEquals(
            "Fetched 2 posters. 3 could not be fetched.",
            posterReportLine(fetched = 2, alreadyHeld = 0, noProviderId = 0, failed = 3),
        )
    }

    @Test
    fun aRunWithNothingToDoDoesNotPretendOtherwise() {
        assertEquals("No artwork to fetch.", posterReportLine(fetched = 0, alreadyHeld = 0, noProviderId = 0, failed = 0))
    }
}
```

- [ ] **Step 2: Run the test**

```bash
./gradlew :ui-mobile:testDebugUnitTest --tests '*PosterFetchReportTest*'
```

Expected: FAIL to compile.

- [ ] **Step 3: Implement**

`PosterFetchReport.kt` holds `posterReportLine` — pure, `internal`, no
composable. Singular and plural are handled; "1 posters" is the kind of thing
that makes a careful app look careless.

`TmdbKeyScreen.kt` is one `OutlinedTextField` and a save button, in the shape
`TelegramApplicationScreen` already uses. It says where the key comes from
(themoviedb.org) and that it is optional. It shows whether one is currently
stored, **never the key itself** — a stored secret is confirmed, not
displayed.

`PostersViewModel` runs the fetch off the main thread, exposes progress as a
state, and hands back the report line. Fetch posters is disabled in the menu
while a run is in flight, and disabled with a reason when no key is stored —
an action that silently does nothing is worse than one that says why it cannot.

Add the `TmdbKey` destination to `LibraryFlow.kt`'s `when` with its own
`BackHandler` and `rememberSaveable` flag, exactly as System was added.

- [ ] **Step 4: Run the test**

```bash
./gradlew :ui-mobile:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 5: Prove it on the phone**

```bash
./gradlew :app:installDebug
adb shell monkey -p com.mediagram.android -c android.intent.category.LAUNCHER 1
```

1. ⋮ → Fetch posters with no key stored: the item is disabled and says why.
2. ⋮ → TMDB key, paste a real v3 key, save. The screen says a key is stored and does not show it.
3. ⋮ → Fetch posters. It reports counts that add up to the library's size.
4. The catalog now shows artwork where it showed initials.
5. Run it a second time: everything reports as already held, and nothing is re-downloaded.
6. Store a deliberately wrong key and fetch: the failure names the key, not the network.
7. `adb shell run-as com.mediagram.android ls files/catalog/current/posters | wc -l` matches what the report said.

**Do not paste a real key into the report, the commit, or the ledger.**

- [ ] **Step 6: Commit**

```bash
git add android/
git commit -m "feat(android): fetch the artwork, and say what came back"
```

## Todo list

- [ ] `TmdbSettings` in its own encrypted file, blank treated as absent
- [ ] Start over clears it, and its dialog says so
- [ ] `fetch_posters` reports fetched, already held, no provider id, failed
- [ ] The catalog directory is resolved once, before the first request
- [ ] A rejected key is `NotAuthorized`, and the key is never in the message
- [ ] A series is one poster however many episodes it has
- [ ] The seven device confirmations made

## Success criteria

A viewer stores a key, fetches artwork, and sees posters where initials were.
A second run downloads nothing. `./scripts/check.sh` passes.

## Risk assessment

| Risk | Mitigation |
|---|---|
| The key reaches a log or an error string | It is passed per call, never stored in Rust, and `NotAuthorized` names the key as rejected without quoting it. The device step says not to paste it anywhere either. |
| A refresh swaps `current` mid-fetch | The directory is resolved once before the first request; worst case is wasted work, not files split across two installs. |
| A library of 540 sets makes 540 TMDB requests on a phone connection | Series share one key, so the count is titles-with-a-provider-id, not sets. The fetch is explicit, so it is never a surprise. The uploader's disk cache is not present on device, so this is genuinely one request per distinct title. |
| A blank key field submits an empty string | Blank-is-absent is shared by both implementations and tested. |
| TMDB rate-limits a large library | The shared client already honours `Retry-After` on a 429, tested in the crate it moved from. |

## Next steps

Phase 5 renders the artwork this fetches, beside the text phase 2 exposed.
