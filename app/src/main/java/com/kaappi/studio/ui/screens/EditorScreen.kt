package com.kaappi.studio.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kaappi.studio.viewmodel.EditorViewModel

@Composable
fun EditorScreen(
    editorViewModel: EditorViewModel,
    isDark: Boolean,
    fontSize: Int,
    getWebView: () -> WebView,
    modifier: Modifier = Modifier,
) {
    val lastResult by editorViewModel.lastResult.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        AndroidView(
            // The WebView is owned by the Activity and reused across section
            // switches and recompositions (issue #3); never create a new one here.
            factory = {
                getWebView().also { webView ->
                    (webView.parent as? ViewGroup)?.removeView(webView)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f),
            update = { webView ->
                applyEditorAppearance(webView, isDark, fontSize)
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

/**
 * Pushes the current theme and font size into the editor page.
 *
 * Called from the [EditorScreen] update block on every recomposition and —
 * because that block can fire in the same composition pass that attaches the
 * WebView, before the page has committed, losing its commands against the
 * blank initial page — again from the `ready` path in MainActivity so the
 * appearance is always re-sent once the page reports ready (issue #15).
 */
internal fun applyEditorAppearance(webView: WebView, isDark: Boolean, fontSize: Int) {
    val theme = if (isDark) "dark" else "light"
    webView.evaluateJavascript("window.kaappiAPI?.setTheme('$theme')", null)
    webView.evaluateJavascript("window.kaappiAPI?.setFontSize($fontSize)", null)
}
