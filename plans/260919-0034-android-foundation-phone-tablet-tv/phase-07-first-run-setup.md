# Phase 7: First-run setup, in the app

**Context:** [plan.md](plan.md) · [spec](../../docs/superpowers/specs/2026-09-19-android-foundation-design.md)

## Overview

- **Priority:** Blocking. Without this the app cannot be used at all.
- **Status:** Not started.
- **Deliverable:** a guided first run that takes a freshly installed APK to a working library, with nothing typed on a developer's machine.

## Why this exists

The app currently dead-ends. `MobileApp.kt:126` renders a text block —
*"Missing Telegram API credentials. Add MEDIAGRAM_API_ID and
MEDIAGRAM_API_HASH to local.properties, then rebuild"* — and gates the entire
authenticated tree behind it. The login and settings screens are built and
tested, and unreachable.

That was a deliberate decision: bake the Telegram application credentials at
build time. It is being reversed. Two reasons.

**A television.** Editing a properties file and rebuilding is not a thing
anyone does to set up a TV app, and the TV surface is a stated goal of this
round.

**A sideloaded APK should set itself up.** Every device otherwise needs a
developer's checkout, and the credentials end up baked into an artifact rather
than living on the device that uses them.

## The architectural consequence

`Core::new(data_dir, api_id, api_hash)` takes its credentials as constructor
arguments, so *sourcing* them elsewhere is small. What is not small: the core
can no longer be constructed at injection time, because the credentials are
read from encrypted storage asynchronously and may not exist at all on a first
run.

So the app has two states — **not yet set up**, where no `Core` exists, and
**running**, where one does. Construction is deferred, the same shape
`PlaybackModule` already uses for `ExoPlayer`: a `Deferred`/holder resolved
once credentials are present, with the UI showing setup until then.

Do not fabricate a `Core` with empty credentials to keep the graph simple. A
half-built core that fails on first use is exactly the class of defect this
round has been fixing.

## The flow

Three steps, resumable, each skippable if its data is already stored:

1. **Telegram application** — `api_id` and `api_hash`, from my.telegram.org.
   Say where they come from; a person setting this up on a tablet cannot guess.
2. **Sign in** — phone → code → 2FA. `LoginScreen` already does this, with
   per-step retry that preserves the typed value on a wrong code. Reuse it.
3. **Library** — package URL and key. `SettingsScreen` already does this.

Then the catalog. On a later launch with everything stored, none of these
appear.

## Storage

`EncryptedPackageSettings` already holds the package URL and key through
`EncryptedSharedPreferences` with a Keystore master key, and an instrumented
test proves the key is not readable in plain preferences. The Telegram
credentials belong in the same store, under the same protection.

`api_hash` is a secret and gets `KeyboardType.Password` and masking, exactly as
the package key does. `api_id` is a number and is not secret.

## Also close a known gap

Nothing clears the session today. If Telegram invalidates the key — the
account signs out elsewhere — `isAuthorized()` stays true forever with no path
back to signing in. A setup flow needs a way to start over anyway, so add one:
clear the stored session and credentials and return to step 1. It must warn
before it acts, and it must actually delete `session.key` rather than only
forgetting it.

## What to remove

- The `MEDIAGRAM_API_ID` / `MEDIAGRAM_API_HASH` `buildConfigField` wiring in
  `AndroidApplicationConventionPlugin`, and the `local.properties` read behind
  it.
- `MissingCredentialsMessage` and the gate around it.
- Any documentation telling a reader to write `local.properties`.

Leave `local.properties` itself alone — Gradle uses it for the SDK path.

## Related code files

- Create: a setup feature (ViewModel + UiState in `feature/`, screens in `ui-mobile/`), a credentials store beside `PackageSettings`
- Modify: `android/core/data/**`, `android/app/**` DI, `android/ui-mobile/MobileApp.kt`, `android/build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`
- Do not touch: `crates/**`, `android/ui-tv/**`

## Success criteria

1. A clean checkout with **no `local.properties` credentials** compiles, and
   `./gradlew lint` still passes.
2. Installing the APK on a device with cleared data shows step 1, not an error.
3. The three steps can be completed in order, each rejecting obviously invalid
   input before it is stored.
4. Killing and relaunching mid-setup resumes where it left off rather than
   starting again.
5. With everything stored, a relaunch goes straight to the catalog.
6. Starting over clears the session and credentials and returns to step 1.
7. No secret is logged, and none appears in an exception message.

## Risk assessment

| Risk | Mitigation |
|---|---|
| A half-built `Core` with empty credentials | Forbidden above. The core exists or it does not. |
| Setup state and auth state disagree — e.g. credentials stored but the session was invalidated remotely | Derive what to show from what is actually true, not from a remembered step counter. |
| The reset path deletes the session but leaves the catalog, or vice versa | Decide what reset means, write it down, and test it. |
| `api_hash` leaks into a log or error | Same rule as the package key; assert it in a test rather than trusting review. |

## Next steps

The TV surface renders this same state later; keep the setup logic in
`feature/` with no composables, as `feature:catalog` does, so `ui-tv` can
reuse it without duplication.
