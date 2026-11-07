package com.mediagram.controller

import android.content.SharedPreferences
import android.webkit.JavascriptInterface
import androidx.core.content.edit
import org.json.JSONObject

/**
 * `window.mediagramApp` for the game-night page. The page keeps its memory (name, seat token,
 * avatar) here rather than in localStorage, which belongs to the box's address and is lost when
 * the router hands the box a new one. Called on the web view's bridge thread.
 */
class AppBridge(
    private val prefs: SharedPreferences,
    private val buzz: (String) -> Unit,
) {
    @JavascriptInterface
    fun vibrate(kind: String) = buzz(kind)

    @JavascriptInterface
    fun getMemory(): String {
        val memory = JSONObject()
        for ((key, value) in prefs.all) {
            if (key.startsWith(PAGE) && value is String) memory.put(key.removePrefix(PAGE), value)
        }
        return memory.toString()
    }

    @JavascriptInterface
    fun setMemory(
        key: String,
        value: String?,
    ) {
        prefs.edit { if (value == null) remove(PAGE + key) else putString(PAGE + key, value) }
    }

    private companion object {
        // keeps the page's keys apart from the app's own (box.url)
        const val PAGE = "page."
    }
}
