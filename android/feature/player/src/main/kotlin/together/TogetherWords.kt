package player.together

import model.clockTime
import model.together.Change
import model.together.Noticed
import model.together.Session
import player.speedLabel

/*
 * What watch together says in the player. The web player's
 * `together-words.js` is the reference: these are its words, word for word.
 */

internal const val ENDED = "Watch together ended"
internal const val COULD_NOT_START = "Watch together couldn't start"
internal const val NOT_HERE = "That title isn't on this profile"
internal const val LEFT_NOT_HERE = "Left watch together: that title isn't on this profile"

/** What the button says it does. */
val TogetherButton.words: String
    get() =
        when (this) {
            TogetherButton.START -> "Watch together"
            TogetherButton.JOIN -> "Join"
            TogetherButton.LEAVE -> "Leave watch together"
        }

/**
 * What a screen reader hears of the button: its words, and while it is greyed
 * out why, which the web gives as the button's title.
 */
val TogetherUi.description: String
    get() = listOfNotNull(button.words, reason).joinToString(", ")

/** The two-second notice for another screen's edit [session], as `changeOf` named it. */
internal fun say(
    noticed: Noticed,
    session: Session,
): String {
    val by = noticed.label ?: "Another screen"
    return when (noticed.change) {
        Change.JOINED -> "$by joined"
        Change.LEFT -> "$by left"
        Change.SOUGHT -> "Skipped to ${clockTime(session.pos)} by $by"
        Change.RATE -> "${speedLabel(session.rate.toFloat())} by $by"
        Change.TITLE -> "Next episode by $by"
        Change.PAUSED -> "Paused by $by"
        Change.PLAYED -> "Resumed by $by"
    }
}

private const val TV_LABEL = "TV"

/**
 * The notice for a cast's edit, as `sayOnRemote` in the web's `cast-words.js` says it:
 * the TV did it "on TV", and its own joining and leaving is no news.
 */
internal fun sayOnRemote(
    noticed: Noticed,
    session: Session,
): String? {
    val line = say(noticed, session)
    if (noticed.label != TV_LABEL) return line
    if (noticed.change == Change.JOINED || noticed.change == Change.LEFT) return null
    return if (line.endsWith(" by $TV_LABEL")) line.removeSuffix("by $TV_LABEL") + "on $TV_LABEL" else line
}
