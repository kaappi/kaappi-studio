package com.kaappi.studio.bridge

import android.webkit.WebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito

class KaappiBridgeTest {

    private class RecordingListener : KaappiBridge.BridgeListener {
        var readyCount = 0
        var readyWebView: WebView? = null

        override fun onReady() {
            readyCount++
        }

        override fun onReadyWithWebView(webView: WebView) {
            readyWebView = webView
        }
    }

    @Test
    fun readyEvent_notifiesListener_withoutWebView() {
        val listener = RecordingListener()
        val bridge = KaappiBridge(listener)

        bridge.onMessage("""{"event":"ready"}""")

        assertEquals(1, listener.readyCount)
        assertNull(listener.readyWebView)
    }

    @Test
    fun otherEvents_areIgnored() {
        val listener = RecordingListener()
        val bridge = KaappiBridge(listener)

        bridge.onMessage("""{"event":"runComplete","stdout":"hi"}""")

        assertEquals(0, listener.readyCount)
    }

    @Test
    fun malformedMessage_doesNotThrow() {
        val listener = RecordingListener()
        val bridge = KaappiBridge(listener)

        bridge.onMessage("this is not json at all")

        assertEquals(0, listener.readyCount)
    }

    @Test
    fun readyDetection_isSubstringBased() {
        // Documents current behavior: any message containing "ready" triggers the ready path.
        val listener = RecordingListener()
        val bridge = KaappiBridge(listener)

        bridge.onMessage("""{"event":"notready"} contains "ready" quoted""")

        assertEquals(1, listener.readyCount)
    }

    @Test
    fun readyEvent_withAttachedWebView_deliversWebView() {
        val listener = RecordingListener()
        val bridge = KaappiBridge(listener)
        val webView = Mockito.mock(WebView::class.java)
        Mockito.`when`(webView.post(Mockito.any())).thenAnswer { invocation ->
            (invocation.getArgument(0) as Runnable).run()
            true
        }
        bridge.webView = webView

        bridge.onMessage("""{"event":"ready"}""")

        assertEquals(1, listener.readyCount)
        assertSame(webView, listener.readyWebView)
    }

    @Test
    fun webViewPost_failures_areSwallowed() {
        val listener = RecordingListener()
        val bridge = KaappiBridge(listener)
        val webView = Mockito.mock(WebView::class.java)
        Mockito.`when`(webView.post(Mockito.any())).thenThrow(RuntimeException("not mocked"))
        bridge.webView = webView

        try {
            bridge.onMessage("""{"event":"ready"}""")
        } catch (e: Exception) {
            fail("onMessage must not propagate exceptions: $e")
        }

        assertEquals(1, listener.readyCount)
    }
}
