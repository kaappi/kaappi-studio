import SwiftUI

struct ContentView: View {
    @EnvironmentObject var settingsVM: SettingsViewModel
    @StateObject private var editorVM = EditorViewModel()
    @StateObject private var fileBrowserVM = FileBrowserViewModel()
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            // The save error propagates to EditorView, which presents it and
            // only then updates the current file name — a failed save never
            // fabricates success.
            EditorView(editorVM: editorVM, onSave: { name, content in
                    try fileBrowserVM.saveFile(name: name, content: content)
                })
                .tabItem {
                    Label("Editor", systemImage: "chevron.left.forwardslash.chevron.right")
                }
                .tag(0)

            ExamplesView(onSelect: { code in
                editorVM.loadCode(code)
                editorVM.currentFileName = nil
                selectedTab = 0
            })
                .tabItem {
                    Label("Examples", systemImage: "book")
                }
                .tag(1)

            FileBrowserView(onFileLoad: { name, content in
                editorVM.loadCode(content)
                editorVM.currentFileName = name
                selectedTab = 0
            })
                .tabItem {
                    Label("Files", systemImage: "folder")
                }
                .tag(2)

            SettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gear")
                }
                .tag(3)
        }
        .preferredColorScheme(settingsVM.colorScheme)
        .tint(Color(red: 0.31, green: 0.77, blue: 0.70))
    }
}
