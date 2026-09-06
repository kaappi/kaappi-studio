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
        // The user content controller strongly holds the script message
        // handler; remove it so the coordinator is released with the view
        // instead of living as long as the configuration object.
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: "kaappi")
        guard let vm = coordinator.editorViewModel else { return }
        // Mirror Android's readiness reset on WebView recreation (5d89219):
        // the recreated webview loads a fresh page that is not ready until it
        // posts `ready`, so Run/Save disable again and loadCode queues instead
        // of targeting a page whose window.kaappiAPI does not exist yet.
        vm.isReady = false
        vm.webView = nil
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
        // Weak on purpose: the user content controller keeps this coordinator
        // alive for as long as the webview lives, so a strong reference here
        // would give webview → coordinator → view model retain cycle.
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

            Task { @MainActor in
                // Resolved on the main actor: WebKit happens to deliver script
                // messages on the main thread today, but reading the view
                // model's state here is only sound once inside the Task.
                guard let vm = editorViewModel else { return }
                switch event {
                case "ready":
                    vm.onReady()
                case "runStart":
                    vm.isRunning = true
                    vm.hasResult = false
                    // A new run invalidates the previous run's output; without
                    // this, a run that fails shows stale stdout/elapsed from
                    // the last successful one.
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
                    // runError can arrive without a preceding runStart (init
                    // failure, runtime unavailable), so clear stale output
                    // here too rather than relying on runStart alone.
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
