import SwiftUI

/// The native board over the page while the page asks for one.
struct BoardOverlay: View {
    @ObservedObject var model: BoardModel

    var body: some View {
        Color(red: 0x0F / 255, green: 0x5A / 255, blue: 0x43 / 255).ignoresSafeArea()
    }
}
