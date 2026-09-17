---
phase: 8
title: "Docs and operating notes"
status: completed
priority: P3
effort: "0.5d"
dependencies: [7]
---

# Phase 8: Docs and operating notes

## Overview
Write down how the player works and how to run it, and fold the new client
into the architecture document rather than leaving it as a second, undescribed
system.

## Requirements
- Functional: a reader can run the player — on the uploader's machine or
  anywhere else — expose it safely, and understand why transcoding exists.
- Non-functional: no example that is not generated or verified.

## Related Code Files
- Create: `docs/running-the-player.md`
- Modify: `docs/system-architecture.md` (add the playback path),
  `docs/development-roadmap.md`, `docs/project-changelog.md`, `README.md`

## Implementation Steps
1. `docs/running-the-player.md`: getting a session string onto the player
   host, pointing it at a published package, config keys, cache sizing, and
   what to check when playback stalls.
2. Architecture: add playback beside upload and export, and record that the
   player is a second MTProto implementation — the first thing a future reader
   will want explained. Say why: it runs where the uploader does not.
3. Record the codec policy: which profiles direct-play, which transcode, and
   why browsers refuse the rest. This is the question a future reader will ask
   first.
4. Roadmap and changelog entries.
5. Note what the player deliberately does not do: one viewer, no history, no
   accounts.
6. Write down the auth-key exposure plainly, and the dedicated-account option
   for hosts the reader does not control.

## Success Criteria
- [x] Someone can run the player from the document alone — issuing a session,
      starting it, the settings, what converts and why, putting it behind a
      proxy, and what to check when it stalls
- [x] The architecture document describes every index consumer, and explains
      why Telegram is spoken in two languages — §7, with the module map and
      the consumer table
- [x] The codec policy is written down with its reasoning
- [x] Docs stay within the 800-line guidance per file — the largest is 450

## How drift is prevented

The risk section's answer was "keep the codec table in one place and have the
code read from it". The code already had it in one place; what was missing was
anything that notices when the prose stops matching. So the three sets in
`web/public/lib/playable.js` are exported, and `web/test/codec-policy-doc.test.ts`
reads the table out of the rendered markdown and compares. It fails on drift
rather than rotting quietly.

The same problem in a form no test can catch — a list of files that grows —
was removed rather than maintained: the architecture document now states the
grammers confinement as the negative that has to hold, with the `grep` that
checks it, instead of enumerating the Telegram-facing files.

## What the accuracy pass found

An independent check of every claim against the source found seven. Six were
documentation errors, fixed here. The seventh was a defect the documentation
had surfaced: `MEDIAGRAM_LIBRARY_DB` was required even when a package supplied
the catalog, so a player with no uploader filesystem to read still refused to
start without a path to a file it would never open. `web/src/config.ts` now
requires it only when no package is configured, with tests for both.

## Risk Assessment
- Documentation drift. Keep the codec table in one place and have the code
  read from it rather than restating it in prose.
