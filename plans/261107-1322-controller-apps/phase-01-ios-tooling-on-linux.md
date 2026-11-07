# Phase 01 — iOS tooling on Linux (with the user)

**Priority:** blocks phase 05. **Status:** todo.
Several steps need the user's hands (sudo, Apple ID login + 2FA, browser download, taps on each iPhone/iPad). Ask for each in one message with the exact command; the user runs sudo/interactive ones as `! <command>`.

**Files:** none in the repo. Record findings in `plans/261107-1322-controller-apps/reports/ios-tooling-261107-report.md`.

**Interfaces:** Produces for phase 05: working `swift`, `xtool` (logged in, SDK installed), each household iOS device's UDID + iOS version, and whether xtool sees devices over Wi-Fi.

- [ ] **Step 1: usbmuxd + libimobiledevice tools.** Check `usbmuxd --help` and `ideviceinfo --help`. Missing on this machine (CachyOS): user runs `! sudo pacman -S usbmuxd libimobiledevice`. Expected: both print usage.
- [ ] **Step 2: Swift 6.4 toolchain.** Install via swiftly (`https://swift.org/install/linux`) without sudo if possible. Verify: `swift --version` → `Swift version 6.4`.
- [ ] **Step 3: xtool.** Download `xtool-$(uname -m).AppImage` from the latest release to `~/.local/bin/xtool`, `chmod +x`. Verify: `xtool --help` lists `dev`, `install`, `devices`.
- [ ] **Step 4: Xcode 27.1 `.xip`.** The user downloads it in a browser from `https://developer.apple.com/download/all/?q=Xcode` (Apple ID login). Note the path.
- [ ] **Step 5: `xtool setup`.** The user runs `! xtool setup`, picks login mode **1 (Password)** — free Apple ID — and gives the `.xip` path. Verify: `xtool sdk` reports an installed SDK.
- [ ] **Step 6: Each iOS device (every household iPhone + the iPad mini).** Plug in by USB, tap **Trust**, then record `idevice_id -l` UDID and `ideviceinfo -u <udid> -k ProductVersion`. Ask the user how many sideloaded apps each device already has: a free account allows **3 at once**, so Spieltisch may need a slot freed.
- [ ] **Step 7: Deployment target decision.** If every device is on iOS ≥ 16, phase 05 uses iOS 16 + `DataScannerViewController`. If any device is below 16, write that in the report: phase 05 then targets iOS 15 and scans with `AVCaptureMetadataOutput`.
- [ ] **Step 8: Wi-Fi reach.** Unplug one device (same Wi-Fi) and run `xtool devices`. Record whether it is listed with a network connection type — this decides whether the weekly refresh needs a cable.
- [ ] **Step 9: Commit the report** (`docs(plans): iOS tooling on Linux findings`).

**Success:** report lists tool versions, every device's UDID/iOS version/free app slots, the deployment target, and USB-only vs Wi-Fi refresh.

**Risk:** xtool password login uses private Apple APIs; xtool's docs suggest a throwaway Apple ID — mention it once, the user decides.
