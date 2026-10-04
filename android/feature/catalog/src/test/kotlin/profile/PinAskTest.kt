package catalog.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.ProfileOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one PIN prompt the picker and Manage share — `pin-prompt.js`'s `askPin`. */
class PinAskTest {
    @Test
    fun aPinIsSentAndThePromptClosesOnDone() =
        runTest {
            val ask = PinAsk(this)
            var sent: String? = null
            var finished: String? = null
            ask.ask(PinPrompt("Ada’s PIN", newPin = false), send = { sent = it; ProfileOutcome.Done }, done = { finished = it })
            ask.enter("1234")
            runCurrent()
            assertEquals("1234", sent)
            assertEquals("1234", finished)
            assertNull(ask.prompt.value)
        }

    @Test
    fun aRefusedPinStaysOpenAndSaysWhy() =
        runTest {
            val ask = PinAsk(this)
            ask.ask(PinPrompt("Ada’s PIN", newPin = false), send = { ProfileOutcome.WrongPin }, done = {})
            ask.enter("0000")
            runCurrent()
            assertEquals(PinPrompt("Ada’s PIN", newPin = false, error = "Wrong PIN."), ask.prompt.value)
        }

    /** Said at once and never sent: the server's tries are for PINs that could be right. */
    @Test
    fun anythingButFourDigitsIsSaidAndNotSent() =
        runTest {
            val ask = PinAsk(this)
            var sends = 0
            ask.ask(PinPrompt("Ada’s PIN", newPin = false), send = { sends++; ProfileOutcome.Done }, done = {})
            ask.enter("12a4")
            ask.enter("123")
            runCurrent()
            assertEquals(0, sends)
            assertEquals("A PIN is four digits.", ask.prompt.value?.error)
        }

    @Test
    fun aNewPinIsTypedTwiceBeforeAnythingIsSent() =
        runTest {
            val ask = PinAsk(this)
            val sent = mutableListOf<String>()
            ask.ask(PinPrompt("Choose a PIN for Bo", newPin = true), send = { sent += it; ProfileOutcome.Done }, done = {})
            ask.enter("4321")
            runCurrent()
            assertEquals(emptyList(), sent)
            assertEquals("The new PIN again", ask.prompt.value?.heading)
            ask.enter("4321")
            runCurrent()
            assertEquals(listOf("4321"), sent)
        }

    @Test
    fun twoDifferentNewPinsAreRefusedAndAskedForAgain() =
        runTest {
            val ask = PinAsk(this)
            var sends = 0
            ask.ask(PinPrompt("Choose a PIN for Bo", newPin = true), send = { sends++; ProfileOutcome.Done }, done = {})
            ask.enter("1111")
            ask.enter("2222")
            runCurrent()
            assertEquals(0, sends)
            assertEquals(PinPrompt("Choose a PIN for Bo", newPin = true, error = "The two PINs are not the same."), ask.prompt.value)
        }

    /**
     * A new PIN typed twice and the right shape would fare no better a second
     * time: what was refused is something else — a name, a current PIN sent
     * beside it — so the prompt ends and the caller says why, as the web does.
     */
    @Test
    fun aRefusedNewPinEndsThePromptAndHandsTheReasonBack() =
        runTest {
            val ask = PinAsk(this)
            var told: String? = null
            ask.ask(
                PinPrompt("A PIN for Bo", newPin = true),
                send = { ProfileOutcome.NameTaken },
                done = { error("not done") },
                refused = { told = it },
            )
            ask.enter("2468")
            ask.enter("2468")
            runCurrent()
            assertNull(ask.prompt.value)
            assertEquals("A profile with that name already exists.", told)
        }

    @Test
    fun aCancelledAskIgnoresItsLateAnswer() =
        runTest {
            val ask = PinAsk(this)
            val answer = CompletableDeferred<ProfileOutcome>()
            var finished = false
            ask.ask(PinPrompt("Ada’s PIN", newPin = false), send = { answer.await() }, done = { finished = true })
            ask.enter("1234")
            runCurrent()
            ask.cancel()
            answer.complete(ProfileOutcome.Done)
            runCurrent()
            assertNull(ask.prompt.value)
            assertEquals(false, finished)
        }

    @Test
    fun aCoreThatCouldNotBeAskedSaysItDidNotGoThrough() =
        runTest {
            val ask = PinAsk(this)
            ask.ask(PinPrompt("Ada’s PIN", newPin = false), send = { error("private path") }, done = {})
            ask.enter("1234")
            runCurrent()
            assertEquals("That did not go through. Please try again.", ask.prompt.value?.error)
        }

    @Test
    fun whileBusyAnotherEntryIsIgnored() =
        runTest {
            val ask = PinAsk(this)
            val answer = CompletableDeferred<ProfileOutcome>()
            var sends = 0
            ask.ask(PinPrompt("Ada’s PIN", newPin = false), send = { sends++; answer.await() }, done = {})
            ask.enter("1234")
            runCurrent()
            ask.enter("5678")
            runCurrent()
            assertEquals(1, sends)
            assertTrue(ask.prompt.value!!.busy)
            answer.complete(ProfileOutcome.WrongPin)
            runCurrent()
        }
}
