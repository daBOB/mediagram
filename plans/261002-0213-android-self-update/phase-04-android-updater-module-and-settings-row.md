# Phase 04 — Android updater and the Updates row

## Context
- Spec § Components › Android updater, Settings → System; Rollout gate: Phase 00's report says whether `REQUEST_INSTALL_PACKAGES` stays and whether Task 4.6 runs.
- Facts: `MainActivity.onStart/onStop` already call `watchSync.onForeground()/onBackground()` (`app/src/main/kotlin/com/mediagram/android/MainActivity.kt`). `@AppScope CoroutineScope` = `SupervisorJob() + Dispatchers.Default` (`core/data/.../di/DataModule.kt:122`). "Playing" = `PlayerHandle.player.value?.isPlaying` (`feature/player/.../PlayerHandle.kt`), read on the main thread. Test doubles: `testing.FakeCore`, `testing.ResolvedCoreProvider(core)`, `settings.InMemoryLibrarySettings`. Library modules get kotlin.test, JUnit, coroutines-test and turbine from the convention plugin.
- System screen: `feature/system/.../SystemBlocks.kt` `thisAppRows(state)`, `SystemUiState`, `SystemViewModel.snapshot()`; rendered by `ui-mobile/.../ui/system/SystemScreen.kt:108` and `ui-tv/.../system/TvSystemSection.kt:84`.

## Task 4.1: `feature:update` module with its pure rules

**Files:**
- Create: `android/feature/update/build.gradle.kts`
- Create: `android/feature/update/src/main/AndroidManifest.xml`
- Create: `android/feature/update/src/main/kotlin/UpdateRules.kt`
- Create: `android/feature/update/src/main/kotlin/UpdateStatus.kt`
- Create: `android/feature/update/src/test/kotlin/UpdateRulesTest.kt`
- Modify: `android/settings.gradle.kts` (add `include(":feature:update")` after `include(":feature:system")`)

**Interfaces:**
- Produces: package `update`: `CHECK_INTERVAL_MS`, `shouldCheck(enabled: Boolean, playing: Boolean, lastCheckAtMs: Long?, nowMs: Long): Boolean`, `isNewer(releaseCode: Long, installedCode: Long): Boolean`, `staleFiles(names: List<String>, wantedCode: Long?): List<String>`, `sealed interface UpdateStatus { Off, NotChecked, UpToDate(checkedAtMs), Downloading(versionName), Ready(versionName), ConfirmWaiting(versionName), Failed(reason) }`, `updateLine(status: UpdateStatus, nowMs: Long): String?`

- [ ] **Step 1: Module files**

`android/feature/update/build.gradle.kts`:
```kotlin
// The app replacing itself with the newest release pinned in the library
// channel. No composables: the System screen renders its status line.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.feature.update"
}

dependencies {
    implementation(project(":core:data"))

    testImplementation(project(":core:testing"))
}
```

`android/feature/update/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Lets a release build replace itself without a prompt on Android 12+
         (PackageInstaller, USER_ACTION_NOT_REQUIRED, self-update). -->
    <uses-permission android:name="android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION" />
    <!-- Keep or drop per the self-update spike's result: needed only if a
         device refused the session without it. -->
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <application>
        <receiver android:name="update.InstallResultReceiver" android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 2: Failing tests** — `android/feature/update/src/test/kotlin/UpdateRulesTest.kt`

```kotlin
package update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateRulesTest {
    private val now = 10 * CHECK_INTERVAL_MS

    @Test
    fun aCheckWaitsForAnIdlePlayerAndAnHourSinceTheLast() {
        assertTrue(shouldCheck(enabled = true, playing = false, lastCheckAtMs = null, nowMs = now))
        assertTrue(shouldCheck(enabled = true, playing = false, lastCheckAtMs = now - CHECK_INTERVAL_MS, nowMs = now))
        assertFalse(shouldCheck(enabled = true, playing = false, lastCheckAtMs = now - CHECK_INTERVAL_MS + 1, nowMs = now))
        assertFalse(shouldCheck(enabled = true, playing = true, lastCheckAtMs = null, nowMs = now), "never while something plays")
        assertFalse(shouldCheck(enabled = false, playing = false, lastCheckAtMs = null, nowMs = now), "debug and benchmark builds never check")
    }

    @Test
    fun onlyAHigherVersionCodeIsNewer() {
        assertTrue(isNewer(releaseCode = 93_000, installedCode = 92_002))
        assertFalse(isNewer(releaseCode = 92_002, installedCode = 92_002))
        assertFalse(isNewer(releaseCode = 92_001, installedCode = 92_002))
    }

    @Test
    fun everyFileButTheWantedApkIsStale() {
        val names = listOf("93000.apk", "92500.apk", "93001.part", "stray.txt")
        assertEquals(listOf("92500.apk", "93001.part", "stray.txt"), staleFiles(names, wantedCode = 93_000))
        assertEquals(names, staleFiles(names, wantedCode = null), "nothing newer to keep: everything goes")
    }

    @Test
    fun theStatusLineSaysWhatIsHappening() {
        assertNull(updateLine(UpdateStatus.Off, now))
        assertEquals("not checked yet", updateLine(UpdateStatus.NotChecked, now))
        assertEquals("up to date · checked just now", updateLine(UpdateStatus.UpToDate(now - 30_000), now))
        assertEquals("up to date · checked 5 min ago", updateLine(UpdateStatus.UpToDate(now - 5 * 60_000), now))
        assertEquals("up to date · checked 3 h ago", updateLine(UpdateStatus.UpToDate(now - 3 * 3_600_000), now))
        assertEquals("0.93.0 · downloading", updateLine(UpdateStatus.Downloading("0.93.0"), now))
        assertEquals("0.93.0 ready · installs when you leave the app", updateLine(UpdateStatus.Ready("0.93.0"), now))
        assertEquals("0.93.0 ready · confirm when asked", updateLine(UpdateStatus.ConfirmWaiting("0.93.0"), now))
        assertEquals("last update failed: no network", updateLine(UpdateStatus.Failed("no network"), now))
    }
}
```

- [ ] **Step 3: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :feature:update:testDebugUnitTest`
Expected: FAIL — unresolved `shouldCheck`, `UpdateStatus`, …

- [ ] **Step 4: Implement**

`android/feature/update/src/main/kotlin/UpdateRules.kt`:
```kotlin
package update

/** How long one look at the channel stands for: a release is not urgent, and every check is a Telegram request. */
const val CHECK_INTERVAL_MS = 60 * 60 * 1000L

/**
 * Whether to look for a release now. Never while something plays — the APK
 * download would compete with the player for Telegram file requests — and
 * never in a build that does not update itself.
 */
fun shouldCheck(
    enabled: Boolean,
    playing: Boolean,
    lastCheckAtMs: Long?,
    nowMs: Long,
): Boolean = enabled && !playing && (lastCheckAtMs == null || nowMs - lastCheckAtMs >= CHECK_INTERVAL_MS)

/** Android installs an update only over a lower versionCode. */
fun isNewer(
    releaseCode: Long,
    installedCode: Long,
): Boolean = releaseCode > installedCode

/**
 * The files in the updates directory to delete: everything except the APK
 * for [wantedCode] — older downloads, partial ones, the copy of the version
 * now installed. With nothing wanted, all of them.
 */
fun staleFiles(
    names: List<String>,
    wantedCode: Long?,
): List<String> = names.filter { it != "$wantedCode.apk" }
```

`android/feature/update/src/main/kotlin/UpdateStatus.kt`:
```kotlin
package update

/** Where this device stands with the newest release — what the System screen's Updates row reads. */
sealed interface UpdateStatus {
    /** A debug or benchmark build: it never updates itself, and the row is hidden. */
    data object Off : UpdateStatus

    data object NotChecked : UpdateStatus

    data class UpToDate(val checkedAtMs: Long) : UpdateStatus

    data class Downloading(val versionName: String) : UpdateStatus

    data class Ready(val versionName: String) : UpdateStatus

    /** Android 10–11: the system's own confirm screen is waiting for the next time the app opens. */
    data class ConfirmWaiting(val versionName: String) : UpdateStatus

    data class Failed(val reason: String) : UpdateStatus
}

/** The Updates row's text, or `null` when the row is not shown. */
fun updateLine(
    status: UpdateStatus,
    nowMs: Long,
): String? =
    when (status) {
        UpdateStatus.Off -> null
        UpdateStatus.NotChecked -> "not checked yet"
        is UpdateStatus.UpToDate -> "up to date · checked ${agoOf(nowMs - status.checkedAtMs)}"
        is UpdateStatus.Downloading -> "${status.versionName} · downloading"
        is UpdateStatus.Ready -> "${status.versionName} ready · installs when you leave the app"
        is UpdateStatus.ConfirmWaiting -> "${status.versionName} ready · confirm when asked"
        is UpdateStatus.Failed -> "last update failed: ${status.reason}"
    }

private fun agoOf(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ago"
    }
}
```
Add `include(":feature:update")` to `android/settings.gradle.kts`.

- [ ] **Step 5: Run, expect PASS**

Run: `cd android && ./gradlew -q :feature:update:testDebugUnitTest`
Expected: 4 passed.

- [ ] **Step 6: Commit**

```bash
git add android/feature/update android/settings.gradle.kts
git commit -m "feat(android): update rules and status line for a self-updating release build"
```

## Task 4.2: Installing an APK over this app

**Files:**
- Create: `android/feature/update/src/main/kotlin/ApkInstaller.kt`
- Create: `android/feature/update/src/main/kotlin/PackageApkInstaller.kt`
- Create: `android/feature/update/src/main/kotlin/InstallResultReceiver.kt`
- Create: `android/feature/update/src/main/kotlin/di/UpdateBindings.kt`
- Create: `android/feature/update/src/test/kotlin/ApkRefusalTest.kt`

**Interfaces:**
- Produces: `interface ApkInstaller { fun refusal(apk: File): String?; fun install(apk: File) }`, `fun apkRefusal(archivePackage: String?, archiveCode: Long, archiveSigners: Set<String>, ownPackage: String, installedCode: Long, ownSigners: Set<String>): String?`, `class InstallResultReceiver` (calls `AppUpdater.onInstallResult(status: Int, message: String?, confirm: Intent?)` — defined in Task 4.3).

- [ ] **Step 1: Failing tests** — `android/feature/update/src/test/kotlin/ApkRefusalTest.kt`

```kotlin
package update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApkRefusalTest {
    private val own = "com.mediagram.android"
    private val key = setOf("5840181d")

    private fun refusal(
        pkg: String? = own,
        code: Long = 93_000,
        signers: Set<String> = key,
    ) = apkRefusal(pkg, code, signers, own, installedCode = 92_002, ownSigners = key)

    @Test
    fun theSameAppNewerAndSameKeyInstalls() = assertNull(refusal())

    @Test
    fun anotherPackageIsRefused() = assertEquals("the download is com.example, not this app", refusal(pkg = "com.example"))

    @Test
    fun anUnreadableFileIsRefused() = assertEquals("the download is not an APK this device can read", refusal(pkg = null))

    @Test
    fun anOlderOrEqualVersionIsRefused() = assertEquals("the download is not newer than this app", refusal(code = 92_002))

    @Test
    fun anotherKeyIsRefused() = assertEquals("the download is signed with another key", refusal(signers = setOf("deadbeef")))

    @Test
    fun noSignersReadableLeavesTheKeyCheckToAndroid() = assertNull(refusal(signers = emptySet()))
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :feature:update:testDebugUnitTest`
Expected: FAIL — unresolved `apkRefusal`.

- [ ] **Step 3: Implement**

`android/feature/update/src/main/kotlin/ApkInstaller.kt`:
```kotlin
package update

import java.io.File

/** Puts a downloaded APK in this app's place. The outcome arrives at [AppUpdater.onInstallResult]. */
interface ApkInstaller {
    /** `null` when [apk] may replace this app, else the reason it may not. */
    fun refusal(apk: File): String?

    fun install(apk: File)
}

/**
 * Whether an APK may replace this app: the same package, a higher
 * versionCode, the same signing key. An archive whose signers this Android
 * version cannot read (below API 28) is left to PackageInstaller, which
 * refuses another key on its own.
 */
fun apkRefusal(
    archivePackage: String?,
    archiveCode: Long,
    archiveSigners: Set<String>,
    ownPackage: String,
    installedCode: Long,
    ownSigners: Set<String>,
): String? =
    when {
        archivePackage == null -> "the download is not an APK this device can read"
        archivePackage != ownPackage -> "the download is $archivePackage, not this app"
        archiveCode <= installedCode -> "the download is not newer than this app"
        archiveSigners.isNotEmpty() && archiveSigners != ownSigners -> "the download is signed with another key"
        else -> null
    }
```

`android/feature/update/src/main/kotlin/PackageApkInstaller.kt`:
```kotlin
package update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

/** [ApkInstaller] over PackageInstaller: one session, no prompt on Android 12+ (a self-update). */
class PackageApkInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ApkInstaller {
        override fun refusal(apk: File): String? {
            val pm = context.packageManager
            val archive = pm.getPackageArchiveInfo(apk.path, SIGNER_FLAGS)
            val installed = pm.getPackageInfo(context.packageName, SIGNER_FLAGS)
            return apkRefusal(
                archivePackage = archive?.packageName,
                archiveCode = archive?.let(::versionCodeOf) ?: 0,
                archiveSigners = archive?.let(::signersOf).orEmpty(),
                ownPackage = context.packageName,
                installedCode = versionCodeOf(installed),
                ownSigners = signersOf(installed),
            )
        }

        override fun install(apk: File) {
            val installer = context.packageManager.packageInstaller
            val params =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                // Mutable on 31+: the system fills in the status extras.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val result = PendingIntent.getBroadcast(context, 0, Intent(context, InstallResultReceiver::class.java), flags)
                session.commit(result.intentSender)
            }
        }

        private companion object {
            @Suppress("DEPRECATION")
            val SIGNER_FLAGS = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

            @Suppress("DEPRECATION")
            fun versionCodeOf(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

            @Suppress("DEPRECATION")
            fun signersOf(info: PackageInfo): Set<String> {
                val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
                return signatures.orEmpty().map { signature ->
                    MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
                }.toSet()
            }
        }
    }
```

`android/feature/update/src/main/kotlin/InstallResultReceiver.kt`:
```kotlin
package update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Where PackageInstaller reports how a self-update ended. */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {
    @Inject
    lateinit var updater: AppUpdater

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        @Suppress("DEPRECATION")
        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        updater.onInstallResult(
            status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
            message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            confirm = confirm,
        )
    }
}
```

`android/feature/update/src/main/kotlin/di/UpdateBindings.kt`:
```kotlin
package update.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import update.ApkInstaller
import update.PackageApkInstaller

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateBindings {
    @Binds
    abstract fun installer(impl: PackageApkInstaller): ApkInstaller
}
```
(`InstallResultReceiver` references `AppUpdater`; write Task 4.3 before compiling, or compile both together at Task 4.3 Step 4.)

- [ ] **Step 4: Commit after Task 4.3 compiles** (one commit for 4.2 + 4.3 — the receiver needs the updater).

## Task 4.3: `AppUpdater`

**Files:**
- Create: `android/feature/update/src/main/kotlin/UpdateConfig.kt`
- Create: `android/feature/update/src/main/kotlin/AppUpdater.kt`
- Create: `android/feature/update/src/test/kotlin/AppUpdaterTest.kt`

**Interfaces:**
- Consumes: `CoreInterface.latestAppRelease(handle: String): AppRelease?`, `CoreInterface.downloadAppRelease(release: AppRelease, path: String)` (Task 3.3); `CoreProvider.coreOrNull()`; `LibrarySettings.read()`; `ApkInstaller` (4.2); rules (4.1).
- Produces: `data class UpdateConfig(enabled: Boolean, installedVersionCode: Long, updatesDir: File)`; `fun interface PlaybackActivity { suspend fun isPlaying(): Boolean }`; `@Singleton class AppUpdater { val status: StateFlow<UpdateStatus>; fun onForeground(); fun onBackground(); fun takeConfirmIntent(): Intent?; fun onInstallResult(status: Int, message: String?, confirm: Intent?); internal suspend fun checkAndDownload(); internal suspend fun installIfReady() }`

- [ ] **Step 1: Failing tests** — `android/feature/update/src/test/kotlin/AppUpdaterTest.kt`

```kotlin
package update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import settings.InMemoryLibrarySettings
import testing.FakeCore
import testing.ResolvedCoreProvider
import uniffi.mediagram_core.AppRelease
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppUpdaterTest {
    private val dir: File = Files.createTempDirectory("updates").toFile()
    private val release = AppRelease("0.93.0", 93_000L, 3uL, "a".repeat(64), -100L, 7L)
    private val core = FakeCore().apply { latestRelease = release; releaseApk = byteArrayOf(1, 2, 3) }
    private var playing = false
    private val installer = RecordingInstaller()

    private suspend fun updater(
        handle: String? = "library",
        enabled: Boolean = true,
    ): AppUpdater {
        val settings = InMemoryLibrarySettings().apply { handle?.let { write(it) } }
        return AppUpdater(
            UpdateConfig(enabled = enabled, installedVersionCode = 92_002, updatesDir = dir),
            ResolvedCoreProvider(core),
            settings,
            { playing },
            installer,
            CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        ).apply { now = { 1_000_000L } }
    }

    @Test
    fun aNewerReleaseIsDownloadedAndInstalledWhenTheAppLeaves() =
        runTest {
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
            assertEquals(listOf(File(dir, "93000.apk").path), core.downloadedPaths)
            updater.installIfReady()
            assertEquals(listOf(File(dir, "93000.apk")), installer.installed)
        }

    @Test
    fun nothingInstallsWhileSomethingPlays() =
        runTest {
            val updater = updater()
            updater.checkAndDownload()
            playing = true
            updater.installIfReady()
            assertTrue(installer.installed.isEmpty())
        }

    @Test
    fun aReleaseNotNewerLeavesTheAppUpToDateAndClearsOldFiles() =
        runTest {
            File(dir, "92500.apk").writeText("old")
            core.latestRelease = release.copy(versionCode = 92_002L)
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.UpToDate(1_000_000L), updater.status.value)
            assertTrue(dir.list().orEmpty().isEmpty())
            assertTrue(core.downloadedPaths.isEmpty())
        }

    @Test
    fun anAlreadyDownloadedReleaseIsNotFetchedAgainAndOlderOnesGo() =
        runTest {
            File(dir, "93000.apk").writeBytes(byteArrayOf(1, 2, 3))
            File(dir, "92500.apk").writeText("old")
            val updater = updater()
            updater.checkAndDownload()
            assertTrue(core.downloadedPaths.isEmpty())
            assertEquals(listOf("93000.apk"), dir.list().orEmpty().toList())
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
        }

    @Test
    fun aFailedDownloadSaysWhyAndInstallsNothing() =
        runTest {
            core.downloadFailure = IllegalStateException("no network")
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Failed("no network"), updater.status.value)
            updater.installIfReady()
            assertTrue(installer.installed.isEmpty())
        }

    @Test
    fun aRefusedApkIsDeletedAndNotInstalled() =
        runTest {
            installer.refusal = "the download is signed with another key"
            val updater = updater()
            updater.checkAndDownload()
            updater.installIfReady()
            assertTrue(installer.installed.isEmpty())
            assertEquals(UpdateStatus.Failed("the download is signed with another key"), updater.status.value)
            assertTrue(!File(dir, "93000.apk").exists())
        }

    @Test
    fun noLibraryChosenOrADisabledBuildNeverAsksTheCore() =
        runTest {
            updater(handle = null).checkAndDownload()
            updater(enabled = false).checkAndDownload()
            assertEquals(0, core.releaseChecks)
        }

    private class RecordingInstaller : ApkInstaller {
        var refusal: String? = null
        val installed = mutableListOf<File>()

        override fun refusal(apk: File): String? = refusal

        override fun install(apk: File) {
            installed += apk
        }
    }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :feature:update:testDebugUnitTest`
Expected: FAIL — unresolved `AppUpdater`, `UpdateConfig`.

- [ ] **Step 3: Implement**

`android/feature/update/src/main/kotlin/UpdateConfig.kt`:
```kotlin
package update

import java.io.File

/**
 * What the app module knows about this build: whether it updates itself
 * (only the `release` build type does), which versionCode it is, and where
 * downloads go.
 */
data class UpdateConfig(
    val enabled: Boolean,
    val installedVersionCode: Long,
    val updatesDir: File,
)

/** Whether the player is playing right now. Reads the player on its own thread. */
fun interface PlaybackActivity {
    suspend fun isPlaying(): Boolean
}
```

`android/feature/update/src/main/kotlin/AppUpdater.kt`:
```kotlin
package update

import android.content.Intent
import android.content.pm.PackageInstaller
import data.CoreProvider
import data.di.AppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import settings.LibrarySettings
import uniffi.mediagram_core.AppRelease
import uniffi.mediagram_core.CoreInterface
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** How often a running download looks at the player. */
private const val PLAYBACK_POLL_MS = 2_000L

/**
 * Keeps a release build current with the newest release pinned in the
 * library channel: looks when the app comes to the foreground (at most
 * hourly, never while something plays), downloads it verified, and installs
 * it when the app goes to the background.
 */
@Singleton
class AppUpdater
    @Inject
    constructor(
        private val config: UpdateConfig,
        private val coreProvider: CoreProvider,
        private val settings: LibrarySettings,
        private val playback: PlaybackActivity,
        private val installer: ApkInstaller,
        @AppScope private val scope: CoroutineScope,
    ) {
        internal var now: () -> Long = System::currentTimeMillis

        private val _status = MutableStateFlow(if (config.enabled) UpdateStatus.NotChecked else UpdateStatus.Off)
        val status: StateFlow<UpdateStatus> = _status.asStateFlow()

        private var lastCheckAtMs: Long? = null
        private var ready: Ready? = null
        private var checking: Job? = null
        private var confirm: Intent? = null

        /** From `MainActivity.onStart`. */
        fun onForeground() {
            if (!config.enabled || checking?.isActive == true) return
            checking = scope.launch { checkAndDownload() }
        }

        /** From `MainActivity.onStop`. */
        fun onBackground() {
            if (config.enabled) scope.launch { installIfReady() }
        }

        /** Android 10–11's confirm screen, once, for the activity to show now that it is in front. */
        fun takeConfirmIntent(): Intent? = confirm.also { confirm = null }

        fun onInstallResult(
            status: Int,
            message: String?,
            confirm: Intent?,
        ) {
            when (status) {
                // The process is being replaced; the next launch is the new version.
                PackageInstaller.STATUS_SUCCESS -> Unit
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    this.confirm = confirm
                    ready?.let { _status.value = UpdateStatus.ConfirmWaiting(it.versionName) }
                }
                else -> _status.value = UpdateStatus.Failed(message ?: "Android refused the update ($status)")
            }
        }

        internal suspend fun checkAndDownload() {
            if (!shouldCheck(config.enabled, playback.isPlaying(), lastCheckAtMs, now())) return
            val handle = settings.read() ?: return
            val core = coreProvider.coreOrNull() ?: return
            lastCheckAtMs = now()
            try {
                val release = core.latestAppRelease(handle)
                if (release == null || !isNewer(release.versionCode, config.installedVersionCode)) {
                    ready = null
                    removeStale(wantedCode = null)
                    _status.value = UpdateStatus.UpToDate(now())
                    return
                }
                removeStale(wantedCode = release.versionCode)
                val apk = File(config.updatesDir, "${release.versionCode}.apk")
                // The core renames a download into place only once it matched its caption.
                if (!apk.exists()) {
                    _status.value = UpdateStatus.Downloading(release.versionName)
                    config.updatesDir.mkdirs()
                    if (!downloadWhileIdle(core, release, apk)) {
                        lastCheckAtMs = null
                        _status.value = UpdateStatus.NotChecked
                        return
                    }
                }
                ready = Ready(apk, release.versionName)
                _status.value = UpdateStatus.Ready(release.versionName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = UpdateStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

        internal suspend fun installIfReady() {
            val apk = ready ?: return
            if (playback.isPlaying()) return
            val refusal = installer.refusal(apk.file)
            if (refusal != null) {
                apk.file.delete()
                ready = null
                _status.value = UpdateStatus.Failed(refusal)
                return
            }
            try {
                installer.install(apk.file)
            } catch (e: Exception) {
                _status.value = UpdateStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

        /** `false` when playback started first: the download is dropped and the next check starts it again. */
        private suspend fun downloadWhileIdle(
            core: CoreInterface,
            release: AppRelease,
            apk: File,
        ): Boolean =
            coroutineScope {
                val download = async { core.downloadAppRelease(release, apk.path) }
                while (download.isActive) {
                    delay(PLAYBACK_POLL_MS)
                    if (download.isActive && playback.isPlaying()) {
                        download.cancel()
                        return@coroutineScope false
                    }
                }
                download.await()
                true
            }

        private fun removeStale(wantedCode: Long?) {
            val names = config.updatesDir.list()?.toList() ?: return
            staleFiles(names, wantedCode).forEach { File(config.updatesDir, it).delete() }
        }

        private data class Ready(
            val file: File,
            val versionName: String,
        )
    }
```
Note: `checkAndDownload` checks `shouldCheck` itself, so the disabled-build test asserts no core call. `downloadWhileIdle` returns as soon as the download completes even inside the 2 s poll: `while (download.isActive)` re-tests after each `delay`; under `runTest` the fake completes at once, so the loop never runs.

- [ ] **Step 4: Run, expect PASS**

Run: `cd android && ./gradlew -q :feature:update:testDebugUnitTest`
Expected: 17 passed (4 rules, 6 refusal, 7 updater).

- [ ] **Step 5: Commit**

```bash
git add android/feature/update
git commit -m "feat(android): the app downloads the newest release and installs it over itself when it goes to the background"
```

## Task 4.4: Wire it into the app

**Files:**
- Create: `android/app/src/main/kotlin/com/mediagram/android/di/UpdateModule.kt`
- Modify: `android/app/build.gradle.kts` (dependencies)
- Modify: `android/app/src/main/kotlin/com/mediagram/android/MainActivity.kt`

- [ ] **Step 1: Dependencies** — in `android/app/build.gradle.kts` `dependencies { … }` add:

```kotlin
    // The self-updater, and the player whose state it waits on.
    implementation(project(":feature:update"))
    implementation(project(":feature:player"))
```

- [ ] **Step 2: Module** — `android/app/src/main/kotlin/com/mediagram/android/di/UpdateModule.kt`

```kotlin
package com.mediagram.android.di

import android.content.Context
import android.os.Build
import com.mediagram.android.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import player.PlayerHandle
import update.PlaybackActivity
import update.UpdateConfig
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    /** `self_update` is true only in the release build type (`app/build.gradle.kts`). */
    @Provides
    @Singleton
    fun config(
        @ApplicationContext context: Context,
    ): UpdateConfig {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)

        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return UpdateConfig(
            enabled = context.resources.getBoolean(R.bool.self_update),
            installedVersionCode = code,
            updatesDir = File(context.cacheDir, "updates"),
        )
    }

    /** ExoPlayer is read on the main thread only. */
    @Provides
    fun playback(handle: PlayerHandle): PlaybackActivity =
        PlaybackActivity { withContext(Dispatchers.Main.immediate) { handle.player.value?.isPlaying == true } }
}
```

- [ ] **Step 3: Activity hooks** — in `MainActivity.kt` add the field beside `watchSync`:

```kotlin
    @Inject
    lateinit var appUpdater: AppUpdater
```
(import `update.AppUpdater`), and change `onStart`/`onStop`:

```kotlin
    override fun onStart() {
        super.onStart()
        watchSync.onForeground()
        appUpdater.onForeground()
        // Android 10–11 only: the system's confirm screen for an update the app committed while in the background.
        appUpdater.takeConfirmIntent()?.let { runCatching { startActivity(it) } }
    }

    override fun onStop() {
        watchSync.onBackground()
        appUpdater.onBackground()
        super.onStop()
    }
```

- [ ] **Step 4: Build all variants**

Run: `cd android && ./gradlew -q :app:assembleDebug :app:assembleRelease :app:assembleBenchmark`
Expected: all three build (release signed, per Phase 01).

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(android): release builds check for an update when they come to the front and install it when they leave"
```

## Task 4.5: The Updates row (phone and TV)

**Files:**
- Modify: `android/feature/system/build.gradle.kts` (`implementation(project(":feature:update"))`)
- Modify: `android/feature/system/src/main/kotlin/SystemUiState.kt` (new field)
- Modify: `android/feature/system/src/main/kotlin/SystemViewModel.kt` (inject `AppUpdater`, fill the field)
- Modify: `android/feature/system/src/main/kotlin/SystemBlocks.kt` (`thisAppRows`)
- Test: `android/feature/system/src/test/kotlin/SystemRowsTest.kt`

- [ ] **Step 1: Failing test** — add to `SystemRowsTest` (it builds states with its own `facts()` helper):

```kotlin
    @Test
    fun theUpdatesRowFollowsVersionOnlyInABuildThatUpdatesItself() {
        assertEquals(listOf("Version", "Telegram", "Uptime"), thisAppRows(facts()).map { it.first })
        val updating = facts().copy(updateLine = "up to date · checked just now")
        assertEquals(listOf("Version", "Updates", "Telegram", "Uptime"), thisAppRows(updating).map { it.first })
        assertEquals("up to date · checked just now", thisAppRows(updating)[1].second)
    }
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :feature:system:testDebugUnitTest`
Expected: FAIL — `updateLine` is not a parameter of `SystemUiState`.

- [ ] **Step 3: Implement**
- `SystemUiState`: add as the last field `/** The Updates row, or `null` in a build that never updates itself. */ val updateLine: String? = null,`
- `SystemBlocks.kt`:

```kotlin
/** This app itself: its version, whether it is current, its Telegram session, and how long it has been running. */
fun thisAppRows(state: SystemUiState): List<Pair<String, String?>> =
    buildList {
        add("Version" to state.versionName)
        state.updateLine?.let { add("Updates" to it) }
        add("Telegram" to telegramLine(state.connected))
        add("Uptime" to uptimeLine(state.uptimeSeconds))
    }
```
- `SystemViewModel`: add `private val updater: AppUpdater,` to the constructor and, where `snapshot()` builds `SystemUiState(…)`, pass `updateLine = updateLine(updater.status.value, System.currentTimeMillis()),` (imports `update.AppUpdater`, `update.updateLine`). Existing `SystemViewModelTest` constructions gain an `AppUpdater` built like `AppUpdaterTest.updater(enabled = false)` — status `Off`, so their expectations are unchanged.

- [ ] **Step 4: Run, expect PASS**

Run: `cd android && ./gradlew -q :feature:system:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest`
Expected: green; both surfaces render the row through `thisAppRows` unchanged.

- [ ] **Step 5: Commit**

```bash
git add android/feature/system
git commit -m "feat(android): Settings › System shows whether this release build is current"
```

## Task 4.6 (only if the spike found `REQUEST_INSTALL_PACKAGES` necessary): offer the permission

**Files:** `UpdateStatus.kt`, `AppUpdater.kt`, `UpdateRulesTest.kt`, `AppUpdaterTest.kt`, and the two renderers (`ui-mobile/.../ui/system/SystemScreen.kt`, `ui-tv/.../system/TvSystemSection.kt`).

- [ ] **Step 1: Test** — in `UpdateRulesTest`: `assertEquals("0.93.0 ready · allow installs from this app", updateLine(UpdateStatus.NeedsPermission("0.93.0"), now))`; in `AppUpdaterTest`: an installer whose `install` throws `SecurityException` leaves `UpdateStatus.NeedsPermission("0.93.0")`.
- [ ] **Step 2: Implement** — add `data class NeedsPermission(val versionName: String) : UpdateStatus` and its `updateLine` branch; in `installIfReady`, `catch (e: SecurityException) { _status.value = UpdateStatus.NeedsPermission(apk.versionName) }` before the general catch.
- [ ] **Step 3: The action** — read both renderers first; where the Updates row is drawn and the status is `NeedsPermission`, make the row (phone: clickable; TV: the focusable block's OK) start
  `Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))`. Expose the status to the renderers through `SystemUiState.updateNeedsPermission: Boolean`.
- [ ] **Step 4: Run, expect PASS**, commit `feat(android): the Updates row offers the install permission where Android asks for it`.

## Task 4.7: Version, docs, full check

- [ ] **Step 1:** Bump (minor) by pattern — the block in Phase 02 Task 2.4 Step 6.
- [ ] **Step 2:** `docs/system-architecture.md` Android module map (`:305-322`): add `feature:update — self-update from the channel's pinned #mlib-app release (release builds only)`. `docs/project-changelog.md`: a new top entry for this version (Added: self-updating release builds, `publish-app`, `release-android.sh`).
- [ ] **Step 3:** `scripts/check.sh` green; review with the `code-reviewer` agent; fix findings.
- [ ] **Step 4:** Commit `feat(android): release builds keep themselves current from the library channel; release $V`.

## Success criteria
All unit tests green; release/benchmark/debug build; the Updates row appears only in the release build; reviewer findings resolved.
