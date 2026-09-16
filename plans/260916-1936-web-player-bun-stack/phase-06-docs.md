---
phase: 6
title: "Docs and operating notes"
status: pending
priority: P3
effort: "0.5d"
dependencies: [5]
---

# Phase 6: Docs and operating notes

## Overview
Write down how the player works and how to run it, and fold the new client
into the architecture document rather than leaving it as a second, undescribed
system.

## Requirements
- Functional: a reader can run both processes, expose them safely, and
  understand why transcoding exists.
- Non-functional: no example that is not generated or verified.

## Related Code Files
- Create: `docs/running-the-player.md`
- Modify: `docs/system-architecture.md` (add the playback path),
  `docs/development-roadmap.md`, `docs/project-changelog.md`, `README.md`

## Implementation Steps
1. `docs/running-the-player.md`: the two processes, config keys, the proxy
   setup, cache sizing, and what to check when playback stalls.
2. Architecture: add playback beside upload and export. State plainly that
   `serve` is a third read-only consumer of the index, alongside `push-index`
   and `export-package`.
3. Record the codec policy: which profiles direct-play, which transcode, and
   why browsers refuse the rest. This is the question a future reader will ask
   first.
4. Roadmap and changelog entries.
5. Note what the player deliberately does not do: one viewer, no history, no
   accounts.

## Success Criteria
- [ ] Someone can run the player from the document alone
- [ ] The architecture document describes three index consumers, not two
- [ ] The codec policy is written down with its reasoning
- [ ] Docs stay within the 800-line guidance per file

## Risk Assessment
- Documentation drift. Keep the codec table in one place and have the code
  read from it rather than restating it in prose.
