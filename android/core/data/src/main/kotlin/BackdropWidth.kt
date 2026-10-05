package data

import android.content.Context

/**
 * The backdrop width this device asks TMDB for: w780 on a phone, w1280 on a
 * tablet or a television, matching the web player's own hero. A fixed width
 * would either blur a wide hero or spend a phone's limited storage on pixels
 * it never shows.
 */
fun interface BackdropWidth {
    fun pixels(): Int
}

/**
 * Reads Android's own smallest-width bucket. `>= 600dp` is the line Android
 * itself draws between a phone and a tablet — the `sw600dp` resource
 * qualifier — so this follows a boundary the platform already decided
 * rather than inventing a second one. A television counts as wide whatever
 * its dp width: it reports 960dp, but that is 1920 px of screen and its hero
 * spans all of it.
 */
class DeviceBackdropWidth(
    private val context: Context,
) : BackdropWidth {
    override fun pixels(): Int =
        if (isTelevision(context) ||
            context.resources.configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP
        ) {
            TABLET_WIDTH
        } else {
            PHONE_WIDTH
        }

    private companion object {
        const val TABLET_SMALLEST_WIDTH_DP = 600
        const val PHONE_WIDTH = 780
        const val TABLET_WIDTH = 1280
    }
}
