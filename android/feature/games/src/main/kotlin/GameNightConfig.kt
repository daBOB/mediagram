package games

import model.MediaSet
import model.Profile
import model.forKidsProfile
import uniffi.mediagram_core.GameNightConfig

/** The FSK a grown-up's game night counts as kid-safe when "Nur für Kinder" is switched on. */
const val GAME_NIGHT_KIDS_LIMIT = 12

/**
 * What the core is told about kids: which sets are kid-safe and whether the
 * toggle is locked on. A kids profile is held to its own limit; a grown-up
 * starts at [GAME_NIGHT_KIDS_LIMIT] with the toggle free. The rule itself is
 * [forKidsProfile] — never repeated in the core. The TMDB key is the view
 * model's to add.
 */
fun gameNightConfig(
    sets: List<MediaSet>,
    profile: Profile,
    marks: Map<String, Int>,
): GameNightConfig {
    val limit = if (profile.kids) profile.kidsLimit else GAME_NIGHT_KIDS_LIMIT
    return GameNightConfig(
        kidsSetIds = forKidsProfile(sets, marks, limit).map { it.setId },
        kidsLocked = profile.kids,
        tmdbKey = null,
    )
}
