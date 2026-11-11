package games

import data.toModel
import model.Profile
import model.WatchSnapshot
import model.hiddenFrom
import uniffi.mediagram_core.CoreInterface
import java.security.MessageDigest

/**
 * Everything a night is built from: whose it is, the kids and section rules it is held to, the kids
 * marks, which catalog it draws on, and the app version whose rules built it. Two reads with the
 * same [fingerprint] build the same night.
 */
class NightInputs(
    val profile: Profile,
    /** The household's kids marks, by the age each is for, as [gameNightConfig] reads them. */
    val marks: Map<String, Int>,
    /** Which installed catalog, and which descriptions this device fetched for it. */
    val catalog: String,
    /** This app's version code: an update may change the rules, so a night from another version is never served. */
    val appVersion: Long,
) {
    /** A SHA-256 over every input above: equal exactly when the same night would be built. */
    val fingerprint: String =
        listOf(profile.id, profile.kids, profile.kidsLimit, profile.hiddenFrom.sorted(), marks.toSortedMap(), catalog, appVersion)
            .joinToString("\n")
            .let { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()) }
            .joinToString("") { "%02x".format(it) }
}

/**
 * Read from the core itself rather than a repository's memory of it, so a profile changed by
 * another screen or by sync is seen at once. `null` with nobody chosen, or with an installed
 * catalog whose version cannot be told: a night nobody can vouch for is not kept.
 */
suspend fun CoreInterface.nightInputs(appVersion: Long): NightInputs? {
    val id = chosenProfile() ?: return null
    val profile = profiles().find { it.id == id }?.toModel() ?: return null
    val state = snapshot(id)
    val marks = WatchSnapshot(emptyList(), emptyList(), emptyList(), state.kids, emptyList(), kidsFromSix = state.kidsFromSix).kidsMarks
    // The installed index is opened read-only and every install gets a version of its own, so the
    // version names its sets and ratings. The counts catch artwork that arrived since, and the digest
    // the fetched descriptions that can move a title into a section (Anime) without a new index.
    val facts = catalogFacts()
    val version = facts.publishedAt?.toString() ?: if (facts.sets == 0uL) "none" else return null
    return NightInputs(profile, marks, "${facts.origin}:$version:${facts.sets}:${facts.posters}:${facts.described}", appVersion)
}
