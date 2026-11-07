package com.mediagram.controller

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanGameUrlTest {
    @Test
    fun acceptsPlainHttpOnThePrivateLan() {
        for (url in listOf(
            "http://192.168.0.37:8080/",
            "http://10.0.0.5:8080/",
            "http://172.20.1.2:8080/x.html",
            "http://spieltisch.local:8080/",
        )) {
            assertEquals(url, lanGameUrl(url), url)
        }
    }

    @Test
    fun rejectsAnythingThatIsNotTheBox() {
        for (raw in listOf(
            "https://192.168.0.37/",
            "http://8.8.8.8:8080/",
            "http://172.32.0.1/",
            "http://192.168.0.37.evil.com/",
            "javascript:alert(1)",
            "",
            "http://example.com/",
            "WIFI:S:home;T:WPA;P:x;;",
        )) {
            assertNull(lanGameUrl(raw), raw)
        }
    }

    @Test
    fun sameBoxIsSameSchemeHostAndPort() {
        assertTrue(sameBox("http://192.168.0.37:8080/", "http://192.168.0.37:8080/avatars/x.webp"))
        assertFalse(sameBox("http://192.168.0.37:8080/", "http://192.168.0.37:8081/"))
        assertFalse(sameBox("http://192.168.0.37:8080/", "http://example.com/"))
    }
}
