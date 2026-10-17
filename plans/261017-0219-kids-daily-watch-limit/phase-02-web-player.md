# Phase 2 — Web player

## Context
- Manage: `web/public/lib/profile-manage.js:159` (age select is the template), `profile-api.js:73`.
- Open: `app.js` `play()` `:285` → `openTitle` `:296`; `playback/player.js` `openPlayer` `:620`; Next episode `app.js:320`, `player.js:97`; watch together join `app.js:230`.
- Ticker: `player.js` `startSaving`/`saveTimer` `:497-503`; progress PUT `web/src/state/routes.ts:95-121`.

## Overview
Priority high, status pending (after phase 1). Parity reference for Android.

## Requirements
- Manage profiles: per kid, a "Daily limit" select beside the age limit — No limit, 30 min, 45 min, 1 h, 1 h 30, 2 h, 3 h; on create too (default No limit).
- Open gate in `openPlayer` (every path goes through it): fetch today's state for a kid with a limit; `out` → no player, a notice instead: "That's all the watching for today. See you tomorrow." (exact words shared with Android).
- Warning: the progress PUT answers with today's `{ watchedSeconds, limitMinutes }` for a limited kid; on entering `warn` show once per title: "Only a few minutes of watching left today. This one can finish." (from `daily-limit.json` `words`)
- At `out` during play: nothing stops; the Next episode autoplay and button are refused with the notice.
- Server stays the counter (stats already recorded there); the browser never counts on its own.

## Steps
1. API: `GET /api/profiles/{id}/today` (or a field on the existing profile listing — pick the smaller) + the PUT answer.
2. Manage select + create field.
3. Gate, warning toast, next-episode refusal — using the house notice/toast component.
4. Tests: `profile-manage.test.ts`, `player-features.test.ts`, `state-http.test.ts` additions.
5. Preview harness check (`cd web && bun run preview`, never the real player): a kid at 0, at 4 min left, out.

## Todo
- [ ] API answer
- [ ] Manage UI
- [ ] gate + warning + next-episode refusal
- [ ] tests + preview check

## Success criteria
In the preview: a limited kid with time left plays and sees the warning once; out of time, no title opens and the playing one finishes without autoplay.

## Risks
Several open paths (resume, Next up, together) — gate in `openPlayer` only, not per caller.
