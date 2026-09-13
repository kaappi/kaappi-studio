import Foundation
@preconcurrency import shared

enum FileBrowserError: LocalizedError {
    case deleteFailed(path: String)

    var errorDescription: String? {
        switch self {
        case .deleteFailed(let path):
            return "Could not delete \((path as NSString).lastPathComponent)."
        }
    }
}

/// Main-actor front for the shared Kotlin `FileRepository`.
///
/// File names, sanitization, atomic writes, the never-fabricate-success rules
/// and the schemes directory layout all live in `shared` (see the `expect`
/// KDoc in `FileRepository.kt`); this class only adapts the suspend API to
/// Swift concurrency and keeps the published list current. Kotlin exceptions
/// declared with `@Throws` arrive here as `NSError`s whose
/// `localizedDescription` is the Kotlin message.
@MainActor
class FileBrowserViewModel: ObservableObject {
    @Published var files: [SchemeFile] = []

    private let repository: FileRepository

    init(repository: FileRepository = FileRepository()) {
        self.repository = repository
    }

    func refresh() async {
        // listFiles never throws for I/O (an unreadable directory lists as
        // empty — see the expect KDoc); the only error that can cross the
        // bridge is cancellation, and a cancelled refresh must leave the
        // list as it was rather than blank it.
        guard let listed = try? await repository.listFiles() else { return }
        files = listed
    }

    /// Throws on invalid names or write failures — it never fabricates success.
    @discardableResult
    func saveFile(name: String, content: String) async throws -> SchemeFile {
        let file = try await repository.writeFile(name: name, content: content)
        await refresh()
        return file
    }

    /// True when a file with this (sanitized) base name already exists, so the
    /// new-file flow can confirm before overwriting. Checks the disk, not the
    /// in-memory list: this view model instance can be separate from the one
    /// other views save through, so `files` may be stale.
    func fileExists(name: String) async throws -> Bool {
        let base = try SchemeFileNames.shared.sanitize(name: name)
        return try await repository.listFiles().contains { $0.name == base }
    }

    /// Deletes the file at `path`. The list is refreshed either way so it shows
    /// what is actually on disk; throws when nothing was deleted, matching the
    /// Android view model.
    func deleteFile(path: String) async throws {
        let deleted = try await repository.deleteFile(path: path).boolValue
        await refresh()
        if !deleted { throw FileBrowserError.deleteFailed(path: path) }
    }

    /// Throws instead of returning "" so a failed read can never lead to the
    /// real content being overwritten with empty text.
    func readFile(path: String) async throws -> String {
        try await repository.readFile(path: path)
    }
}
