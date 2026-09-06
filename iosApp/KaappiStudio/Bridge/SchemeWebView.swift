import SwiftUI
import WebKit

struct SchemeWebView: UIViewRepresentable {
    let isDark: Bool
    let fontSize: Int
    let onWebViewReady: (WKWebView) -> Void
    let editorViewModel: EditorViewModel

    func makeCoordinator() -> Coordinator {
        Coordinator(editorViewModel: editorViewModel)
    }

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        // Teardown for the retain chain EditorViewModel -> WKWebView ->
        // WKUserContentController -> Coordinator. The Coordinator only holds the
        // view model weakly (breaking the cycle), but the content controller
        // keeps the Coordinator alive until the handler is removed, so drop it
        // explicitly when the editor leaves the hierarchy for good.
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: "kaappi")
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")

        let contentController = config.userContentController
        contentController.add(context.coordinator, name: "kaappi")

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear

        if let htmlURL = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "webview")
            ?? Bundle.main.url(forResource: "index", withExtension: "html") {
            webView.loadFileURL(htmlURL, allowingReadAccessTo: htmlURL.deletingLastPathComponent())
        }

        onWebViewReady(webView)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        let theme = isDark ? "dark" : "light"
        webView.evaluateJavaScript("window.kaappiAPI?.setTheme('\(theme)')")
        webView.evaluateJavaScript("window.kaappiAPI?.setFontSize(\(fontSize))")
    }

    class Coordinator: NSObject, WKScriptMessageHandler {
        // Weak on purpose: the content controller retains this Coordinator, and
        // the view model retains the WKWebView, so a strong reference here would
        // form a retain cycle (view model -> web view -> content controller ->
        // coordinator -> view model).
        weak var editorViewModel: EditorViewModel?

        init(editorViewModel: EditorViewModel) {
            self.editorViewModel = editorViewModel
        }

        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard let body = message.body as? String,
                  let data = body.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let event = json["event"] as? String else { return }

            guard let vm = editorViewModel else { return }
            Task { @MainActor in
                switch event {
                case "ready":
                    vm.onReady()
                case "runStart":
                    vm.isRunning = true
                    vm.hasResult = false
                    // Drop the previous run's output up front: a run that fails
                    // in the instantiate phase only reports runError, and the
                    // output panel must not show the last successful run's
                    // stdout/elapsed next to it.
                    vm.lastStdout = ""
                    vm.lastStderr = ""
                    vm.lastElapsed = 0
                case "runComplete":
                    vm.isRunning = false
                    vm.lastStdout = json["stdout"] as? String ?? ""
                    vm.lastStderr = json["stderr"] as? String ?? ""
                    vm.lastElapsed = json["elapsed"] as? Double ?? 0
                    vm.hasResult = true
                case "runError":
                    vm.isRunning = false
                    // Clear stdout/elapsed so only this run's error shows; a
                    // runStart already cleared the previous result, but keep
                    // runError self-sufficient in case events are reordered.
                    vm.lastStdout = ""
                    vm.lastElapsed = 0
                    vm.lastStderr = json["error"] as? String ?? "Unknown error"
                    vm.hasResult = true
                default:
                    break
                }
            }
        }
    }
}
