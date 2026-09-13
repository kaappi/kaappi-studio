import Foundation
import shared

// Swift-side conveniences for the Kotlin models exported by the `shared`
// framework (shared/src/commonMain/.../domain). The models themselves are
// defined once, in Kotlin, and used as-is by both apps.

extension SchemeFile: @retroactive Identifiable {
    public var id: String { path }

    /// `lastModified` is epoch milliseconds on every platform (see the
    /// `FileRepository` contract); SwiftUI wants a `Date`.
    var modifiedDate: Date {
        Date(timeIntervalSince1970: TimeInterval(lastModified) / 1000)
    }
}

/// `Example.id` already exists on the Kotlin class, so the conformance needs
/// no members. (`description` is exported as `description_` because NSObject
/// owns the plain name.)
extension Example: @retroactive Identifiable {}

extension ThemeMode {
    /// Human-readable label for the settings picker ("Light", "Dark", "System").
    var label: String { name.capitalized }
}
