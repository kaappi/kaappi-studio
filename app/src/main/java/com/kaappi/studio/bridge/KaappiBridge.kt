package com.kaappi.studio.bridge

import android.webkit.JavascriptInterface
import android.webkit.WebView

class KaappiBridge(private val listener: BridgeListener) {

    var webView: WebView? = null

    interface BridgeListener {
        fun onReady()
        fun onReadyWithWebView(webView: WebView)
    }

    @JavascriptInterface
    fun onMessage(jsonString: String) {
        try {
            if (jsonString.contains("\"ready\"")) {
                listener.onReady()
                webView?.post { webView?.let { listener.onReadyWithWebView(it) } }
            }
        } catch (_: Exception) { }
    }
}

/**
 * Decodes the JSON string returned by `evaluateJavascript("...getCode()")`.
 *
 * Returns null when there is no content to decode — including when the page
 * has not loaded `kaappiAPI` yet, in which case evaluateJavascript reports
 * the JS value `null` as the literal string "null". Treating that as content
 * would inject/save the text "null" into the editor or a file.
 */
internal fun decodeCodeResult(raw: String?): String? {
    val decoded = try {
        kotlinx.serialization.json.Json.decodeFromString<String>(raw ?: return null)
    } catch (_: Exception) {
        raw?.removeSurrounding("\"")
    }
    return decoded?.takeIf { it != "null" }
}
