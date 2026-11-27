package com.mediagram.spieltisch

import kotlin.test.Test
import kotlin.test.assertEquals

/** The TV page's address: films off when the media app did not answer, and the game the home screen asked for. */
class GamePageAddressTest {
    private val tv = "http://127.0.0.1:8080/tv"

    @Test
    fun plain() = assertEquals(tv, gamePageAddress(tv, filmsUnavailable = false, game = null))

    @Test
    fun filmsOff() = assertEquals("$tv?films=0", gamePageAddress(tv, filmsUnavailable = true, game = null))

    @Test
    fun gameOnly() = assertEquals("$tv?game=durak", gamePageAddress(tv, filmsUnavailable = false, game = "durak"))

    @Test
    fun both() = assertEquals("$tv?films=0&game=durak", gamePageAddress(tv, filmsUnavailable = true, game = "durak"))

    @Test
    fun aGameIsEncoded() = assertEquals("$tv?game=a%26b", gamePageAddress(tv, filmsUnavailable = false, game = "a&b"))
}
