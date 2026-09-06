import SwiftUI

struct FileBrowserView: View {
    @StateObject private var viewModel = FileBrowserViewModel()
    @State private var showNewFileAlert = false
    @State private var newFileName = ""
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
                                onFileLoad(file.name, file.content)
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
                            // Capture paths up front: deleteFile() refreshes the
                            // list, so indexing viewModel.files inside the loop
                            // would re-index a mutating array and delete the
                            // wrong files on a multi-row delete.
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
                    if !newFileName.isEmpty {
                        let file = viewModel.saveFile(name: newFileName, content: "")
                        onFileLoad(file.name, "")
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Enter a name for your Scheme file (.scm)")
            }
            .onAppear { viewModel.refresh() }
        }
    }
}
