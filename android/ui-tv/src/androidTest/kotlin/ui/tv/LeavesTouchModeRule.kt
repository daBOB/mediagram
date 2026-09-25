package ui.tv

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/**
 * A freshly launched instrumented test's `Activity` starts in Android's
 * touch mode, where `requestFocus()` is quietly ignored by anything that is
 * not itself focusable-in-touch-mode. Every screen in this module assumes a
 * remote, which never leaves touch mode to begin with, so every instrumented
 * test in the set needs this left before it composes any content — one
 * shared rule rather than each test class reaching for its own workaround,
 * so a class's focus assertions never depend on another class having
 * already run first and left the device out of touch mode for it.
 */
class LeavesTouchModeRule : ExternalResource() {
    override fun before() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }
}
