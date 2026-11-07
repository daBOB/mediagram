package com.mediagram.controller

import java.net.URI
import java.net.URISyntaxException

/**
 * [raw] when it can be the TV box's game night: plain http to a private LAN address
 * (10/8, 172.16/12, 192.168/16) or a `.local` name. Everything else — a website, a Wi-Fi
 * QR code, a lookalike host — is `null`, because the page we load gets the native bridge.
 */
fun lanGameUrl(raw: String): String? {
    val uri = parse(raw) ?: return null
    if (uri.scheme != "http") return null
    val host = uri.host ?: return null
    return raw.takeIf { isPrivateIpv4(host) || (host.endsWith(".local") && host.length > ".local".length) }
}

/** Same scheme, host and port: the web view may move within one box, never off it. */
fun sameBox(
    a: String,
    b: String,
): Boolean {
    val x = parse(a) ?: return false
    val y = parse(b) ?: return false
    return x.scheme == y.scheme && x.host == y.host && portOf(x) == portOf(y)
}

private fun parse(raw: String): URI? =
    try {
        URI(raw)
    } catch (e: URISyntaxException) {
        null
    }

private fun portOf(uri: URI) = if (uri.port == -1 && uri.scheme == "http") 80 else uri.port

private fun isPrivateIpv4(host: String): Boolean {
    val octets = host.split(".").map { it.toIntOrNull() ?: return false }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    val (first, second) = octets
    return first == 10 || (first == 172 && second in 16..31) || (first == 192 && second == 168)
}
