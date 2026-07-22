import Foundation

struct SchemeFile: Identifiable {
    var id: String { path }
    let name: String
    let path: String
    var content: String
    let lastModified: Date
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

        files = items
            .filter { $0.pathExtension == "scm" }
            .compactMap { url -> SchemeFile? in
                guard let content = try? String(contentsOf: url, encoding: .utf8),
                      let attrs = try? fm.attributesOfItem(atPath: url.path),
                      let date = attrs[.modificationDate] as? Date else { return nil }
                return SchemeFile(
                    name: url.deletingPathExtension().lastPathComponent,
                    path: url.path,
                    content: content,
                    lastModified: date
                )
            }
            .sorted { $0.lastModified > $1.lastModified }
    }

    func saveFile(name: String, content: String) -> SchemeFile {
        let safeName = name.hasSuffix(".scm") ? name : "\(name).scm"
        let url = directory.appendingPathComponent(safeName)
        try? content.write(to: url, atomically: true, encoding: .utf8)
        let file = SchemeFile(
            name: String(safeName.dropLast(4)),
            path: url.path,
            content: content,
            lastModified: Date()
        )
        refresh()
        return file
    }

    func deleteFile(path: String) {
        try? FileManager.default.removeItem(atPath: path)
        refresh()
    }

    func readFile(path: String) -> String {
        (try? String(contentsOfFile: path, encoding: .utf8)) ?? ""
    }
}
