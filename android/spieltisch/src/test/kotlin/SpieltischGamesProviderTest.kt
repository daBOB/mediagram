package com.mediagram.spieltisch

import android.net.Uri
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import uniffi.spieltisch_core.ShelfGame
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** What the TV home screen reads from Spieltisch: the playable games in lobby order, and their posters. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpieltischGamesProviderTest {
    private val app = RuntimeEnvironment.getApplication()

    @Before
    fun theHomeScreenHoldsThePermission() = shadowOf(app).grantPermissions(SpieltischGamesProvider.PERMISSION)

    @Test
    fun gamesAreThePlayableShelfInOrder() {
        provider().query(uri("games"), null, null, null, null)!!.use {
            assertEquals(listOf("kind", "name"), it.columnNames.toList())
            val rows = generateSequence { if (it.moveToNext()) it.getString(0) to it.getString(1) else null }.toList()
            assertEquals(listOf("quiz" to "Quiz", "tien_len" to "Tiến Lên"), rows)
        }
    }

    @Test
    fun aGameIsPosteredByItsDashedName() = assertEquals("game-art/poster-tien-len.webp", posterAsset("tien_len"))

    @Test
    fun posterOfAnUnknownKindIsRefused() {
        assertFailsWith<FileNotFoundException> { provider().openAssetFile(uri("poster/chess"), "r") }
    }

    @Test
    fun aFailingCoreAnswersAnEmptyCursor() {
        val failing = provider().also { it.games = { error("no core") } }
        failing.query(uri("games"), null, null, null, null)!!.use { assertEquals(0, it.count) }
    }

    @Test
    fun writesAreRefused() {
        assertFailsWith<UnsupportedOperationException> { provider().insert(uri("games"), null) }
    }

    private fun provider() =
        Robolectric.buildContentProvider(SpieltischGamesProvider::class.java).create(SpieltischGamesProvider.AUTHORITY).get().also {
            it.games = { listOf(ShelfGame("quiz", "Quiz"), ShelfGame("tien_len", "Tiến Lên")) }
        }

    private fun uri(path: String): Uri = Uri.parse("content://${SpieltischGamesProvider.AUTHORITY}/$path")
}
