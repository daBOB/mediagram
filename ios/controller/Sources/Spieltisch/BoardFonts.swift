import CoreText
import SwiftUI
import UIKit

/// Bricolage Grotesque (display) and Atkinson Hyperlegible (text), bundled as TTFs. Where registration fails
/// the board falls back to the system font, so a missing file never blocks a game.
enum BoardFonts {
    private static let displayFamily = "Bricolage Grotesque"
    private static let weightAxis = 0x7767_6874 // 'wght'

    static func register() {
        let urls = ["BricolageGrotesque", "AtkinsonHyperlegible-Regular", "AtkinsonHyperlegible-Bold"]
            .compactMap { Bundle.module.url(forResource: $0, withExtension: "ttf", subdirectory: "Fonts")
                ?? Bundle.module.url(forResource: $0, withExtension: "ttf") }
        guard !urls.isEmpty else { return }
        for url in urls { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
    }

    /// The display face at a weight on its variable axis; the system's heavy rounded face without it.
    static func display(_ size: CGFloat, weight: CGFloat = 800) -> Font {
        guard !UIFont.fontNames(forFamilyName: displayFamily).isEmpty else {
            return .system(size: size, weight: weight >= 700 ? .heavy : .semibold, design: .rounded)
        }
        let descriptor = UIFontDescriptor(fontAttributes: [
            .family: displayFamily,
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [weightAxis: weight],
        ])
        return Font(UIFont(descriptor: descriptor, size: size))
    }

    static func text(_ size: CGFloat, bold: Bool = false) -> Font {
        let name = bold ? "AtkinsonHyperlegible-Bold" : "AtkinsonHyperlegible-Regular"
        return UIFont(name: name, size: size) == nil
            ? .system(size: size, weight: bold ? .bold : .regular) : .custom(name, size: size)
    }
}
