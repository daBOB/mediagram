package com.mediagram.android.di

import data.settings.InMemoryLibrarySettings
import data.settings.LibrarySettings
import kotlinx.coroutines.test.runTest
import testing.FakeCore
import testing.FakeCoreProvider
import uniffi.mediagram_core.AppRelease
import uniffi.mediagram_core.CoreException
import update.Lookup
import uniffi.mediagram_core.ReleaseApp
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The media app's updates come from its own library channel and nobody else's. */
class CoreReleaseSourceTest {
    private val release = AppRelease("0.93.0", 93_000L, 3uL, "a".repeat(64), -100L, 7L, null)
    private val core = FakeCore().apply { latestRelease = release; releaseApk = byteArrayOf(1, 2, 3) }

    private suspend fun source(handle: String? = "library", provider: FakeCoreProvider = FakeCoreProvider(core)) =
        CoreReleaseSource(provider, InMemoryLibrarySettings().apply { handle?.let { write(it) } })

    @Test
    fun theNewestMediaAppReleaseIsFoundAndDownloadedByTheCore() =
        runTest {
            val found = assertIs<Lookup.Found>(source().latest()).release!!
            assertEquals("0.93.0" to 93_000L, found.versionName to found.versionCode)
            assertEquals(ReleaseApp.MEDIA_APP, core.releaseApp, "the media app reads only its own releases")
            val apk = File(Files.createTempDirectory("updates").toFile(), "93000.apk")
            found.download(apk)
            assertEquals(listOf(apk.path), core.downloadedPaths)
            assertEquals(listOf<Byte>(1, 2, 3), apk.readBytes().toList())
        }

    @Test
    fun nothingPinnedIsFoundAsNoRelease() =
        runTest {
            core.latestRelease = null
            assertNull(assertIs<Lookup.Found>(source().latest()).release)
        }

    @Test
    fun noLibraryChosenOrNoCoreYetIsNotAskedAboutTheChannel() =
        runTest {
            assertIs<Lookup.Unavailable>(source(handle = null).latest())
            assertIs<Lookup.Unavailable>(source(provider = FakeCoreProvider(null)).latest())
            assertEquals(0, core.releaseChecks)
        }

    @Test
    fun theCoresOwnSentenceIsWhatAFailureSays() {
        val signedOut = CoreException.NotAuthorized("this device was signed out of Telegram")
        runTest { assertEquals("this device was signed out of Telegram", source().sentence(signedOut)) }
        runTest { assertNull(source().sentence(IllegalStateException("no core"))) }
    }

    @Test
    fun aLibraryOrCoreThatCannotBeReadIsUnavailableWithItsReasonSoTheNextForegroundAsksAgain() =
        runTest {
            val brokenCore = FakeCoreProvider(core).apply { beforeCore = { throw CoreException.Io("the keystore refused") } }
            assertEquals(Lookup.Unavailable("the keystore refused"), source(provider = brokenCore).latest())

            val unknown = FakeCoreProvider(core).apply { beforeCore = { error("no core") } }
            assertEquals(Lookup.Unavailable("the update check did not finish"), source(provider = unknown).latest())

            val brokenSettings =
                object : LibrarySettings by InMemoryLibrarySettings() {
                    override suspend fun read(): String? = error("preferences would not open")
                }
            assertEquals(Lookup.Unavailable("the update check did not finish"), CoreReleaseSource(FakeCoreProvider(core), brokenSettings).latest())
            assertEquals(0, core.releaseChecks)
        }

    @Test
    fun aChannelLookupThatFailsStillThrowsSoItKeepsTheHourlyThrottle() =
        runTest {
            core.releaseFailure = CoreException.Network("offline")
            assertFailsWith<CoreException.Network> { source().latest() }
        }
}
