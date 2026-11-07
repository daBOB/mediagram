package com.mediagram.controller

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The box's phone page, as a surface to tap and draw on: no zoom, no overscroll, screen kept on,
 * and never anywhere but this box. [onFailed] gets the box URL when the page cannot load.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun GameWebView(
    url: String,
    bridge: AppBridge,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val box by rememberUpdatedState(url)
    val failed by rememberUpdatedState(onFailed)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                overScrollMode = View.OVER_SCROLL_NEVER
                keepScreenOn = true
                addJavascriptInterface(bridge, "mediagramApp")
                webViewClient =
                    object : WebViewClient() {
                        // a link or redirect off the box would take the bridge with it
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ) = !sameBox(box, request.url.toString())

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) failed(box)
                        }
                    }
                tag = url
                loadUrl(url)
            }
        },
        update = { view ->
            // the box moved: follow it to its new address
            if (view.tag != url) {
                view.tag = url
                view.loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}
