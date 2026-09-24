package setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val NOTHING_PINNED = "That channel has nothing pinned. Run `mediagram push-index` there."

/**
 * What the picker puts on screen decides whether a person can get past the
 * last setup step at all: a failure with no control offered is a dead end,
 * and a list thrown away on a failure is a step that has to be started
 * again.
 */
class LibraryPromptTest {
    @Test
    fun aListNotBackYetIsSomethingToWaitFor() {
        assertEquals(LibraryPrompt.Waiting, libraryPromptFor(choices = null, error = null))
    }

    /**
     * The sentence comes from the core, which is where the detail is:
     * nothing pinned there, or more than one thing, and what to run to fix
     * it. This screen must not flatten those into one message of its own.
     */
    @Test
    fun aListingThatFailedSaysWhyAndOffersToLookAgain() {
        val prompt = libraryPromptFor(choices = null, error = NOTHING_PINNED)

        assertEquals(NOTHING_PINNED, assertIs<LibraryPrompt.LookAgain>(prompt).explanation)
    }

    @Test
    fun anAccountInNoChannelsIsToldSoRatherThanLeftBlank() {
        val prompt = libraryPromptFor(choices = emptyList(), error = null)

        assertEquals(NOTHING_TO_CHOOSE, assertIs<LibraryPrompt.LookAgain>(prompt).explanation)
    }

    /**
     * A pick that did not take leaves the list on screen: trying another
     * channel is the remedy, and it is one tap away only if the list is
     * still there.
     */
    @Test
    fun aPickThatFailedStillOffersEverythingElseToPick() {
        val films = LibraryOption(handle = "h-films", title = "Films")

        val prompt = libraryPromptFor(choices = listOf(films), error = NOTHING_PINNED)

        assertEquals(listOf(films), assertIs<LibraryPrompt.Choose>(prompt).choices)
    }
}
