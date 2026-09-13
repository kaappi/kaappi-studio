import XCTest
import shared
@testable import KaappiStudio

/// Round-trips user files through the shared Kotlin `FileRepository` from
/// Swift: the suspend functions bridge to `async throws`, declared Kotlin
/// exceptions surface as thrown errors, and the never-fabricate-success rules
/// hold end to end. Runs against the test host's real `Documents/schemes`
/// directory, so every test cleans up the file it created.
@MainActor
final class FileBrowserViewModelTests: XCTestCase {

    private let fileName = "kaappi-vm-test-\(UUID().uuidString.prefix(8))"

    override func tearDown() async throws {
        let vm = FileBrowserViewModel()
        await vm.refresh()
        for file in vm.files where file.name == fileName {
            try? await vm.deleteFile(path: file.path)
        }
    }

    func test_saveListReadDelete_roundTrip() async throws {
        let vm = FileBrowserViewModel()

        let saved = try await vm.saveFile(name: fileName, content: "(display \"héllo\")")
        XCTAssertEqual(saved.name, fileName)
        XCTAssertTrue(saved.path.hasSuffix("/schemes/\(fileName).scm"))
        XCTAssertTrue(vm.files.contains { $0.path == saved.path }, "saveFile must refresh the list")
        let existsAfterSave = try await vm.fileExists(name: fileName)
        XCTAssertTrue(existsAfterSave)

        let content = try await vm.readFile(path: saved.path)
        XCTAssertEqual(content, "(display \"héllo\")")

        try await vm.deleteFile(path: saved.path)
        XCTAssertFalse(vm.files.contains { $0.path == saved.path }, "deleteFile must refresh the list")
        let existsAfterDelete = try await vm.fileExists(name: fileName)
        XCTAssertFalse(existsAfterDelete)
    }

    func test_invalidName_throwsInsteadOfWriting() async {
        let vm = FileBrowserViewModel()

        do {
            _ = try await vm.saveFile(name: "../\(fileName)", content: "")
            XCTFail("a path-escaping name must be rejected")
        } catch {
            XCTAssertFalse(error.localizedDescription.isEmpty, "the Kotlin message must reach the UI")
        }
    }

    func test_readMissingFile_throws() async {
        let vm = FileBrowserViewModel()

        do {
            _ = try await vm.readFile(path: "/nonexistent/\(fileName).scm")
            XCTFail("readFile must throw, never return \"\"")
        } catch {
            XCTAssertTrue(error.localizedDescription.contains("Cannot read"), error.localizedDescription)
        }
    }

    func test_deleteMissingFile_throws() async {
        let vm = FileBrowserViewModel()

        do {
            try await vm.deleteFile(path: "/nonexistent/\(fileName).scm")
            XCTFail("deleting nothing must not report success")
        } catch let error as FileBrowserError {
            XCTAssertEqual(error.errorDescription, "Could not delete \(fileName).scm.")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }
}
