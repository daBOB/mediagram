---
title: Page code must not wait for a paint the browser-application stub can never deliver
author: Andre
date: 2026-11-08
branch: feat/web-loading-progress
id: l-e3156525
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - docs/project-changelog.md
  - web/package.json
  - web/public/lib/startup-progress.js
  - web/public/lib/artwork-progress.js
  - web/test/support/browser-application.ts
  - web/test/code-standards.test.ts
---

## Decided
- A step in `web/public` that awaits a painted frame (here `startup-progress.js`'s `painted()`) only waits when its words actually reached the page (`show()` returned true). In `test/browser-application.test.ts` the fake `document.getElementById` returns bare stub nodes with no `isConnected` and no `querySelector`, and the stub's timers and `requestAnimationFrame` never fire, so an unconditional wait hung every startup test until timeout (67 tests, 311 s, all failing).
- New page modules find their elements by id (`loading-state`, `loading-words` in `index.html`) and keep references to elements they create, instead of `element.querySelector`, which the stub's base `Node` lacks.

## Watch out
- A full `bun test` that suddenly takes minutes instead of ~17 s is this hang, not slowness.
- Files already over the code-standards line ceiling (`app.js`, `library-session.js`, `shell.css`) may not grow at all; new styles go in their own stylesheet (`progress-bars.css`).

In the code: web/public/lib/startup-progress.js, web/public/lib/artwork-progress.js, web/test/support/browser-application.ts, web/test/code-standards.test.ts
