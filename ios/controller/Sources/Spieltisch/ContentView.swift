import ControllerCore
import SwiftUI
import UIKit
import VisionKit

private let boxURLKey = "box.url"
private let noScanner = "Der QR-Scanner ist auf diesem Gerät nicht verfügbar."

/// The waiting screen until the TV's game night is found, then the box's phone page.
struct ContentView: View {
    @State private var screen = startScreen(remembered: UserDefaults.standard.string(forKey: boxURLKey))
    @State private var scanning = false
    @StateObject private var finder = BoxFinder()
    @Environment(\.scenePhase) private var phase

    private var searching: Bool {
        if case .searching = screen { return true }
        return false
    }

    var body: some View {
        Group {
            switch screen {
            case .playing(let url):
                GameWebView(url: url, onFailed: { on(.pageFailed($0)) })
                    .ignoresSafeArea()
                    // edge swipes need a second swipe instead of stealing a stroke
                    .defersSystemGestures(on: .all)
                    .persistentSystemOverlays(.hidden)
            case .searching(let notice):
                WaitingView(notice: notice, onScan: scan)
            }
        }
        .statusBarHidden(true)
        .sheet(isPresented: $scanning) {
            QRScanner { raw in
                scanning = false
                on(.scanned(raw))
            }
            .ignoresSafeArea()
        }
        .onReceive(finder.$found.compactMap { $0 }) { on(.found($0)) }
        // Falling back to waiting starts discovery afresh, so a box still announced is found again.
        .onChange(of: searching) { _ in finder.restart() }
        .onChange(of: phase) { phase in awake(phase == .active) }
        .onAppear { awake(true) }
    }

    private func on(_ event: ControllerEvent) {
        let next = screen.after(event)
        guard next != screen else { return }
        screen = next
        if case .playing(let url) = next { UserDefaults.standard.set(url, forKey: boxURLKey) }
    }

    private func scan() {
        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
            scanning = true
        } else {
            screen = .searching(notice: noScanner)
        }
    }

    /// The screen stays on and the Wi-Fi is watched only while the app is in front.
    private func awake(_ active: Bool) {
        UIApplication.shared.isIdleTimerDisabled = active
        if active { finder.start() } else { finder.stop() }
    }
}

// The game night page's felt table, so the app and the page look like one thing.
private let felt = Color(red: 0x0F / 255, green: 0x5A / 255, blue: 0x43 / 255)
private let card = Color(red: 0xFB / 255, green: 0xF8 / 255, blue: 0xF1 / 255)
private let muted = Color(red: 0xA9 / 255, green: 0xCD / 255, blue: 0xBF / 255)
private let ink = Color(red: 0x0C / 255, green: 0x2A / 255, blue: 0x22 / 255)
private let yellow = Color(red: 0xF6 / 255, green: 0xC7 / 255, blue: 0x31 / 255)

private struct WaitingView: View {
    let notice: String?
    let onScan: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Text("Spieltisch").font(.system(size: 40, weight: .heavy)).foregroundColor(card)
            Text("Warte auf den Fernseher …").font(.title2).foregroundColor(card).padding(.top, 24)
            Text("Starte auf dem Fernseher den Spieleabend.").font(.body).foregroundColor(muted).padding(.top, 8)
            if let notice {
                Text(notice).font(.body).foregroundColor(yellow).padding(.top, 24)
            }
            Button(action: onScan) {
                Text("QR-Code scannen").font(.headline).foregroundColor(ink)
                    .padding(.horizontal, 28).padding(.vertical, 14)
                    .background(yellow, in: Capsule())
            }
            .padding(.top, 40)
        }
        .multilineTextAlignment(.center)
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(felt.ignoresSafeArea())
    }
}
