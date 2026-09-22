# Phase 3: Somewhere to make the choice

**Deliverable:** A Storage screen on the overflow menu with two sections —
which volume, and how much of it — writing the setting phase 2 already reads.

## Context

- `android/ui-mobile/src/main/kotlin/AppChrome.kt:30-61` — `Destination` and
  its title/back-label mappings; `:117-180` the menu; `:187` the
  `MenuItem(label, disabledReason)` this phase reuses
- `android/ui-mobile/src/main/kotlin/LibraryPositions.kt:23` — `MenuScreen`,
  the enum that maps a menu choice onto a `Destination`
- `android/ui-mobile/src/main/kotlin/LibraryFlow.kt:80-93` — the
  `menuScreen != null` branch and its `when`
- `android/ui-mobile/src/main/kotlin/TmdbKeyScreen.kt` — the precedent: a
  screen reached from the menu that writes a setting
- `android/ui-mobile/src/main/kotlin/SystemRows.kt` and `SystemScreen.kt` —
  the pure-wording-beside-composable split to mirror
- Phase 1's `CacheVolumes.kt` / `CacheBudget.kt`, phase 2's `CacheSettings`
- Spec §7 (why the change waits for a restart) and §8 (the screen)

## Key insight

The screen is not added to the System screen, whose own doc comment commits
it to being "a snapshot a viewer opens to check on, not a live dashboard".
Pickers that write settings are the opposite of a snapshot, and `TmdbKey`
already established that a screen which changes something is its own
destination.

The wording carries two facts the design owes the viewer and that no amount
of good layout implies: **the change takes effect when the app restarts**,
and **titles already held will be fetched again**. Both live in
`StorageRows.kt` as pure functions, so both are pinned by a test rather than
by whoever last edited a composable.

---

### Task 1: The screen's facts and their wording

**Files:**
- Create: `android/feature/system/src/main/kotlin/StorageUiState.kt`
- Create: `android/feature/system/src/main/kotlin/StorageViewModel.kt`
- Create: `android/ui-mobile/src/main/kotlin/StorageRows.kt`
- Create: `android/ui-mobile/src/test/kotlin/StorageRowsTest.kt`

**Interfaces — Produces:** `system.StorageUiState(volumes, selectedVolumeId, budgets, selectedBudgetName)`,
`system.VolumeOption(id, label, freeBytes, removable)`,
`system.BudgetOption(name, label, offered)`,
`system.StorageViewModel` with `val state: StateFlow<StorageUiState?>`,
`fun chooseVolume(id: String)`, `fun chooseBudget(name: String)`;
`ui.volumeLine`, `ui.budgetUnavailableReason`, `ui.RESTART_NOTE`, `ui.REFETCH_NOTE`.
**Consumes:** `cacheVolumes(context)`, `CacheBudget`, `cacheCapacity`,
`isOffered` (phase 1); `CacheSettings` (phase 2 Task 1).

- [ ] **Step 1: Write the failing test**

Create `android/ui-mobile/src/test/kotlin/StorageRowsTest.kt`:

```kotlin
package ui

import kotlin.test.Test
import kotlin.test.assertEquals

private const val GIB = 1024L * 1024 * 1024

class StorageRowsTest {

    @Test
    fun aVolumeLineNamesTheVolumeAndWhatIsFreeOnIt() {
        assertEquals("30.0 GB free", volumeLine(freeBytes = 30 * GIB))
    }

    @Test
    fun aVolumeWithAlmostNothingFreeStillReadsAsANumber() {
        assertEquals("0.1 GB free", volumeLine(freeBytes = GIB / 10))
    }

    @Test
    fun anUnavailablePresetSaysItDoesNotFit() {
        assertEquals("larger than this volume", budgetUnavailableReason(offered = false))
    }

    @Test
    fun anAvailablePresetHasNothingToExplain() {
        assertEquals(null, budgetUnavailableReason(offered = true))
    }

    @Test
    fun theScreenSaysWhenTheChangeTakesEffect() {
        assertEquals("The cache moves when the app next starts.", RESTART_NOTE)
    }

    @Test
    fun theScreenSaysWhatMovingCosts() {
        assertEquals("Titles already held will be fetched again.", REFETCH_NOTE)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.StorageRowsTest'
```

Expected: FAIL to compile — `volumeLine`, `budgetUnavailableReason`,
`RESTART_NOTE` and `REFETCH_NOTE` are unresolved.

- [ ] **Step 3: Write the wording**

Create `android/ui-mobile/src/main/kotlin/StorageRows.kt`:

```kotlin
package ui

private const val GIB = 1024.0 * 1024 * 1024

/**
 * What a volume has spare, under its own name. Gigabytes to one decimal:
 * a viewer choosing between two disks is comparing magnitudes, and a byte
 * count would make them do the arithmetic.
 */
internal fun volumeLine(freeBytes: Long): String = "%.1f GB free".format(freeBytes / GIB)

/**
 * Why a preset cannot be picked, or null when it can. The same shape
 * `MenuItem` already takes for the library menu's unavailable actions, so
 * the app has one answer to "why can I not press that" rather than two.
 */
internal fun budgetUnavailableReason(offered: Boolean): String? =
    if (offered) null else "larger than this volume"

/**
 * The player holds one cache for the life of the process and a data source
 * open over it, so the move cannot happen under a running player. Said
 * plainly rather than implied, because a setting that appears to have taken
 * effect and has not is worse than one that says when it will.
 */
internal const val RESTART_NOTE = "The cache moves when the app next starts."

/**
 * Bytes are not copied between volumes — minutes of work that can fail
 * halfway, for data whose defining property is that it can be fetched
 * again. A viewer is owed that fact before they choose, not after.
 */
internal const val REFETCH_NOTE = "Titles already held will be fetched again."
```

- [ ] **Step 4: Run the tests and make sure they pass**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.StorageRowsTest'
```

Expected: PASS, 6 tests.

- [ ] **Step 5: Write the state and the view model**

Create `android/feature/system/src/main/kotlin/StorageUiState.kt`:

```kotlin
package system

/** One volume, as the Storage screen offers it. */
data class VolumeOption(
    val id: String,
    val label: String,
    val freeBytes: Long,
    val removable: Boolean,
)

/** One budget preset, and whether this volume is large enough for it. */
data class BudgetOption(val name: String, val label: String, val offered: Boolean)

/**
 * What the Storage screen has to say. Left unformatted on purpose — turning
 * these into the sentences a viewer reads is ui-mobile's `StorageRows`, the
 * same split `SystemUiState` and `SystemRows` already make.
 *
 * [selectedBudgetName] is null when nothing has been chosen, which is not
 * the same as a default having been chosen: the resolved default depends on
 * the volume, and the screen shows no preset as selected until a viewer
 * picks one.
 */
data class StorageUiState(
    val volumes: List<VolumeOption>,
    val selectedVolumeId: String,
    val budgets: List<BudgetOption>,
    val selectedBudgetName: String?,
)
```

Create `android/feature/system/src/main/kotlin/StorageViewModel.kt`:

```kotlin
package system

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import playback.CacheBudget
import playback.INTERNAL_VOLUME_ID
import playback.cacheCapacity
import playback.cacheVolumes
import playback.isOffered
import settings.CacheSettings
import javax.inject.Inject

/**
 * The volumes this device actually has, the presets each of them is large
 * enough for, and whichever of both was chosen last.
 *
 * Writes take effect at the next start (`StorageRows.RESTART_NOTE`), so
 * nothing here touches the running cache — it records a choice and re-reads
 * its own state so the screen reflects it immediately.
 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: CacheSettings,
) : ViewModel() {

    private val _state = MutableStateFlow<StorageUiState?>(null)
    val state: StateFlow<StorageUiState?> = _state.asStateFlow()

    init {
        refresh()
    }

    fun chooseVolume(id: String) = viewModelScope.launch {
        settings.write(volumeId = id, budgetName = settings.read().budgetName)
        refresh()
    }

    fun chooseBudget(name: String) = viewModelScope.launch {
        settings.write(volumeId = settings.read().volumeId, budgetName = name)
        refresh()
    }

    private fun refresh() = viewModelScope.launch {
        val choice = settings.read()
        val volumes = cacheVolumes(context)
        val selectedId = volumes.firstOrNull { it.id == choice.volumeId }?.id
            ?: volumes.firstOrNull()?.id
            ?: INTERNAL_VOLUME_ID
        // Presets are offered against the volume that is selected, not the
        // one the cache is currently on: a viewer picking a card wants to
        // know what will fit on the card.
        val selected = volumes.firstOrNull { it.id == selectedId }
        val capacity = selected?.let { cacheCapacity(it.freeBytes, heldBytes = 0) } ?: 0L
        _state.value = StorageUiState(
            volumes = volumes.map {
                VolumeOption(id = it.id, label = it.label, freeBytes = it.freeBytes, removable = it.removable)
            },
            selectedVolumeId = selectedId,
            budgets = CacheBudget.entries.map {
                BudgetOption(name = it.name, label = it.label, offered = isOffered(it, capacity))
            },
            selectedBudgetName = choice.budgetName,
        )
    }
}
```

`:feature:system` must be able to see `:core:playback`. Check
`android/feature/system/build.gradle.kts`; if it does not already have
`implementation(project(":core:playback"))`, add it — `SystemViewModel`
already imports `playback.CacheProvider`, so it almost certainly does.

- [ ] **Step 6: Compile and commit**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :feature:system:compileDebugKotlin :ui-mobile:testDebugUnitTest
cd /home/andre/Workspace/mediagram-android-cache
wc -l android/feature/system/src/main/kotlin/StorageViewModel.kt   # < 200
git add android/feature/system/src/main/kotlin/StorageUiState.kt \
        android/feature/system/src/main/kotlin/StorageViewModel.kt \
        android/ui-mobile/src/main/kotlin/StorageRows.kt \
        android/ui-mobile/src/test/kotlin/StorageRowsTest.kt \
        android/feature/system/build.gradle.kts
git commit -m "feat(android): offer the volumes a cache could use, and the sizes each allows"
```

---

### Task 2: The screen, and a way to reach it

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/StorageScreen.kt`
- Modify: `android/ui-mobile/src/main/kotlin/AppChrome.kt`
- Modify: `android/ui-mobile/src/main/kotlin/LibraryPositions.kt`
- Modify: `android/ui-mobile/src/main/kotlin/LibraryFlow.kt`
- Modify: `android/ui-mobile/src/test/kotlin/AppChromeTest.kt`

**Interfaces — Produces:** `ui.StorageScreen()`, `Destination.Storage`,
`MenuScreen.Storage`.
**Consumes:** `StorageViewModel`, `StorageUiState`, `volumeLine`,
`budgetUnavailableReason`, `RESTART_NOTE`, `REFETCH_NOTE` (Task 1).

- [ ] **Step 1: Write the failing test for the destination**

Add to `android/ui-mobile/src/test/kotlin/AppChromeTest.kt`:

```kotlin
    @Test
    fun theStorageScreenIsTitledAndHasAWayBack() {
        assertEquals("Storage", barTitleFor(Destination.Storage))
        assertEquals("Back", backLabelFor(Destination.Storage))
    }
```

- [ ] **Step 2: Run it to make sure it fails**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :ui-mobile:testDebugUnitTest --tests '*AppChromeTest'
```

Expected: FAIL to compile — `Destination.Storage` is unresolved.

- [ ] **Step 3: Add the destination**

In `AppChrome.kt`, add to the `Destination` interface and both `when`s:

```kotlin
    data object Storage : Destination
```
```kotlin
    Destination.Storage -> "Storage"     // in barTitleFor
    Destination.Storage -> "Back"        // in backLabelFor
```

In `LibraryPositions.kt`, add to `MenuScreen`:

```kotlin
    Storage(Destination.Storage),
```

In `AppChrome.kt`'s menu, beside the existing items, add an `onStorage`
callback parameter and a `MenuItem(label = "Storage", disabledReason = null) { onStorage() }`.

In `LibraryFlow.kt`, wire the callback and the branch:

```kotlin
        onStorage = { at.menuScreen = MenuScreen.Storage },
```
```kotlin
                MenuScreen.Storage -> StorageScreen()
```

- [ ] **Step 4: Run the test and make sure it passes**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :ui-mobile:testDebugUnitTest --tests '*AppChromeTest'
```

Expected: PASS.

- [ ] **Step 5: Write the screen**

Create `android/ui-mobile/src/main/kotlin/StorageScreen.kt`:

```kotlin
package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import system.StorageUiState
import system.StorageViewModel

/**
 * Where the cache lives and how large it may grow.
 *
 * Its own destination rather than a block on the System screen: that screen
 * is a snapshot a viewer opens to check on, and a picker that writes a
 * setting is the opposite of one. `TmdbKeyScreen` set that precedent.
 */
@Composable
fun StorageScreen() {
    val viewModel: StorageViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        item { WhereBlock(current, viewModel::chooseVolume) }
        item { HowMuchBlock(current, viewModel::chooseBudget) }
        item { Notes() }
    }
}

@Composable
private fun WhereBlock(state: StorageUiState, onChoose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("Where", style = MaterialTheme.typography.titleMedium)
        state.volumes.forEach { volume ->
            Choice(
                selected = volume.id == state.selectedVolumeId,
                label = volume.label,
                detail = volumeLine(volume.freeBytes),
                enabled = true,
                onClick = { onChoose(volume.id) },
            )
        }
    }
}

@Composable
private fun HowMuchBlock(state: StorageUiState, onChoose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("How much", style = MaterialTheme.typography.titleMedium)
        state.budgets.forEach { budget ->
            Choice(
                selected = budget.name == state.selectedBudgetName,
                label = budget.label,
                detail = budgetUnavailableReason(budget.offered),
                enabled = budget.offered,
                onClick = { onChoose(budget.name) },
            )
        }
    }
}

@Composable
private fun Notes() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(RESTART_NOTE, style = MaterialTheme.typography.bodySmall)
        Text(REFETCH_NOTE, style = MaterialTheme.typography.bodySmall)
    }
}

/** One option, and — when it cannot be taken — why, underneath its own label. */
@Composable
private fun Choice(
    selected: Boolean,
    label: String,
    detail: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = onClick)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

- [ ] **Step 6: Run every test in the module**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :ui-mobile:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 7: Prove it on the device**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :app:installDebug
adb shell am start -n com.mediagram.android/.MainActivity
```

Note from prior sessions: `adb shell input tap` is unreliable on Compose
cards — use `adb shell input swipe X Y X Y 150` instead.

Check, in order:

1. Overflow menu shows **Storage**; opening it titles the bar "Storage" and
   offers Back.
2. **Where** lists exactly one volume on this phone — "Internal storage",
   with its free space. It must not list two.
3. **How much** shows all four presets. The phone has ~74 GB free, so 8 GB
   and 32 GB are selectable and **128 GB is not** — it must be greyed out
   with "larger than this volume" underneath. That is the disabled-preset
   rule doing its job against a real number; check the free space shown in
   **Where** agrees with it.
4. Pick 32 GB. Leave the screen and return: 32 GB is still selected.
5. Force-stop and relaunch, then open System. The Cache block's budget must
   now read 32 GB.

```bash
adb shell am force-stop com.mediagram.android
adb shell am start -n com.mediagram.android/.MainActivity
```

- [ ] **Step 8: Full check, then commit**

```bash
cd /home/andre/Workspace/mediagram-android-cache
ANDROID_HOME="${ANDROID_HOME:?set this or the Android half is skipped}" ./scripts/check.sh
git add -A
git commit -m "feat(android): choose where the cache lives and how large it grows"
```

## Success criteria

- Storage is reachable from the overflow menu, titled, and has a way back.
- On the test phone exactly one volume is listed — the emulated primary
  volume is not offered as a second disk.
- A preset larger than the volume is disabled and says why.
- A chosen budget survives a force-stop and is what the System screen reports
  after the restart. This is the end-to-end proof that phases 1, 2 and 3 are
  connected.
- Every new file under 200 lines; `scripts/check.sh` green.

## Risks

**No volume choice is testable on available hardware.** Step 7 can prove the
budget end to end and can prove that the emulated volume is correctly *not*
offered, but the case the feature is named for — a second row, selected, and
honoured after a restart — has no device here. Record that in the plan's
Review rather than implying it was verified.

**A viewer can choose a volume and never restart.** The screen says so, and
nothing enforces it. An "apply now" that tore down the player graph was
considered and rejected in spec §7 as a larger change to the app's object
lifetime than the feature warrants.

## Next steps

Phase 2 of the wider ask — the LAN cache server — is unstarted and needs its
own spec. The shape is already visible: the web player answers HTTP Range
requests over a set's bytes through its own configurable cache, so Android's
side is an `HttpDataSource` in front of `MlibDataSource`, not a new server.
That is also the phase that reaches the Fire Stick and the Chromecast, which
this one does not.
