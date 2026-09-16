---
phase: 8
title: "Docs and operating notes"
status: pending
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
- [ ] Someone can run the player from the document alone
- [ ] The architecture document describes every index consumer, and explains
      why Telegram is spoken in two languages
- [ ] The codec policy is written down with its reasoning
- [ ] Docs stay within the 800-line guidance per file

## Risk Assessment
- Documentation drift. Keep the codec table in one place and have the code
  read from it rather than restating it in prose.
