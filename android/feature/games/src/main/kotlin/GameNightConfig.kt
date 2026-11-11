package games

import model.MediaSet
import model.Profile
import model.forKidsProfile
import model.hiddenFrom
import model.sectionKey
import uniffi.mediagram_core.NightTitlesConfig

/** The FSK a grown-up's game night counts as kid-safe when "Nur für Kinder" is switched on. */
const val GAME_NIGHT_KIDS_LIMIT = 12

/**
 * What the core is told about the profile: which sets are kid-safe, whether
 * the toggle is locked on, and which sets sit in sections it sees. A kids
 * profile is held to its own limit; a grown-up starts at
 * [GAME_NIGHT_KIDS_LIMIT] with the toggle free. The rules themselves are
 * [forKidsProfile] and [hiddenFrom], as the library applies them — never
 * repeated in the core.
 */
fun gameNightConfig(
    sets: List<MediaSet>,
    profile: Profile,
    marks: Map<String, Int>,
): NightTitlesConfig {
    val limit = if (profile.kids) profile.kidsLimit else GAME_NIGHT_KIDS_LIMIT
    val hidden = profile.hiddenFrom
    return NightTitlesConfig(
        kidsSetIds = forKidsProfile(sets, marks, limit).map { it.setId },
        visibleSetIds = sets.filter { it.sectionKey() !in hidden }.map { it.setId },
        kidsLocked = profile.kids,
    )
}
