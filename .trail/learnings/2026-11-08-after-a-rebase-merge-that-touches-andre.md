---
title: "After a rebase/merge that touches crates/mediagram-core, rebuild the Android .so before…"
author: Andre
date: 2026-11-08
id: l-7cc81ae6
scope: repo
status: active
touches:
  - scripts/build-android-core.sh
---

## Decided
`android/core/rust/src/main/jniLibs/*/libmediagram_core.so` are gitignored build output. The gradle
`verifyNativeCore` guard only checks they *exist*, not that they match the committed Kotlin bindings.

2026-09-26: after rebasing the TV branch onto main (which added active sessions), the TV box build
crashed at every launch with `UnsatisfiedLinkError ... uniffi_mediagram_core_fn_method_core_revoke_session`.

**Why:** new bindings + old .so; builds and unit tests pass, the APK dies on device.

## Watch out
after any rebase/merge/pull that changes `crates/mediagram-core`, run
`ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/build-android-core.sh` before
`installDebug`/`installBenchmark`. Verify a new symbol with the NDK's `llvm-nm -D`. Tightening the guard
(fail when .so is older than the bindings) was offered to the user, not done. See tv-box-gets-the-benchmark-build.
…

_From Claude Code's memory of this repo, last updated Sep 26._
