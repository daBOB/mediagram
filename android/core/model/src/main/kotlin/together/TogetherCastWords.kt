package model.together

/*
 * What casting says. Ported word for word from `web/public/lib/together/cast-words.js`;
 * `web/test/fixtures/together/cast.json` holds both to the same words.
 */

object CastWords {
    const val PLAY_ON_TV = "Play on TV"
    const val ASKING = "Asking the TV…"
    const val REPLACE = "Replace"
    const val CANCEL = "Cancel"
    const val NO_ANSWER = "Mediagram isn't open on the TV. Open it there and try again."
    const val LEAVE_FIRST = "Leave watch together first"
    const val PLAYING_ON_TV = "Playing on TV"
    const val STOP = "Stop"
    const val FINISHED = "Finished"
    const val STOPPED = "Stopped on the TV"
    const val LIBRARY_MISSING = "the TV's library doesn't have it yet"
    const val PROFILE_UNKNOWN = "the TV doesn't know this profile yet"
}

fun busyQuestion(title: String) = "The TV is playing $title. Replace it?"

fun refusedLine(reason: String) = "The TV won't play this: $reason"

fun castFrom(from: String) = "Cast from $from"

fun barLine(title: String) = "Playing on TV · $title"

fun ageReason(name: String) = "it's rated above $name's age limit"

fun timeReason(name: String) = "$name's time is up for today"
