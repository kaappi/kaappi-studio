import XCTest
import shared
@testable import KaappiStudio

/// Proves the app really links the shared Kotlin framework and that the parts
/// of its contract the Swift side relies on survive the Objective-C bridge.
///
/// Example integrity itself (unique ids, non-empty titles and code) is tested
/// once, in `shared/src/commonTest`; these tests only need the list to be
/// reachable and usable from Swift.
final class SharedFrameworkTests: XCTestCase {

    func testSharedExamplesAreAvailable() {
        let examples = ExampleRepository.shared.examples
        XCTAssertFalse(examples.isEmpty)
        XCTAssertEqual(examples.first?.id, "hello")
        XCTAssertFalse(examples.first?.description_.isEmpty ?? true, "description_ is the bridged name")
    }

    func testEveryCategoryHasALabelAndKeepsDeclarationOrder() {
        let labels = ExampleCategory.entries.map(\.label)
        XCTAssertEqual(labels.first, "Getting Started")
        XCTAssertEqual(Set(labels).count, labels.count)
        for label in labels {
            XCTAssertFalse(label.isEmpty)
        }
    }

    func testThemeModeBridgesToSwift() {
        XCTAssertEqual(ThemeMode.entries.map(\.label), ["Light", "Dark", "System"])
        XCTAssertEqual(SettingsViewModel.fontSizeRange, 10...24)
    }

    func testSanitizeRejectsPathSeparatorsAsAThrownError() {
        // `@Throws(IllegalArgumentException::class)` turns the Kotlin exception
        // into a Swift error instead of terminating the process.
        XCTAssertThrowsError(try SchemeFileNames.shared.sanitize(name: "../escape"))
        XCTAssertThrowsError(try SchemeFileNames.shared.sanitize(name: "   "))
        XCTAssertEqual(try SchemeFileNames.shared.sanitize(name: " hello.scm "), "hello")
    }
}
