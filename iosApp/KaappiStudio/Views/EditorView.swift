import SwiftUI
import WebKit

struct EditorView: View {
    @EnvironmentObject var settingsVM: SettingsViewModel
    @ObservedObject var editorVM: EditorViewModel
    let onSave: (String, String) throws -> SchemeFile
    @State private var showSaveDialog = false
    @State private var saveFileName = ""
    @State private var saveError: String?

    private var isDark: Bool {
        switch settingsVM.themeMode {
        case .dark: return true
        case .light: return false
        case .system:
            return UITraitCollection.current.userInterfaceStyle == .dark
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                SchemeWebView(
                    isDark: isDark,
                    fontSize: settingsVM.fontSize,
                    onWebViewReady: { editorVM.webView = $0 },
                    editorViewModel: editorVM
                )
                .frame(maxHeight: .infinity)

                Divider()

                ScrollView {
                    if editorVM.hasResult {
                        VStack(alignment: .leading, spacing: 0) {
                            if !editorVM.lastStdout.isEmpty {
                                Text(editorVM.lastStdout)
                                    .font(.system(.body, design: .monospaced))
                                    .foregroundColor(.primary)
                            }
                            if !editorVM.lastStderr.isEmpty {
                                Text(editorVM.lastStderr)
                                    .font(.system(.body, design: .monospaced))
                                    .foregroundColor(.red)
                            }
                            Text("\n(\(String(format: "%.1f", editorVM.lastElapsed)) ms)")
                                .font(.system(.caption, design: .monospaced))
                                .foregroundColor(.secondary)
                                .italic()
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(8)
                        .textSelection(.enabled)
                    } else {
                        Text("Output will appear here")
                            .font(.system(.body, design: .monospaced))
                            .foregroundColor(.secondary)
                            .italic()
                            .padding(8)
                    }
                }
                .frame(height: 200)
                .background(isDark ? Color(white: 0.08) : Color(white: 0.96))
            }
            .navigationTitle(editorVM.currentFileName.map { "\($0).scm" } ?? "Editor")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    Button {
                        saveFileName = editorVM.currentFileName ?? ""
                        showSaveDialog = true
                    } label: {
                        Image(systemName: "square.and.arrow.down")
                    }
                    .disabled(!editorVM.isReady)

                    Button {
                        editorVM.runCode()
                    } label: {
                        if editorVM.isRunning {
                            ProgressView()
                        } else {
                            Image(systemName: "play.fill")
                        }
                    }
                    .disabled(!editorVM.isReady || editorVM.isRunning)
                }
            }
            .alert("Save File", isPresented: $showSaveDialog) {
                TextField("File name", text: $saveFileName)
                Button("Save") {
                    if !saveFileName.isEmpty {
                        editorVM.webView?.evaluateJavaScript("window.kaappiAPI?.getCode()") { result, _ in
                            let code = result as? String ?? ""
                            Task { @MainActor in
                                do {
                                    let saved = try onSave(saveFileName, code)
                                    // Only a successful save names the file.
                                    editorVM.currentFileName = saved.name
                                } catch {
                                    saveError = (error as? LocalizedError)?.errorDescription
                                        ?? error.localizedDescription
                                }
                            }
                        }
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Enter a name for your Scheme file (.scm)")
            }
            .alert("Save Failed", isPresented: Binding(
                get: { saveError != nil },
                set: { if !$0 { saveError = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(saveError ?? "")
            }
        }
    }
}
