import SwiftUI

@main
struct SpieltischApp: App {
    init() { BoardFonts.register() }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
