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

    func testRequiredWebviewAssetsExistInAppBundle() throws {
        for (name, ext) in requiredAssets {
            let url = try XCTUnwrap(
                appBundle.url(forResource: name, withExtension: ext),
                "Expected \(name).\(ext) to be bundled in the app resources"
            )
            XCTAssertTrue(
                FileManager.default.fileExists(atPath: url.path),
                "\(name).\(ext) exists in the bundle but is missing on disk"
            )
        }
    }

    func testIndexHTMLIsNonEmpty() throws {
        let url = try XCTUnwrap(
            appBundle.url(forResource: "index", withExtension: "html")
        )
        let contents = try String(contentsOf: url, encoding: .utf8)
        XCTAssertFalse(contents.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }
}
