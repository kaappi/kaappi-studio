package com.kaappi.studio.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kaappi.studio.bridge.KaappiBridge
import com.kaappi.studio.viewmodel.EditorViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EditorScreen(
    editorViewModel: EditorViewModel,
    isDark: Boolean,
    fontSize: Int,
    onWebViewReady: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lastResult by editorViewModel.lastResult.collectAsState()

    val bridge = remember {
        KaappiBridge(object : KaappiBridge.BridgeListener {
            override fun onReady() {
                editorViewModel.onReady()
            }
            override fun onReadyWithWebView(webView: WebView) {
                val pending = editorViewModel.consumePendingCode() ?: return
                val b64 = android.util.Base64.encodeToString(
                    pending.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP,
                )
                webView.evaluateJavascript(
                    "window.kaappiAPI?.setCodeBase64('$b64')", null,
                )
            }
        })
    }

    Column(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = true
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    addJavascriptInterface(bridge, "KaappiBridge")
                    bridge.webView = this
                    loadUrl("file:///android_asset/webview/index.html")
                    onWebViewReady(this)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f),
            update = { webView ->
                val theme = if (isDark) "dark" else "light"
                webView.evaluateJavascript("window.kaappiAPI?.setTheme('$theme')", null)
                webView.evaluateJavascript("window.kaappiAPI?.setFontSize($fontSize)", null)
            },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val outputScrollState = rememberScrollState()
        SelectionContainer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .background(
                    if (isDark) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .verticalScroll(outputScrollState)
                .padding(8.dp),
        ) {
            val result = lastResult
            if (result != null) {
                Text(
                    text = buildAnnotatedString {
                        if (result.stdout.isNotEmpty()) {
                            append(result.stdout)
                        }
                        if (result.stderr.isNotEmpty()) {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                                append(result.stderr)
                            }
                        }
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = FontStyle.Italic,
                            )
                        ) {
                            append("\n(${String.format("%.1f", result.elapsedMs)} ms)")
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.5).sp,
                )
            } else {
                Text(
                    text = "Output will appear here",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}
