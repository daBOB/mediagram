package com.mediagram.controller

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AppBridgeTest {
    private val prefs =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("bridge-test", Context.MODE_PRIVATE)
    private val buzzes = mutableListOf<String>()

    private fun bridge() = AppBridge(prefs) { buzzes += it }

    @Test
    fun emptyMemoryIsAnEmptyObject() {
        assertEquals("{}", bridge().getMemory())
    }

    @Test
    fun memoryIsWrittenReadAndRemoved() {
        val bridge = bridge()

        bridge.setMemory("spieltisch.name", "Ada")
        assertEquals("Ada", JSONObject(bridge.getMemory()).getString("spieltisch.name"))

        bridge.setMemory("spieltisch.name", null)
        assertEquals("{}", bridge.getMemory())
    }

    @Test
    fun memorySurvivesANewBridge() {
        bridge().setMemory("spieltisch.token", "t1")

        assertEquals("t1", JSONObject(bridge().getMemory()).getString("spieltisch.token"))
    }

    @Test
    fun theAppsOwnSettingsStayOutOfThePagesMemory() {
        prefs.edit().putString("box.url", "http://192.168.0.37:8080/").commit()

        assertEquals("{}", bridge().getMemory())
    }

    @Test
    fun vibrateReachesTheBuzzer() {
        bridge().vibrate("won")

        assertEquals(listOf("won"), buzzes)
    }
}
