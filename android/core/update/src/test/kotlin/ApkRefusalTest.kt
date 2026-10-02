package update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApkRefusalTest {
    private val own = "com.mediagram.android"
    private val key = setOf("5840181d")

    private fun refusal(
        pkg: String? = own,
        code: Long = 93_000,
        signers: Set<String> = key,
    ) = apkRefusal(pkg, code, signers, own, installedCode = 92_002, ownSigners = key)

    @Test
    fun theSameAppNewerAndSameKeyInstalls() = assertNull(refusal())

    @Test
    fun anotherPackageIsRefused() = assertEquals("the download is com.example, not this app", refusal(pkg = "com.example"))

    @Test
    fun anUnreadableFileIsRefused() = assertEquals("the download is not an APK this device can read", refusal(pkg = null))

    @Test
    fun anOlderOrEqualVersionIsRefused() = assertEquals("the download is not newer than this app", refusal(code = 92_002))

    @Test
    fun anotherKeyIsRefused() = assertEquals("the download is signed with another key", refusal(signers = setOf("deadbeef")))

    @Test
    fun noSignersReadableLeavesTheKeyCheckToAndroid() = assertNull(refusal(signers = emptySet()))
}
