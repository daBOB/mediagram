package com.mediagram.controller

/** What the app shows: the waiting screen, or the box's phone page. */
sealed interface ControllerScreen {
    data class Searching(
        val notice: String? = null,
    ) : ControllerScreen

    data class Playing(
        val url: String,
    ) : ControllerScreen
}

sealed interface ControllerEvent {
    /** The TV announced game night at [url]. */
    data class Found(
        val url: String,
    ) : ControllerEvent

    /** The camera read [raw] from a QR code. */
    data class Scanned(
        val raw: String,
    ) : ControllerEvent

    /** The page at [url] could not be loaded. */
    data class PageFailed(
        val url: String,
    ) : ControllerEvent
}

const val NOT_THE_BOX = "Das ist nicht der Code vom Fernseher."

/** Last night's box is tried straight away; discovery corrects it if the box has moved. */
fun startScreen(remembered: String?): ControllerScreen =
    remembered?.let(::lanGameUrl)?.let { ControllerScreen.Playing(it) } ?: ControllerScreen.Searching()

fun ControllerScreen.after(event: ControllerEvent): ControllerScreen =
    when (event) {
        is ControllerEvent.Found -> lanGameUrl(event.url)?.let { ControllerScreen.Playing(it) } ?: this
        is ControllerEvent.Scanned -> lanGameUrl(event.raw.trim())?.let { ControllerScreen.Playing(it) } ?: ControllerScreen.Searching(NOT_THE_BOX)
        // A failure of a box we already left behind changes nothing.
        is ControllerEvent.PageFailed -> if (this == ControllerScreen.Playing(event.url)) ControllerScreen.Searching() else this
    }
