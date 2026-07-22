import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var settingsVM: SettingsViewModel

    var body: some View {
        NavigationStack {
            Form {
                Section("Appearance") {
                    Picker("Theme", selection: $settingsVM.themeMode) {
                        ForEach(ThemeMode.allCases, id: \.self) { mode in
                            Text(mode.rawValue).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section("Editor") {
                    HStack {
                        Text("Font size: \(settingsVM.fontSize)px")
                        Spacer()
                        Stepper("", value: $settingsVM.fontSize, in: 10...24)
                            .labelsHidden()
                    }
                }

                Section("About") {
                    LabeledContent("Version", value: "1.0.0")
                    LabeledContent("Engine", value: "Kaappi v0.21.0 (WASM)")
                    Text("A learning environment for the Scheme programming language.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text("Free and open source (MIT License)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
