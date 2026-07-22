import SwiftUI

enum ThemeMode: String, CaseIterable {
    case light = "Light"
    case dark = "Dark"
    case system = "System"
}

class SettingsViewModel: ObservableObject {
    @Published var themeMode: ThemeMode {
        didSet { UserDefaults.standard.set(themeMode.rawValue, forKey: "theme_mode") }
    }
    @Published var fontSize: Int {
        didSet { UserDefaults.standard.set(fontSize, forKey: "font_size") }
    }

    init() {
        let saved = UserDefaults.standard.string(forKey: "theme_mode") ?? "System"
        self.themeMode = ThemeMode(rawValue: saved) ?? .system
        let size = UserDefaults.standard.integer(forKey: "font_size")
        self.fontSize = size > 0 ? size : 14
    }

    var colorScheme: ColorScheme? {
        switch themeMode {
        case .light: return .light
        case .dark: return .dark
        case .system: return nil
        }
    }
}
