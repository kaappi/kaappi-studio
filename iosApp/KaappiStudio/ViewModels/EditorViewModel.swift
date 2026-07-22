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

    func onReady() {
        isReady = true
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
