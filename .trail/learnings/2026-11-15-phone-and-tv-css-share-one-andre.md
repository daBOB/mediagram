---
title: "Phone and TV CSS share one page, and the question history is shared by every game"
author: Andre
date: 2026-11-15
branch: feat/letztes-wort
cost: { minutes: 1, tokens: 2428 }
id: l-3ad51f8a
scope: repo
status: active
src: agent
---

## Watch out
- Every phone and TV stylesheet is loaded on every page, so a class name used on both sides collides: the phone host desk banner `.lw-floor` turned the whole TV red because the TV section took `lw-${stage}` = `lw-floor`. Keep phone and TV class names distinct.
- The question history `used` in `factory.rs` is one set shared by every game. A game that starts its own pack over must drop only its own key prefix (`used.retain(|k| !k.starts_with(PREFIX) || ...)`), or every other game repeats its questions that night.

In the code: crates/mediagram-game/src/letztes_wort_config.rs (`letztes_wort_prompts`), crates/mediagram-game/client/src/phone/letztes-wort-phone.css (`.lw-desk-floor`).
