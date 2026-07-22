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
        let editorViewModel: EditorViewModel

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

            let vm = editorViewModel
            Task { @MainActor in
                switch event {
                case "ready":
                    vm.onReady()
                case "runStart":
                    vm.isRunning = true
                    vm.hasResult = false
                case "runComplete":
                    vm.isRunning = false
                    vm.lastStdout = json["stdout"] as? String ?? ""
                    vm.lastStderr = json["stderr"] as? String ?? ""
                    vm.lastElapsed = json["elapsed"] as? Double ?? 0
                    vm.hasResult = true
                case "runError":
                    vm.isRunning = false
                    vm.lastStderr = json["error"] as? String ?? "Unknown error"
                    vm.hasResult = true
                default:
                    break
                }
            }
        }
    }
}
