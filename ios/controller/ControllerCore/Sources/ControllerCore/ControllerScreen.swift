/// What the app shows: the waiting screen, or the box's phone page.
public enum ControllerScreen: Equatable, Sendable {
    case searching(notice: String?)
    case playing(url: String)
}

public enum ControllerEvent: Sendable {
    /// The TV announced game night at this URL.
    case found(String)
    /// The camera read this text from a QR code.
    case scanned(String)
    /// The page at this box URL could not be loaded.
    case pageFailed(String)
}

public let notTheBox = "Das ist nicht der Code vom Fernseher."

/// Last night's box is tried straight away; discovery corrects it if the box has moved.
public func startScreen(remembered: String?) -> ControllerScreen {
    remembered.flatMap(lanGameURL).map { .playing(url: $0) } ?? .searching(notice: nil)
}

extension ControllerScreen {
    public func after(_ event: ControllerEvent) -> ControllerScreen {
        switch event {
        case .found(let url):
            return lanGameURL(url).map { .playing(url: $0) } ?? self
        case .scanned(let raw):
            let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            return lanGameURL(text).map { .playing(url: $0) } ?? .searching(notice: notTheBox)
        case .pageFailed(let url):
            // A failure of a box we already left behind changes nothing.
            return self == .playing(url: url) ? .searching(notice: nil) : self
        }
    }
}
