import SwiftUI

enum ThemeMode: String, CaseIterable {
    case light = "Light"
    case dark = "Dark"
    case system = "System"
}

// Font-size bounds of the settings contract (shared SettingsRepository.kt,
// MIN_FONT_SIZE/MAX_FONT_SIZE/DEFAULT_FONT_SIZE) — keep the two in sync.
private let fontSizeRange = 10...24
private let defaultFontSize = 14

class SettingsViewModel: ObservableObject {
    @Published var themeMode: ThemeMode {
        didSet { UserDefaults.standard.set(themeMode.rawValue, forKey: "theme_mode") }
    }
    @Published var fontSize: Int {
        didSet {
            // Contract: clamp into the valid range so nothing invalid is stored.
            UserDefaults.standard.set(
                min(max(fontSize, fontSizeRange.lowerBound), fontSizeRange.upperBound),
                forKey: "font_size")
        }
    }

    init() {
        let saved = UserDefaults.standard.string(forKey: "theme_mode") ?? "System"
        self.themeMode = ThemeMode(rawValue: saved) ?? .system
        let size = UserDefaults.standard.integer(forKey: "font_size")
        // Contract: only in-range stored values are trusted; anything else
        // (including a stored 0) falls back to the default.
        self.fontSize = fontSizeRange.contains(size) ? size : defaultFontSize
    }

    var colorScheme: ColorScheme? {
        switch themeMode {
        case .light: return .light
        case .dark: return .dark
        case .system: return nil
        }
    }
}
