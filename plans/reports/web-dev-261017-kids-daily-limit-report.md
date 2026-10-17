# Web: kids daily watch limit (phase 1 web half + phase 2)

## Files
Server (`web/src/state/`): `roles-schema.ts` (v14: `daily_limit`, `daily_limit_updated_at`), `profiles.ts` (Profile.dailyLimit, row, insert), `profiles-write.ts` (new; PIN/age/daily writes moved out of profiles.ts for the line limit), `profiles-manage.ts` (`setDailyLimit`, `createKid(..., dailyLimit)`), `profiles-routes.ts` (routes + `leftToday`), `roles-record.ts`, `roles-merge.ts`, `roles-exchange.ts`, `watched-today.ts` (new), `store.ts` (`today()`), `routes.ts` (progress answer).
Browser (`web/public/`): `lib/daily-limit.js` + `.d.ts` (rule, words, choices; fixture-held), `lib/daily-limit-gate.js` (open gate, warning), `lib/daily-limit-choice.js` (Manage select), `lib/profile-manage.js`, `lib/profile-api.js`, `lib/watch-state.js` (`onToday`), `app.js` (gate in `openTitle`), `styles/catalog.css`.
Tests: new `daily-limit.test.ts`, `state-daily-limit.test.ts`; extended `profile-manage`, `state-migration` (v13->v14), `shared-watch-state-fixtures` (canonicalRoles), `state-http`, `state-roles-exchange`, `code-standards` (ceilings: store +6, profiles-manage 212, app.js 701, watch-state 485, dated note).

## API shapes (Android must match)
- `PUT /api/profiles/{id}/daily-limit` body `{actorId, pin, minutes}`; minutes whole 1..1440 or `null`; 204 / refusal as `kids-age` (same `set-kids-age` rule). Bad minutes -> 400 `invalid`.
- `POST /api/profiles` kid: extra optional `dailyLimit` (same validation; absent/null = none, dated 0 so unsaid on sync).
- `Profile` JSON gains `dailyLimit: number|null` (null on grown-ups).
- `GET /api/profiles/{id}/today` -> `{limitMinutes: number|null, watchedSeconds: number}`; 404 unknown profile; limitMinutes always null for a grown-up. watchedSeconds = SUM(stats_days.seconds) for server-local today, all devices.
- Progress PUT/POST `/api/profiles/{id}/progress/{set}`: still 204, EXCEPT a kid with a limit gets 200 with the same `{limitMinutes, watchedSeconds}` body.
- Sync key `dailyLimit {minutes|null, updatedAt}` exported only once dated (>0); kid-only; per fixtures. Schema v14.

## Browser behaviour
- Gate once in `app.js openTitle` (every open path: shelf, resume, Next up, next episode/button/autoplay, together join/move all reach it through `play`). Kids only: GET today before opening; out -> no open + notice. Server unreachable -> opens (household rule).
- Notice = the player's own `#note` line when the player is open, else a `p.error.daily-limit-notice` (fixed bottom-centre, role=status, gone after 10 s). No generic toast existed in the house; this reuses `.error` + `#note`.
- Warning once per opened title: from the progress answer (`watch-state.onToday`) or at open (after `freshResume` settles, so "Carrying on" cannot overwrite it).
- Manage profiles: "Daily limit" select beside age (existing kids) and in Add a kid (default No limit); odd stored values still show.

## Verified
`bun test` 3385 pass (3 fixtures incl.), typecheck 0 errors, lint clean. Preview harness (copy db, port 8817; seeded a parent+kid and stats_days only in the copy), gstack browse: 30 min limit + 600 s -> title opens, no warning; 1560 s -> warning at open and via progress answer, shown once (second tick did not re-show); 1900 s while playing -> playing title untouched, Next -> player stays on same title with out words in `#note`; player closed + out -> episode click opens nothing, page notice, same route. curl: set/clear limit, 400/403/404/405 paths. Screenshots: `kids-limit-web-next-refused.png`, `kids-limit-web-out-browse.png` here.

## Open questions
- No auto-hide of the warning in the player note (stays until next open). Fine?
- Out words as page notice sit fixed at the viewport bottom (full-page screenshot shows it mid-page; artifact).
- The worktree has no `node_modules`; I symlinked the main one for testing and removed it again.
