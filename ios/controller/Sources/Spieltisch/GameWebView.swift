import ControllerCore
import SwiftUI
import WebKit

/// The box's phone page, as a surface to tap and draw on: no zoom, no scrolling or bounce,
/// and never anywhere but this box. `onFailed` gets the box URL when the page cannot load.
/// On an iPad the page can lay `board`, the native drawing board, over itself.
struct GameWebView: UIViewRepresentable {
    let url: String
    let board: BoardModel
    let onFailed: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let coordinator = context.coordinator
        coordinator.onFailed = onFailed
        coordinator.box = url
        coordinator.board = board
        let config = WKWebViewConfiguration()
        config.userContentController.add(WeakHandler(coordinator), name: "mediagramApp")
        coordinator.content = config.userContentController
        coordinator.installBridge()

        let view = WKWebView(frame: .zero, configuration: config)
        view.navigationDelegate = coordinator
        view.isOpaque = false
        view.backgroundColor = UIColor(red: 0x0F / 255, green: 0x5A / 255, blue: 0x43 / 255, alpha: 1)
        view.scrollView.isScrollEnabled = false
        view.scrollView.bounces = false
        view.scrollView.minimumZoomScale = 1
        view.scrollView.maximumZoomScale = 1
        view.scrollView.contentInsetAdjustmentBehavior = .never
        view.load(URLRequest(url: URL(string: url)!))
        board.send = { [weak view] act in
            guard let script = boardReceiveScript(for: act) else { return }
            // the page installs `mediagramBoard` only in board mode; the script checks for it
            view?.evaluateJavaScript(script, completionHandler: nil)
        }
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {
        let coordinator = context.coordinator
        coordinator.onFailed = onFailed
        // the box moved: follow it to its new address
        guard coordinator.box != url, let next = URL(string: url) else { return }
        coordinator.box = url
        view.load(URLRequest(url: next))
    }

    static func dismantleUIView(_ view: WKWebView, coordinator: Coordinator) {
        view.configuration.userContentController.removeScriptMessageHandler(forName: "mediagramApp")
        // a re-created page shows the board again itself; until then no stale board
        coordinator.board?.hide()
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var box = ""
        var onFailed: (String) -> Void = { _ in }
        var board: BoardModel?
        weak var content: WKUserContentController?
        private let buzzer = Buzzer()

        /// `window.mediagramApp` as the page expects it: the same three calls as on Android, and on an iPad the board.
        /// The page's memory is written into the script, so it is current for every page load.
        func installBridge() {
            guard let content else { return }
            content.removeAllUserScripts()
            let source = bridgeScript(memory: PageMemory.load(), board: UIDevice.current.userInterfaceIdiom == .pad)
            let script = WKUserScript(source: source, injectionTime: .atDocumentStart, forMainFrameOnly: true)
            content.addUserScript(script)
        }

        func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.frameInfo.isMainFrame, let body = message.body as? [String: Any] else { return }
            if let kind = body["vibrate"] as? String { buzzer.buzz(kind) }
            if let key = body["setMemory"] as? String {
                PageMemory.set(key, body["value"] as? String)
                installBridge()
            }
            if let json = body["showBoard"] as? String { showBoard(json) }
            if body["hideBoard"] != nil { board?.hide() }
        }

        private func showBoard(_ json: String) {
            do {
                board?.show(try JSONDecoder().decode(NativeBoardShow.self, from: Data(json.utf8)))
            } catch {
                // a screen this app does not know yet: the page stays as it is
                NSLog("Spieltisch: ignored a board show: %@", "\(error)")
            }
        }

        // a page that reloads shows the board again itself, with its drawing resynced
        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            board?.hide()
        }

        // a crashed page must not leave the board up on its last screen
        func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
            board?.hide()
        }

        // a link or redirect off the box would take the bridge with it
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            let target = action.request.url?.absoluteString ?? ""
            decisionHandler(sameBox(box, target) ? .allow : .cancel)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            failed(error)
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            failed(error)
        }

        private func failed(_ error: Error) {
            // a load we replaced or refused ourselves is not the box going away
            if (error as NSError).code == NSURLErrorCancelled { return }
            if (error as NSError).domain == "WebKitErrorDomain", (error as NSError).code == 102 { return }
            onFailed(box)
        }
    }
}

/// The page's memory (name, seat token, avatar), kept by the app rather than in the page's
/// localStorage, which belongs to the box's address and is lost when the router hands out a new one.
enum PageMemory {
    private static let key = "page.memory"

    static func load() -> [String: String] {
        UserDefaults.standard.dictionary(forKey: key) as? [String: String] ?? [:]
    }

    static func set(_ name: String, _ value: String?) {
        var memory = load()
        memory[name] = value
        UserDefaults.standard.set(memory, forKey: key)
    }
}

private func bridgeScript(memory: [String: String], board: Bool) -> String {
    let data = (try? JSONSerialization.data(withJSONObject: memory)) ?? Data("{}".utf8)
    let json = String(data: data, encoding: .utf8) ?? "{}"
    return """
    window.mediagramApp = {
      _m: \(json),
      vibrate(k) { webkit.messageHandlers.mediagramApp.postMessage({ vibrate: k }) },
      getMemory() { return JSON.stringify(this._m) },
      setMemory(k, v) {
        if (v === null) delete this._m[k]; else this._m[k] = v;
        webkit.messageHandlers.mediagramApp.postMessage({ setMemory: k, value: v })
      },
      board: \(board),
      showBoard(j) { webkit.messageHandlers.mediagramApp.postMessage({ showBoard: j }) },
      hideBoard() { webkit.messageHandlers.mediagramApp.postMessage({ hideBoard: true }) },
    };
    """
}

/// WKUserContentController holds its handlers strongly; this keeps the coordinator collectable.
private final class WeakHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?

    init(_ target: WKScriptMessageHandler) { self.target = target }

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(controller, didReceive: message)
    }
}
