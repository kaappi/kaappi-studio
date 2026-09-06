import Foundation
import WebKit

@MainActor
class EditorViewModel: ObservableObject {
    @Published var isRunning = false
    @Published var isReady = false
    @Published var currentFileName: String?
    @Published var lastStdout: String = ""
    @Published var lastStderr: String = ""
    @Published var lastElapsed: Double = 0
    @Published var hasResult = false

    var webView: WKWebView?

    /// Code handed to loadCode() before the page posted its `ready` event.
    /// `window.kaappiAPI.setCode` silently drops code until the CodeMirror
    /// editor exists (async page init), so early loads would otherwise vanish
    /// and leave the default doc. Mirrors the Android EditorViewModel's
    /// pendingCode: the last load before ready wins and is applied in onReady().
    private var pendingCode: String?

    func onReady() {
        isReady = true
        if let code = pendingCode {
            pendingCode = nil
            loadCode(code)
        }
    }

    func runCode() {
        guard let webView = webView, !isRunning else { return }
        webView.evaluateJavaScript("window.kaappiAPI?.runCode()")
    }

    func clearOutput() {
        hasResult = false
        lastStdout = ""
        lastStderr = ""
        lastElapsed = 0
    }

    func loadCode(_ code: String) {
        guard let webView = webView, isReady else {
            // Page not ready (or web view not created yet): queue the load —
            // the last call wins — instead of silently no-oping; onReady()
            // applies it once the page posted `ready`.
            pendingCode = code
            return
        }
        let escaped = code
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "")
        webView.evaluateJavaScript("window.kaappiAPI?.setCode('\(escaped)')")
        clearOutput()
    }
}
