# Phase 01 — Spike: are the updates delivered?

## Context links
- grammers 0.10: `SenderPool::new` returns an updates receiver the core drops today
  (`crates/mediagram-core/src/api/session.rs:50`, `SenderPool { runner, handle, .. }`);
  `Client::stream_updates(updates, UpdatesConfiguration { catch_up, .. })`, `UpdateStream::next_raw`
- teleproto: `client.addEventHandler(handler, new Raw({}))`
- Precedent for measuring before trusting: `web/src/telegram/state-channel.ts:14-33` (search was assumed, measured, dropped)

## Overview
Priority P1 for this plan (it gates the rest). Status: **done 2026-09-22, GO** — see `reports/spike-260922-2240-update-delivery-report.md` and the clean redo `reports/spike-260922-2305-update-delivery-redo-report.md` (fair catch-up test, no pins). Throwaway probes, not product code.

## Questions to answer, with numbers
1. Does session B receive `updateEditChannelMessage` when session A (same account) edits a `#mlib-state` document? Latency?
2. `updateNewChannelMessage` for a new message; `updatePinnedChannelMessages` for a pin? Latency?
3. After B is offline 10 min, does `catch_up: true` (grammers) / teleproto's default catch-up deliver what was missed,
   or does it need an explicit `updates.getChannelDifference`?
4. Does A receive its own edit as an update (the echo case)?

## Implementation steps
1. `crates/mediagram-core/src/bin/probe_updates.rs` (not shipped; remove or keep `#[cfg]`-gated after): connect with an existing
   session key, stream raw updates, print type + channel id + message id + caption prefix + timestamp.
2. Same in `web/scripts/probe-updates.ts` with teleproto.
3. Drive it from the uploader (`mediagram push-index`) and a web player sync round. Record results in `reports/`.

## Success criteria
A report `reports/spike-260922-update-delivery-report.md` answering 1–4 for both libraries.
**Stop rule:** if 1 or 2 fail on either library, mark the plan `blocked` with the evidence.

## Risks
Updates for a channel may only flow after the session has "opened" it (`channels.getChannelDifference` once).
If so, 03 makes that call at startup. The spike finds out.

## Security
Probes print no captions beyond their marker prefix and no session material.
