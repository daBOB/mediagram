# Phase 5 — Verify, document, release

## Overview

Priority: last. Proof, then the paperwork the repo asks for.

## Requirements

- The page seen in a browser against `stub-offline.ts`, not only in tests.
- `scripts/check.sh` green: clippy, cargo test, bun test, gradle lint.
- Docs updated; version bumped in all three manifests; plan review written.

## Implementation steps

1. Start the offline stub detached on a spare port. Never the real player —
   it holds the account's MTProto auth key, and one key cannot serve two
   clients.
2. Set a profile in storage, reload, screenshot `#/home`, and check: five
   rows, a resume line under a part-watched show, "next up" under a
   finished one, no title twice, every See all link arriving at its shelf.
3. `scripts/check.sh`.
4. `docs/system-architecture.md`: the landing route and the two payload
   changes. `docs/project-changelog.md`: a dated entry.
5. Minor bump — a new feature, backwards compatible for the viewer — in the
   workspace `Cargo.toml`, `web/package.json` and the Android manifest's
   `versionName`. Not `versionCode`, which counts builds.
6. Fill in the plan's Review section with what actually happened.

## Todo

- [ ] stub + browser verification
- [ ] `scripts/check.sh`
- [ ] docs
- [ ] version bump
- [ ] plan review

## Success criteria

A screenshot of the page and a green check run. Nothing claimed that was
not seen.

## Risks

The stub loads `web/src` once: after editing a route, restart it or the
change looks not to have worked.

## Security

The stub's upstream fetcher throws, so nothing can reach Telegram by
accident. Do not start the real player to look at a page.
