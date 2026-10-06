package testing

import java.io.File
import kotlin.test.fail

/**
 * The web player's fixture at `web/test/fixtures/<relative>`, found by
 * walking up from the working directory.
 *
 * A missing fixture fails the test rather than skipping it: these tests are
 * what holds this app to the web player's decisions, and one that quietly
 * skips holds it to nothing.
 */
fun webFixture(relative: String): File {
    val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
    return generateSequence(start) { it.parentFile }
        .map { File(it, "web/test/fixtures/$relative") }
        .firstOrNull { it.isFile }
        ?: fail("$relative not found above $start")
}
