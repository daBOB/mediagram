package com.mediagram.controller

import com.mediagram.controller.ControllerEvent.Found
import com.mediagram.controller.ControllerEvent.PageFailed
import com.mediagram.controller.ControllerEvent.Scanned
import com.mediagram.controller.ControllerScreen.Playing
import com.mediagram.controller.ControllerScreen.Searching
import kotlin.test.Test
import kotlin.test.assertEquals

class ControllerScreenTest {
    private val a = "http://192.168.0.37:8080/"
    private val b = "http://192.168.0.41:8080/"

    @Test
    fun startsFromTheRememberedBoxOrSearches() {
        assertEquals(Searching(), startScreen(null))
        assertEquals(Playing(a), startScreen(a))
    }

    @Test
    fun foundBoxIsJoined() {
        assertEquals(Playing(a), Searching().after(Found(a)))
        assertEquals(Playing(a), Playing(a).after(Found(a)))
    }

    @Test
    fun boxMovedSinceLastNight() {
        assertEquals(Playing(b), Playing(a).after(Found(b)))
    }

    @Test
    fun tvRestartedGameNight() {
        val lost = Playing(a).after(PageFailed(a))
        assertEquals(Searching(), lost)
        assertEquals(Playing(a), lost.after(Found(a)))
    }

    @Test
    fun aFailureOfAnEarlierBoxIsIgnored() {
        assertEquals(Playing(b), Playing(b).after(PageFailed(a)))
    }

    @Test
    fun foreignQrCode() {
        assertEquals(Searching("Das ist nicht der Code vom Fernseher."), Searching().after(Scanned("https://example.com")))
        assertEquals(Playing(a), Searching().after(Scanned(a)))
    }
}
