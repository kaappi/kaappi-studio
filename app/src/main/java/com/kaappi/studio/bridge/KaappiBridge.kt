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
