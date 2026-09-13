import SwiftUI
import shared

/// Main-actor front for the shared Kotlin `SettingsRepository`.
///
/// Storage keys, the theme fallback and the font-size range/clamping are the
/// repository's contract (`SettingsRepository.kt`); this class only mirrors
/// the values into published properties for SwiftUI.
@MainActor
class SettingsViewModel: ObservableObject {
    /// Font sizes the settings contract accepts, shared with Android.
    nonisolated static let fontSizeRange = Int(SettingsRepositoryKt.MIN_FONT_SIZE)...Int(SettingsRepositoryKt.MAX_FONT_SIZE)

    @Published var themeMode: ThemeMode {
        didSet { repository.setThemeMode(mode: themeMode) }
    }
    @Published var fontSize: Int {
        // The repository clamps into `fontSizeRange` before storing.
        didSet { repository.setFontSize(size: Int32(fontSize)) }
    }

    private let repository: SettingsRepository

    init(repository: SettingsRepository = SettingsRepository()) {
        self.repository = repository
        themeMode = repository.getThemeMode()
        fontSize = Int(repository.getFontSize())
    }

    var colorScheme: ColorScheme? {
        if themeMode == ThemeMode.light { return .light }
        if themeMode == ThemeMode.dark { return .dark }
        return nil
    }
}
