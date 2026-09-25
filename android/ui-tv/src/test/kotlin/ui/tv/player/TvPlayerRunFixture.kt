package ui.tv.player

import data.CatalogRepository
import io.mockk.coEvery
import io.mockk.mockk
import model.Kind
import model.Profile
import model.WatchSnapshot
import ui.tv.catalog.set

/** The three-title run the up-next tests open the middle of. */
internal val THREE_TITLE_RUN = listOf("set-zero", "set-one", "set-two")

/**
 * A player fixture whose catalogue knows every title of [THREE_TITLE_RUN],
 * each ten minutes long — enough for up next to name what follows and to
 * know when the last half-minute begins.
 */
internal fun runFixture(
    snapshot: WatchSnapshot = WatchSnapshot.Empty,
    profile: Profile? = null,
): TvPlayerFixture {
    val catalog = mockk<CatalogRepository>(relaxed = true)
    coEvery { catalog.mediaSet("set-zero") } returns set("set-zero", Kind.EPISODE, "Before", show = "A Show", addedAt = 1, episode = 3, durationSecs = 600)
    coEvery { catalog.mediaSet("set-one") } returns set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
    coEvery { catalog.mediaSet("set-two") } returns set("set-two", Kind.EPISODE, "After", show = "A Show", addedAt = 1, episode = 5, durationSecs = 600)
    return TvPlayerFixture(snapshot = snapshot, profile = profile, catalog = catalog)
}

/** Into the last half-minute of the open title, as a seek there lands: the card's own cue. */
internal fun TvPlayerScreenHarness.nearTheEnd() {
    compose.runOnUiThread {
        fixture.positionMs = 590_000L
        controller.get().playerViewModel.onSeeked()
    }
    compose.waitForIdle()
}
