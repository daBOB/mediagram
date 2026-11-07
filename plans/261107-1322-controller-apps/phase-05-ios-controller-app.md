# Phase 05 — iOS controller app + weekly refresh

**Priority:** core deliverable. **Status:** todo. Needs phase 01's report (deployment target, UDIDs, Wi-Fi reach).

**Files:**
- Create: `ios/controller/ControllerCore/` — its own SwiftPM package (Foundation only, so `swift test` runs on Linux): `Package.swift`, `Sources/ControllerCore/LanURL.swift`, `Sources/ControllerCore/ControllerScreen.swift`, `Tests/ControllerCoreTests/LanURLTests.swift`, `Tests/ControllerCoreTests/ControllerScreenTests.swift`
- Create: `ios/controller/` — the xtool app package, generated with `xtool new Spieltisch` then edited: `Package.swift` (depends on `ControllerCore` by local path), `xtool.yml` (`bundleID: com.mediagram.controller`, `infoPath: Info.plist`, `iconPath`), `Info.plist`, `Sources/Spieltisch/SpieltischApp.swift`, `ContentView.swift`, `BoxFinder.swift`, `GameWebView.swift`, `Buzzer.swift`, `QRScanner.swift`
- Create: `scripts/refresh-ios-controllers.sh`

**Interfaces:**
- Consumes: plan.md § Contracts; phase 01 report.
- Produces: `func lanGameURL(_ raw: String) -> String?`; `func sameBox(_ a: String, _ b: String) -> Bool`; `enum ControllerScreen: Equatable { case searching(notice: String?), playing(url: String) }`; `enum ControllerEvent { case found(String), scanned(String), pageFailed(String) }`; `func startScreen(remembered: String?) -> ControllerScreen`; `extension ControllerScreen { func after(_ e: ControllerEvent) -> ControllerScreen }`.

### Task 5.1: ControllerCore (tested on Linux)

- [ ] **Step 1: Write the failing tests** — the same tables as phase 04's `LanGameUrlTest` and `ControllerScreenTest`, same values, same notice text `"Das ist nicht der Code vom Fernseher."`, same named cases (`testBoxMovedSinceLastNight`, `testTvRestartedGameNight`, `testForeignQrCode`).
- [ ] **Step 2: Run** `cd ios/controller/ControllerCore && swift test` → FAIL.
- [ ] **Step 3: Implement** `LanURL.swift` (parse with `URLComponents`; IPv4 ranges by octets) and `ControllerScreen.swift`.
- [ ] **Step 4: Run** → PASS.

### Task 5.2: The app

- [ ] **Step 1: Info.plist** — `CFBundleDisplayName` Spieltisch; `CFBundleShortVersionString`/`CFBundleVersion` = the project version; `NSLocalNetworkUsageDescription` ("Spieltisch sucht den Fernseher im WLAN."); `NSBonjourServices` [`_mediagram-game._tcp`]; `NSCameraUsageDescription` ("Zum Scannen des QR-Codes vom Fernseher."); `NSAppTransportSecurity` → `NSAllowsLocalNetworking` true; iPhone portrait + landscape, iPad all four orientations; `UIDeviceFamily` iPhone + iPad. Deployment target per phase 01 (iOS 16, or 15).
- [ ] **Step 2: `BoxFinder`** — `NWBrowser(for: .bonjourWithTXTRecord(type: "_mediagram-game._tcp", domain: nil), using: .tcp)`; read TXT `url` from each result's metadata, pass through `lanGameURL`, publish found URLs (an `AsyncStream<String>` or `@Published`); restartable.
- [ ] **Step 3: `GameWebView`** — `UIViewRepresentable` over `WKWebView`: `scrollView.isScrollEnabled = false`, `bounces = false`, no zoom (`minimumZoomScale = maximumZoomScale = 1`); `WKUserScript` at `.atDocumentStart`, `forMainFrameOnly: true`, defining the bridge with the exact contract:
  ```js
  window.mediagramApp = {
    _m: /*MEMORY_JSON*/,
    vibrate(k) { webkit.messageHandlers.mediagramApp.postMessage({ vibrate: k }) },
    getMemory() { return JSON.stringify(this._m) },
    setMemory(k, v) { if (v === null) delete this._m[k]; else this._m[k] = v;
      webkit.messageHandlers.mediagramApp.postMessage({ setMemory: k, value: v }) },
  };
  ```
  where `/*MEMORY_JSON*/` is the `UserDefaults` memory dictionary serialized with `JSONSerialization` at page load. The message handler stores memory and calls `Buzzer`. Navigation delegate: allow only `sameBox(current, target)`; `didFailProvisionalNavigation`/`didFail` on the main frame → `.pageFailed(url)`. `UIApplication.shared.isIdleTimerDisabled = true` while the app is active; `.defersSystemGestures(on: .all)` and `.persistentSystemOverlays(.hidden)` on the playing view so edge swipes need a second swipe instead of stealing strokes.
- [ ] **Step 4: `Buzzer`** — UIKit feedback generators, a distinct feel per kind (your call); iPad has no motor: calls just do nothing.
- [ ] **Step 5: `QRScanner` + `ContentView`** — `DataScannerViewController` with `.barcode(symbologies: [.qr])` (or `AVCaptureMetadataOutput` if phase 01 chose iOS 15) → `.scanned(raw)`. `ContentView` drives `ControllerScreen` exactly like phase 04's activity: start from remembered `box.url`, finder running while searching and playing, same German waiting screen copy and button "QR-Code scannen".
- [ ] **Step 6: Build** `cd ios/controller && xtool dev build` → succeeds.
- [ ] **Step 7: Device walk** on one iPhone and the iPad mini (`xtool dev run --udid <udid>`; first launch: Settings → General → VPN & Device Management → Trust): the same walk as phase 04 step 5 (local network permission prompt appears once — allow it); on the iPad mini additionally the drawing check with an Apple Pencil: the throwaway canvas page logs `pointerType "pen"` and varying `pressure`, strokes never become text (Scribble), finger and pencil both draw.

### Task 5.3: Weekly refresh

- [ ] **Step 1: `scripts/refresh-ios-controllers.sh`** — builds once (`xtool dev build --ipa`), then installs on every connected device (`idevice_id -l`, plus network devices if phase 01 found Wi-Fi reach) with `xtool install --udid <udid> <ipa>` (check `xtool install --help` for the signing behaviour; it must re-sign so the 7 days restart). Prints per device "aktualisiert" or the error, exits non-zero if any failed. Also writes the unsigned `.ipa` path for Sideloadly.
- [ ] **Step 2: Verify** with one device: run the script, then `ideviceinstaller -u <udid> -l | grep com.mediagram.controller` (or the app opening) — and note the new expiry if xtool prints it.
- [ ] **Step 3: Commit** `ios/`, the script; bump patch; `feat(ios): Spieltisch controller app for iPhone and iPad, built on Linux with xtool`.

**Success:** `swift test` passes on Linux; app installed on an iPhone and the iPad mini passes the walk; one command refreshes every device.
