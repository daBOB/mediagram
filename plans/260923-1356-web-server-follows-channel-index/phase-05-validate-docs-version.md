# Phase 05 — Validate live, docs, version

Priority: medium · Status: todo

## Validation
- Offline stub (no Telegram): install from a local fixture snapshot, swap,
  SSE reaches a browser page, redraw deferred while a title plays.
- Live: the user's player picks up the next real upload from the other machine
  with the page untouched. Measure pin → page time.
- Never pin in probes (memory: pins are flood-limited).

## Docs
- `docs/system-architecture.md`: the web player now follows the channel; drop
  the "deliberate difference" wording; describe SSE.
- `docs/running-the-player.md`: catalog origins and precedence.
- changelog entry.

## Version
Minor bump in all three manifests.
