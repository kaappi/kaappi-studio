import XCTest
@testable import KaappiStudio

/// Integrity checks for the built-in Scheme examples.
///
/// The example list is duplicated on Android
/// (`shared/src/commonMain/.../data/ExampleRepository.kt`); these tests guard
/// the iOS copy (`Helpers/Examples.swift`) so that obvious breakage (duplicate
/// ids, empty title/code) fails CI.
final class ExamplesTests: XCTestCase {

    func testExampleIdsAreUnique() {
        let ids = schemeExamples.map(\.id)
        let counts = Dictionary(grouping: ids, by: { $0 }).filter { $0.value.count > 1 }
        XCTAssertTrue(
            Set(ids).count == ids.count,
            "Duplicate example ids found: \(counts.keys.sorted().joined(separator: ", "))"
        )
    }

    func testEveryExampleHasNonEmptyTitleAndCode() {
        for example in schemeExamples {
            XCTAssertFalse(
                example.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                "Example \(example.id) has an empty title"
            )
            XCTAssertFalse(
                example.code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                "Example \(example.id) has empty code"
            )
        }
    }

    func testThereAreExamplesToShow() {
        XCTAssertFalse(schemeExamples.isEmpty)
    }
}
