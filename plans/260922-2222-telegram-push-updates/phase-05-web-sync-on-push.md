# Phase 05 — Web: sync on push

## Context links
- `web/src/index.ts:200-203` (`syncOnce`, `syncTimer`), `web/src/telegram/client.ts`, 02's `updates.ts`

## Overview
Priority P2. Status: **done 2026-09-22** (uncommitted). `web/src/telegram/channel-events.ts` (adapter + debounced listener), wired in `web/src/index.ts` before the start round; `web/test/channel-events.test.ts` (10). Real check: an unpinned `#mlib-state` send+edit from the uploader key produced exactly one `state` event on the web key, 5.0 s after the first write (window), edit folded in. The web player calls `syncOnce("push")` on `StateChanged`.

## Requirements
- Subscribe explicitly: teleproto must have made a request (`getMe`, as the spike probe did) before updates flow.
  Phase 03 found a fresh grammers connection that made no request stayed silent; check the same on teleproto.
- Raw update handler registered on the live client only when sync is enabled (`MEDIAGRAM_SYNC_STATE`).
- `StateChanged` → debounced `syncOnce("push")`; `IndexChanged` → log only, until the Settings plan's
  channel-index reload exists (see plan key decisions).
- If the Settings plan's swappable client (its phase 02) has landed, register the handler inside the holder so a
  client swap re-registers it. If not, register once at startup.

## Success criteria
`bun test` for the handler with a fake client (echo ignored, debounce holds). Stub-harness only for UI.
Real check in 06: an Android position change reaches the web Continue shelf in under ~10 s.
