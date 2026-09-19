# Phase 3: Login and catalog; the mobile surface

**Context:** [plan.md](plan.md) · [phase 1](phase-01-mediagram-core-and-uniffi.md) · [phase 2](phase-02-gradle-skeleton-and-ci.md)

## Overview

- **Priority:** High.
- **Status:** Blocked by phases 1 and 2.
- **Deliverable:** on a phone and a tablet — log in, paste the package URL and key, see the real library with real posters.

## Key insights

- `feature:catalog` holds **ViewModels and UiState only, no composables**. That is what lets phase 4 render the same state on a television without duplicating logic, and what makes this phase's tests plain JVM tests.
- Posters come out of the package as local files: `Core.posterPath(key)` returns a path, so Coil loads from disk and no poster request ever leaves the device.
- Provisioning is deliberately crude this round (spec §1): typed credentials, pasted URL and key. QR is a later round.

## Related code files

- Create: `android/core/model/src/main/kotlin/**`, `android/core/data/src/main/kotlin/**`, `android/feature/catalog/src/main/kotlin/**`, `android/ui-mobile/src/main/kotlin/**`
- Test: `android/feature/catalog/src/test/kotlin/**`, `android/core/data/src/test/kotlin/**`

---

### Task 0: Build-logic hygiene, before seven modules grow dependencies

**Files:** Modify `android/build-logic/convention/**`, `android/app/build.gradle.kts`, `android/settings.gradle.kts`

This task exists because the next four tasks add real dependencies to modules
that currently have none. Both problems below get worse the moment that
happens, and neither is worth fixing twice.

- [ ] **Step 1: Stop the version-catalog workaround from spreading**

Gradle 9.5 auto-wires `gradle/libs.versions.toml` as a catalog named `libs`
unconditionally, so an explicit `versionCatalogs.create("libs") { from(...) }`
fails with *"you can only call the 'from' method a single time"*. But the
type-safe accessors that auto-wiring provides are **not** available inside an
ordinary build script's `dependencies { }` block — only in `plugins { }` and
inside precompiled convention plugins. `:app` therefore reaches the catalog
through an untyped `VersionCatalogsExtension` lookup.

That is correct, and it must not be copied into seven more build files.
`android/build-logic/convention/src/main/kotlin/config/ProjectExtensions.kt`
already exposes `Project.libs: VersionCatalog`, which every convention plugin
uses. Move per-module dependency declarations into the convention plugins so
each module's `build.gradle.kts` stays declarative, and delete the lookup
boilerplate from `:app`.

- [ ] **Step 2: Drop the convention plugins this project does not use**

`FirebaseConventionPlugin`, `SentryConventionPlugin`,
`PlayVitalsReportingConventionPlugin` (and its task),
`AndroidRoomConventionPlugin`, both Jacoco plugins, and the baseline-profile
plugin are registered but applied by nothing. They were copied wholesale from
the skill's assets. Their classpath — Firebase Crashlytics, Google Services,
the Room Gradle plugin — is resolved on every build for no benefit, and this
project stores its catalog in SQLite through Rust, so Room will never apply.

Delete the plugin sources and their `register` blocks. Keep anything a later
round plausibly wants and say why in the commit message.

- [ ] **Step 3: Remove the plan reference from a code comment**

`android/settings.gradle.kts` ends a comment with a pointer to a plan report.
Report files move and are archived; the invariant the comment describes does
not. State the reason, drop the reference.

- [ ] **Step 4: Verify**

Run `./gradlew help`, then `./gradlew :app:assembleDebug`, then
`./gradlew projects`. All three must succeed and still list nine modules.

- [ ] **Step 5: Commit** — `build(android): declare dependencies in convention plugins`

---

### Task 1: Settings storage for the package credentials

**Files:** Create `android/core/data/src/main/kotlin/settings/PackageSettings.kt` · Test `android/core/data/src/test/kotlin/settings/PackageSettingsTest.kt`

**Interfaces — Produces:** `interface PackageSettings { suspend fun read(): PackageCredentials?; suspend fun write(url: String, keyB64: String) }` and `data class PackageCredentials(val url: String, val keyB64: String)`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun credentialsRoundTrip() = runTest {
    val settings = InMemoryPackageSettings()
    settings.write("https://example.com/latest.json", "a".repeat(44))
    assertEquals("https://example.com/latest.json", settings.read()?.url)
}

@Test
fun nothingStoredMeansNoCredentials() = runTest {
    assertNull(InMemoryPackageSettings().read())
}
```

- [ ] **Step 2:** Run `./gradlew :core:data:testDebugUnitTest`. Expected: FAIL, unresolved reference.
- [ ] **Step 3:** Implement the interface, an `InMemoryPackageSettings` for tests, and an `EncryptedPackageSettings` backed by `androidx.security:security-crypto` `EncryptedSharedPreferences` — the key is a secret (`docs/mlib-package-v1.md`: the URL is not, the key is).
- [ ] **Step 4:** Run the test. Expected: PASS, both.
- [ ] **Step 5:** Commit — `feat(android): store the package url and key`.

---

### Task 2: The catalog repository

**Files:** Create `android/core/model/src/main/kotlin/MediaSet.kt`, `android/core/data/src/main/kotlin/CatalogRepository.kt` · Test `android/core/data/src/test/kotlin/CatalogRepositoryTest.kt`

**Interfaces — Consumes:** phase 1's `Core.listSets()`, `Core.refreshCatalog(url, keyB64)`, `Core.posterPath(key)`. **Produces:**

```kotlin
data class MediaSet(
    val setId: String, val kind: Kind, val title: String, val show: String?,
    val season: Int?, val episodeFirst: Int?, val episodeLast: Int?,
    val year: Int?, val durationSecs: Int?, val posterPath: String?, val totalBytes: Long,
)
enum class Kind { MOVIE, EPISODE, TUTORIAL }

interface CatalogRepository {
    suspend fun refresh(): Result<Int>
    suspend fun sets(): List<MediaSet>
}

// The seam that makes every ViewModel testable. The generated `Core` class
// is final, so nothing above this line may depend on it directly.
// Phases 4 and 5 code against this interface.
interface CoreClient {
    fun isAuthorized(): Boolean
    suspend fun requestCode(phone: String): String
    suspend fun signIn(token: String, code: String): AuthOutcome
    suspend fun checkPassword(password: String): Unit
    suspend fun refreshCatalog(url: String, keyB64: String): Long
    fun listSets(): List<SetSummary>
    fun posterPath(posterKey: String): String?
    fun totalSize(setId: String): Long
    suspend fun read(setId: String, offset: Long, len: Int): ByteArray
}
```

`DefaultCoreClient` delegates every call to the generated `Core`; `FakeCore`
implements it for tests. `SetSummary` and `AuthOutcome` come from phase 1's
generated bindings.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun refreshWithoutCredentialsFails() = runTest {
    val repo = DefaultCatalogRepository(FakeCore(), InMemoryPackageSettings())
    assertTrue(repo.refresh().isFailure)
}

@Test
fun setsMapKindFromTheCoreSurface() = runTest {
    val core = FakeCore(sets = listOf(summary(kind = "ep", episodeFirst = 3, episodeLast = 3)))
    val repo = DefaultCatalogRepository(core, settingsWithCredentials())
    assertEquals(Kind.EPISODE, repo.sets().single().kind)
}
```

- [ ] **Step 2:** Run `./gradlew :core:data:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement `CoreClient`, `DefaultCoreClient` and `DefaultCatalogRepository`, mapping the core's `kind` strings `"movie"`, `"ep"`, `"tut"` onto `Kind`. An unrecognised kind is dropped from the catalog rather than crashing it — a newer uploader may write a kind this client predates.
- [ ] **Step 4:** Run the test. Expected: PASS.
- [ ] **Step 5:** Commit — `feat(android): read the catalog through a repository`.

---

### Task 3: The catalog ViewModel

**Files:** Create `android/feature/catalog/src/main/kotlin/CatalogViewModel.kt`, `CatalogUiState.kt` · Test `android/feature/catalog/src/test/kotlin/CatalogViewModelTest.kt`

**Interfaces — Produces:** `CatalogUiState` with `Loading`, `Ready(shelves: List<Shelf>)`, `Empty`, `Failed(message: String)`; `data class Shelf(val title: String, val items: List<MediaSet>)`. Phase 4 renders exactly these.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun shelvesAreGroupedByKind() = runTest {
    val vm = CatalogViewModel(FakeCatalogRepository(movies = 2, episodes = 1, tutorials = 0))
    vm.state.test {
        assertEquals(CatalogUiState.Loading, awaitItem())
        val ready = awaitItem() as CatalogUiState.Ready
        assertEquals(listOf("Movies", "Series"), ready.shelves.map { it.title })
    }
}

@Test
fun aFailedRefreshSurfacesAsFailed() = runTest {
    val vm = CatalogViewModel(FakeCatalogRepository(refreshFails = true))
    vm.state.test {
        awaitItem()
        assertTrue(awaitItem() is CatalogUiState.Failed)
    }
}
```

- [ ] **Step 2:** Run `./gradlew :feature:catalog:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement with `StateFlow` and `viewModelScope`. An empty shelf is omitted, not rendered empty.
- [ ] **Step 4:** Run the test. Expected: PASS, both.
- [ ] **Step 5:** Commit — `feat(android): group the library into shelves`.

---

### Task 4: The login ViewModel

**Files:** Create `android/feature/catalog/src/main/kotlin/login/LoginViewModel.kt` · Test `android/feature/catalog/src/test/kotlin/login/LoginViewModelTest.kt`

**Interfaces — Produces:** `LoginUiState` = `NeedsPhone`, `NeedsCode`, `NeedsPassword`, `Authorized`, `Failed(message)`. Phase 4 renders these too.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun aCodeThatNeedsTwoFactorAsksForThePassword() = runTest {
    val vm = LoginViewModel(FakeCore(signInOutcome = AuthOutcome.PasswordNeeded))
    vm.submitPhone("+49...")
    vm.submitCode("12345")
    assertEquals(LoginUiState.NeedsPassword, vm.state.value)
}

@Test
fun anAlreadyAuthorizedCoreSkipsStraightToAuthorized() = runTest {
    val vm = LoginViewModel(FakeCore(authorized = true))
    assertEquals(LoginUiState.Authorized, vm.state.value)
}
```

- [ ] **Step 2:** Run the test. Expected: FAIL.
- [ ] **Step 3:** Implement against `CoreClient.requestCode / signIn / checkPassword`.
- [ ] **Step 4:** Run the test. Expected: PASS.
- [ ] **Step 5:** Commit — `feat(android): drive the sign-in steps`.

---

### Task 5: The mobile screens

**Files:** Create `android/ui-mobile/src/main/kotlin/{CatalogScreen.kt,LoginScreen.kt,SettingsScreen.kt,MobileApp.kt}`, `android/core/designsystem/src/main/kotlin/{Theme.kt,Spacing.kt}`

- [ ] **Step 1:** Add `androidx-coil-compose` and the Material 3 adaptive artifacts to `:ui-mobile`.
- [ ] **Step 2:** `core:designsystem` holds colour, type scale and spacing tokens, shared with `ui-tv`. Dark by default — a media library is looked at in the dark.
- [ ] **Step 3:** `CatalogScreen(state: CatalogUiState, onOpen: (MediaSet) -> Unit)` renders shelves as horizontally scrolling poster rows, with the column count from `WindowSizeClass` so a tablet shows more per row. Posters load from `posterPath` with Coil; a set with no poster gets a titled placeholder card.
- [ ] **Step 4:** `LoginScreen` and `SettingsScreen` render their states. The key field is masked.
- [ ] **Step 5:** `MobileApp` wires them with Navigation3, starting at login when unauthorized, at settings when authorized with no credentials, at the catalog otherwise. Call it from `MainActivity`'s non-television branch.
- [ ] **Step 6:** Run `./gradlew :app:assembleDebug` and install on a phone. Log in, paste the real URL and key, confirm the real library appears with real posters. Repeat on a tablet or a resized emulator and confirm the row density changes.
- [ ] **Step 7:** Commit — `feat(android): the touch surface for sign-in, settings and the library`.

## Todo list

- [ ] Package credentials stored encrypted
- [ ] Catalog repository mapping the core surface
- [ ] Catalog ViewModel grouping shelves, tested
- [ ] Login ViewModel covering the 2FA branch, tested
- [ ] Mobile screens rendering the real library on phone and tablet

## Success criteria

On a phone and on a tablet: sign in with phone, code and 2FA; paste the
package URL and key; the real library renders with real posters, and shelf
density differs between the two form factors.

## Risk assessment

| Risk | Mitigation |
|---|---|
| The generated `Core` class is final and unmockable | Task 2 introduces `CoreClient`; every ViewModel depends on the interface, never the generated class. |
| Login blocks the main thread | All core calls are `suspend`; they run in `viewModelScope`. Never `runBlocking` outside the playback loader thread. |
| A large library janks on the poster grid | Measure before optimising, per the skill: `android-performance.md` wants evidence, not assumptions. |

## Security considerations

The package key is stored in `EncryptedSharedPreferences` and masked in the
UI. Never log the key, the URL, a phone number or a code. The login code and
2FA password are held only for the duration of the call.

## Next steps

[Phase 4](phase-04-tv-surface.md) and [phase 5](phase-05-playback-media3.md),
which are independent of each other.
