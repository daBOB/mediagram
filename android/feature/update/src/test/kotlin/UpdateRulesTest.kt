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
