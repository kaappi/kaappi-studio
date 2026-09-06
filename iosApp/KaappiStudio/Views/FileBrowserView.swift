import SwiftUI

struct FileBrowserView: View {
    @StateObject private var viewModel = FileBrowserViewModel()
    @State private var showNewFileAlert = false
    @State private var newFileName = ""
    @State private var overwriteTarget: String?
    @State private var errorMessage: String?
    @State private var loadErrorMessage: String?
    let onFileLoad: (String, String) -> Void

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.files.isEmpty {
                    VStack(spacing: 8) {
                        Text("No saved files yet")
                            .font(.body)
                        Text("Tap + to create a new file")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                } else {
                    List {
                        ForEach(viewModel.files) { file in
                            Button {
                                do {
                                    // Re-read through the throwing readFile:
                                    // the list's cached content is a
                                    // best-effort snapshot, and loading a
                                    // fabricated "" would let the next save
                                    // destroy the real file.
                                    let content = try viewModel.readFile(path: file.path)
                                    onFileLoad(file.name, content)
                                } catch {
                                    showLoadError(error)
                                }
                            } label: {
                                HStack {
                                    Image(systemName: "doc.text")
                                        .foregroundColor(.accentColor)
                                    VStack(alignment: .leading) {
                                        Text("\(file.name).scm")
                                            .font(.subheadline.weight(.medium))
                                            .foregroundColor(.primary)
                                        Text(file.lastModified, style: .date)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                }
                            }
                        }
                        .onDelete { indices in
                            // Capture the paths up front: deleteFile refreshes
                            // the list, so indexing into viewModel.files inside
                            // the loop would re-index a mutated array and delete
                            // the wrong files for a multi-row IndexSet.
                            let paths = indices.map { viewModel.files[$0].path }
                            for path in paths {
                                viewModel.deleteFile(path: path)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Files")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        newFileName = ""
                        showNewFileAlert = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .alert("New File", isPresented: $showNewFileAlert) {
                TextField("File name", text: $newFileName)
                Button("Create") {
                    // Presenting a follow-on alert ("Replace..."/"Could Not
                    // Create File") from inside this button action can be
                    // dropped by SwiftUI when it lands in the same transaction
                    // as the New File alert's dismissal; hop to the next
                    // main-actor turn first.
                    Task { @MainActor in
                        do {
                            let base = try SchemeFileNames.sanitize(newFileName)
                            if try viewModel.fileExists(name: base) {
                                // Confirm before overwriting an existing file
                                // with empty content (issue #10).
                                overwriteTarget = base
                            } else {
                                try createNewFile(base)
                            }
                        } catch {
                            showError(error)
                        }
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Enter a name for your Scheme file (.scm)")
            }
            .alert("Replace \"\(overwriteTarget ?? "").scm\"?", isPresented: Binding(
                get: { overwriteTarget != nil },
                set: { if !$0 { overwriteTarget = nil } }
            )) {
                Button("Overwrite", role: .destructive) {
                    guard let target = overwriteTarget else { return }
                    // Same transaction hazard as the Create button above: the
                    // failure alert must not be presented from inside this
                    // alert's dismissal.
                    Task { @MainActor in
                        do {
                            try createNewFile(target)
                        } catch {
                            showError(error)
                        }
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("A file with this name already exists. " +
                    "Creating a new file will overwrite it with empty content.")
            }
            .alert("Could Not Create File", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorMessage ?? "")
            }
            .alert("Could Not Open File", isPresented: Binding(
                get: { loadErrorMessage != nil },
                set: { if !$0 { loadErrorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(loadErrorMessage ?? "")
            }
            .onAppear { viewModel.refresh() }
        }
    }

    private func createNewFile(_ name: String) throws {
        let file = try viewModel.saveFile(name: name, content: "")
        onFileLoad(file.name, "")
    }

    private func showError(_ error: Error) {
        errorMessage = (error as? LocalizedError)?.errorDescription
            ?? error.localizedDescription
    }

    private func showLoadError(_ error: Error) {
        loadErrorMessage = (error as? LocalizedError)?.errorDescription
            ?? error.localizedDescription
    }
}
