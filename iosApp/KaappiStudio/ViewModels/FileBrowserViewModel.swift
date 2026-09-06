import Foundation

struct SchemeFile: Identifiable {
    var id: String { path }
    let name: String
    let path: String
    var content: String
    let lastModified: Date
}

enum FileBrowserError: LocalizedError {
    case invalidFileName(String)
    case writeFailed(name: String)
    case readFailed(path: String)

    var errorDescription: String? {
        switch self {
        case .invalidFileName(let name):
            return "\"\(name)\" is not a valid file name. " +
                "Use letters, digits, '_', '-' and spaces only."
        case .writeFailed(let name):
            return "Could not save \(name).scm."
        case .readFailed(let path):
            return "Could not read file at \(path)."
        }
    }
}

/// Mirrors `SchemeFileNames` in shared/src/commonMain (FileRepository.kt) —
/// keep the two in sync. Both validate with the same rule so a hostile name
/// behaves identically on every platform.
enum SchemeFileNames {
    static let extensionSuffix = ".scm"

    /// Validates a user-supplied name and returns its base name (no extension).
    /// Leading/trailing whitespace is trimmed and a trailing ".scm" is accepted
    /// and stripped. Throws `FileBrowserError.invalidFileName` for blank names
    /// and names containing characters outside `[A-Za-z0-9_\- ]` — in
    /// particular path separators and "..", so a hostile name can never escape
    /// the schemes directory.
    static func sanitize(_ name: String) throws -> String {
        // Kotlin's trim() strips newlines too, so .whitespacesAndNewlines
        // keeps the two copies of the rule identical.
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw FileBrowserError.invalidFileName(name) }
        let base = trimmed.hasSuffix(extensionSuffix)
            ? String(trimmed.dropLast(extensionSuffix.count))
            : trimmed
        guard base.range(of: "^[A-Za-z0-9_\\- ]+$", options: .regularExpression) != nil else {
            throw FileBrowserError.invalidFileName(name)
        }
        return base
    }

    static func withExtension(_ base: String) -> String { base + extensionSuffix }
}

class FileBrowserViewModel: ObservableObject {
    @Published var files: [SchemeFile] = []

    private var directory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let dir = docs.appendingPathComponent("schemes")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func refresh() {
        let fm = FileManager.default
        guard let items = try? fm.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: .skipsHiddenFiles
        ) else { return }

        // Every .scm file stays visible; non-UTF-8 content decodes lossily
        // (U+FFFD) instead of dropping the file from the list. `content` is a
        // best-effort snapshot — an unreadable file lists with empty content,
        // so the load path must re-read through the throwing readFile(path:)
        // and never trust this field for editing.
        files = items
            .filter { $0.pathExtension == "scm" }
            .map { url -> SchemeFile in
                let data = (try? Data(contentsOf: url)) ?? Data()
                let date = (try? fm.attributesOfItem(atPath: url.path))
                    .flatMap { $0[.modificationDate] as? Date }
                    ?? Date(timeIntervalSince1970: 0)
                return SchemeFile(
                    name: url.deletingPathExtension().lastPathComponent,
                    path: url.path,
                    content: String(decoding: data, as: UTF8.self),
                    lastModified: date
                )
            }
            .sorted { $0.lastModified > $1.lastModified }
    }

    /// Throws on invalid names or write failures — it never fabricates success.
    @discardableResult
    func saveFile(name: String, content: String) throws -> SchemeFile {
        let base = try SchemeFileNames.sanitize(name)
        let safeName = SchemeFileNames.withExtension(base)
        let url = directory.appendingPathComponent(safeName)
        do {
            try content.write(to: url, atomically: true, encoding: .utf8)
        } catch {
            throw FileBrowserError.writeFailed(name: base)
        }
        let file = SchemeFile(
            name: base,
            path: url.path,
            content: content,
            lastModified: Date()
        )
        refresh()
        return file
    }

    /// True when a file with this (sanitized) base name already exists, so the
    /// new-file flow can confirm before overwriting. Checks the disk, not the
    /// in-memory list: this view model instance can be separate from the one
    /// other views save through, so `files` may be stale.
    func fileExists(name: String) throws -> Bool {
        let base = try SchemeFileNames.sanitize(name)
        let url = directory.appendingPathComponent(SchemeFileNames.withExtension(base))
        return FileManager.default.fileExists(atPath: url.path)
    }

    func deleteFile(path: String) {
        try? FileManager.default.removeItem(atPath: path)
        refresh()
    }

    /// Throws instead of returning "" so a failed read can never lead to the
    /// real content being overwritten with empty text.
    func readFile(path: String) throws -> String {
        guard let data = FileManager.default.contents(atPath: path) else {
            throw FileBrowserError.readFailed(path: path)
        }
        return String(decoding: data, as: UTF8.self)
    }
}
