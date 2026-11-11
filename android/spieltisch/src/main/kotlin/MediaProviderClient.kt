package com.mediagram.spieltisch

import android.content.ContentProviderClient
import android.content.ContentResolver

/**
 * Runs [block] on an unstable client of the media app's provider, `null` when there is none. A stable
 * reference (what the resolver's own `query` and `openFileDescriptor` take) kills this process when the
 * media app dies or is replaced while it is held; an unstable one turns that into an exception here, so
 * the media app updating itself never ends a running night. The client is released when [block] ends.
 */
internal inline fun <T> ContentResolver.withMediaApp(block: (ContentProviderClient) -> T): T? {
    val client = acquireUnstableContentProviderClient(MediaApp.AUTHORITY) ?: return null
    try {
        return block(client)
    } finally {
        client.close()
    }
}
