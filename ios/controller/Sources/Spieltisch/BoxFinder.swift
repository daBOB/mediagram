import ControllerCore
import Network
import SwiftUI

/// Join URLs of game nights the TV announces on the Wi-Fi (`_mediagram-game._tcp`, TXT `url`).
@MainActor
final class BoxFinder: ObservableObject {
    @Published private(set) var found: String?
    private var browser: NWBrowser?

    func start() {
        guard browser == nil else { return }
        let browser = NWBrowser(for: .bonjourWithTXTRecord(type: "_mediagram-game._tcp", domain: nil), using: .tcp)
        browser.browseResultsChangedHandler = { [weak self] results, _ in
            let urls = results.compactMap { result -> String? in
                // Throwaway: only the dev server, never another box on the Wi-Fi.
                guard case .service(let name, _, _, _) = result.endpoint, name == "Spieltisch dev" else { return nil }
                guard case .bonjour(let txt) = result.metadata, let url = txt["url"] else { return nil }
                return lanGameURL(url)
            }
            guard let url = urls.first else { return }
            Task { @MainActor in self?.found = url }
        }
        browser.start(queue: .main)
        self.browser = browser
    }

    func stop() {
        browser?.cancel()
        browser = nil
    }

    /// Forget what was found and look again, so the same box is reported anew.
    func restart() {
        stop()
        found = nil
        start()
    }
}
