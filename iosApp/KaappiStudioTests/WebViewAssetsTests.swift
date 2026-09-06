import XCTest

/// Smoke tests for the webview assets bundled into the app.
///
/// These tests deliberately do NOT depend on `kaappi.wasm`: CI-built iOS
/// artifacts never contain the wasm binary (it is gitignored and fetched only
/// for local/Android builds), so assertions here only cover the static webview
/// resources that are always committed.
final class WebViewAssetsTests: XCTestCase {

    /// Hosted unit tests run inside the app process, so the main bundle is
    /// the KaappiStudio.app bundle where the `Resources/webview` files land.
    private var appBundle: Bundle { Bundle.main }

    private let requiredAssets = [
        ("index", "html"),
        ("bridge", "js"),
        ("editor", "js"),
        ("styles", "css"),
    ]

    /// Mirrors SchemeWebView's lookup: `Resources/webview` is a folder
    /// reference copied as `webview/` into the bundle, with a flat-root
    /// fallback for older bundle layouts.
    private func bundledAsset(_ name: String, _ ext: String) -> URL? {
        appBundle.url(forResource: name, withExtension: ext, subdirectory: "webview")
            ?? appBundle.url(forResource: name, withExtension: ext)
    }

    func testRequiredWebviewAssetsExistInAppBundle() {
        for (name, ext) in requiredAssets {
            XCTAssertNotNil(
                bundledAsset(name, ext),
                "Expected \(name).\(ext) to be bundled in the app resources"
            )
        }
    }

    func testIndexHTMLIsNonEmpty() throws {
        let url = try XCTUnwrap(bundledAsset("index", "html"))
        let contents = try String(contentsOf: url, encoding: .utf8)
        XCTAssertFalse(contents.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }
}
