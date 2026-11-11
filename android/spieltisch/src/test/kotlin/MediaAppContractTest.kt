package com.mediagram.spieltisch

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two apps name the provider, its permission and the night's columns separately; a rename on
 * one side alone would leave Spieltisch locked with no error anywhere. Gradle runs this from the
 * module's folder, so the media app's sources are a path away.
 */
class MediaAppContractTest {
    private val android = "http://schemas.android.com/apk/res/android"

    private fun elements(
        manifest: String,
        tag: String,
    ): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(manifest))
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.attr(name: String) = getAttributeNS(android, name)

    /** The media app's own constants, read from its provider's source. */
    private val provider: Map<String, String> =
        Regex("""const val (\w+) = "([^"]+)"""")
            .findAll(File("../feature/games/src/main/kotlin/GameNightProvider.kt").readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun bothAppsNameTheSameProviderPermissionAndColumns() {
        val permission = provider.getValue("PERMISSION")
        assertEquals(MediaApp.AUTHORITY, provider["AUTHORITY"])
        assertEquals(MediaApp.KIDS_LOCKED, provider["KIDS_LOCKED"])
        assertEquals(MediaApp.TITLES, provider["TITLES"])
        assertEquals(MediaApp.VERSION_NAME, provider["VERSION_NAME"])
        assertEquals(MediaApp.VERSION_CODE, provider["VERSION_CODE"])
        assertEquals(MediaApp.BYTES, provider["BYTES"])
        assertEquals(MediaApp.SHA256, provider["SHA256"])
        val source = File("../feature/games/src/main/kotlin/GameNightProvider.kt").readText()
        assertTrue("""listOf("${MediaApp.RELEASE}")""" in source, "the provider answers the release row where Spieltisch asks for it")
        assertTrue("""listOf("${MediaApp.RELEASE_APK}")""" in source, "the provider serves the APK where Spieltisch asks for it")

        val media = "../app/src/main/AndroidManifest.xml"
        val declared = elements(media, "provider").single { it.attr("name") == "games.GameNightProvider" }
        assertEquals(MediaApp.AUTHORITY, declared.attr("authorities"))
        assertEquals(permission, declared.attr("permission"))
        assertTrue(elements(media, "permission").any { it.attr("name") == permission && it.attr("protectionLevel") == "signature" })

        val own = "src/main/AndroidManifest.xml"
        assertTrue(elements(own, "permission").any { it.attr("name") == permission && it.attr("protectionLevel") == "signature" })
        assertTrue(elements(own, "uses-permission").any { it.attr("name") == permission })
        assertTrue(elements(own, "provider").any { it.attr("authorities") == MediaApp.AUTHORITY }, "Spieltisch can see the provider")
    }
}
