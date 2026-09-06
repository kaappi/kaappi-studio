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

    /// Code queued by `loadCode` before the page posted its `ready` event
    /// (`window.kaappiAPI` is not assigned until the async init completes).
    /// Mirrors the Android side's `setPendingCode`/`consumePendingCode`:
    /// the last requested load wins and is applied once the page is ready.
    private(set) var pendingLoad: String?

    func onReady() {
        isReady = true
        if let code = pendingLoad {
            pendingLoad = nil
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
        // The readiness guard comes first: before `makeUIView` has even
        // assigned `webView`, a load would be dropped instead of queued. Once
        // ready, `webView` always exists, so this covers both cases with the
        // same queue — and makes the pending path testable without a
        // WKWebView.
        guard isReady else {
            // Page has not finished init yet, so `window.kaappiAPI?.setCode`
            // would silently no-op. Queue the load; `onReady` applies it.
            pendingLoad = code
            return
        }
        guard let webView = webView else { return }
        let escaped = code
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "")
        webView.evaluateJavaScript("window.kaappiAPI?.setCode('\(escaped)')")
        clearOutput()
    }
}
