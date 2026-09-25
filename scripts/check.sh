#!/usr/bin/env bash
#
# Everything that must be true before work leaves this machine.
#
# One script rather than a list inside a hook, so the same commands run from a
# terminal, from the pre-push hook, and from CI if this repo ever gains a
# remote. The whole thing takes a few seconds; there is no fast subset worth
# maintaining separately.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

step() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

step "clippy (warnings are errors)"
cargo clippy --all-targets --all-features -- -D warnings

step "cargo test"
cargo test --all

step "bun test"
# Skipped rather than failed when the player's dependencies are not installed:
# the uploader is usable without them, and an absent node_modules is a setup
# state, not a broken change.
if [ -d web/node_modules ]; then
  (cd web && bun run lint)
  (cd web && bun test)
else
  echo "skipping: web dependencies are not installed (run 'cd web && bun install')"
fi

step "gradle test and lint"
# Skipped rather than failed when there is no Android SDK: the uploader and
# the player are usable without one, and an absent SDK is a setup state, not
# a broken change. Neither task needs the cross-compiled native core, so this
# stays a Kotlin-only build with no Rust toolchain in it.
if [ -n "${ANDROID_HOME:-}" ]; then
  # compileDebugAndroidTestKotlin catches an instrumented test that does not
  # compile without needing a device connected — testDebugUnitTest and lint
  # alone never touch the androidTest source set at all.
  (cd android && ./gradlew testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin)
else
  echo "skipping: no Android SDK (set ANDROID_HOME to run the Android checks)"
fi

printf '\n\033[1;32m==> all checks passed\033[0m\n'
