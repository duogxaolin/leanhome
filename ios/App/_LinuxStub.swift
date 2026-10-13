//
//  _LinuxStub.swift
//  App
//
//  Always-compiled placeholder so the `App` library target is non-empty on
//  non-Apple platforms (used only for CLI `swift build`/`swift test` parity
//  checks of the CoreDomain test vectors). On iOS/macOS the real
//  `LeanHomeApp.swift` provides the SwiftUI app entry point and this stub
//  contributes nothing beyond symbol presence.
//

import Foundation

public enum LeanHomeAppStub {
    /// Marker so the library product is non-empty on all platforms.
    public static let moduleIdentifier: String = "LeanHome"
}
