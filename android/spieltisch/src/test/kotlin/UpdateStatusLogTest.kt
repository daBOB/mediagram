package com.mediagram.spieltisch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import update.ApkInstaller
import update.AppUpdater
import update.Lookup
import update.UpdateConfig
import update.UpdateStatus
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The box run reads why an update did or did not happen from logcat: one line per change of status, under the tag `update`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateStatusLogTest {
    private val refusing =
        object : ApkInstaller {
            override fun refusal(apk: File): String = "the download is signed with another key"

            override fun install(apk: File) = Unit
        }

    @Test
    fun eachStatusIsOneLogLine() {
        val dir = java.nio.file.Files.createTempDirectory("updates").toFile()
        File(dir, "176000.apk").writeText("apk")
        val source =
            update.ReleaseSource {
                Lookup.Found(update.Release("0.176.0", 176_000L) { })
            }
        val updater = AppUpdater(UpdateConfig(true, 175_000, dir), source, { false }, refusing, CoroutineScope(Dispatchers.Unconfined))
        updater.logStatus(CoroutineScope(Dispatchers.Unconfined))

        updater.onForeground()
        runBlocking { withTimeout(10_000) { updater.status.first { it is UpdateStatus.Ready } } }
        updater.onBackground()
        runBlocking { withTimeout(10_000) { updater.status.first { it is UpdateStatus.Failed } } }

        val lines = ShadowLog.getLogsForTag("update").map { it.msg }
        assertEquals(
            listOf("not checked yet", "0.176.0 ready · installs when you leave the app", "last update failed: the download is signed with another key"),
            lines,
        )
        assertTrue(lines.all { it.isNotBlank() })
    }
}
