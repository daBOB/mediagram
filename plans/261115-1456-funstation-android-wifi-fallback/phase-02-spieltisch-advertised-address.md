# Phase 2: Spieltisch shows the address the host gives it

Priority: high. Status: done; released in Spieltisch 0.192.4, QR on funstation shows 192.168.0.42:8080.

## What
`lan_ip()` in `crates/mediagram-game/src/lan.rs` prefers the Android system property
`funstation.lan_address` when it holds a valid IP, read by running Android's `getprop` under
`cfg(target_os = "android")` (no new dependency). Elsewhere, and when unset, it stays the route lookup.

No UniFFI or Kotlin change: the join URL, the QR code and the DNS-SD TXT `url=` all come from it.

## Files
- Modify: `crates/mediagram-game/src/lan.rs` (+ a test for parsing: valid IP, empty, garbage)
- Versions: Spieltisch line (`crates/mediagram-game/Cargo.toml`, `crates/spieltisch-core/Cargo.toml`,
  client `package.json`, `android/spieltisch/build.gradle.kts`, `ios/controller/Info.plist`)

## Done when
- `cargo test -p mediagram-game` passes; Spieltisch core rebuilt for the Android ABIs.
- On funstation in Wi-Fi mode the QR shows `http://192.168.0.42:8080/`.
- Released to the channel above 0.192.2, since funstation's Spieltisch updates itself from there.

## Risks
- The property is read once per night start; a night already open when the mode switches is restarted
  anyway, because the switch restarts Android.
