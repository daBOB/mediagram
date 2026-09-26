# Phase 06 — Web Settings page

## Context links
- `web/public/index.html:46` (`nav-system`, hidden until `/api/status` answers)
- `web/public/app.js:495-503` (`offerSystem`: HEAD probe), `:517-522` (`viewSystem`), `:524-560` (`route`)
- `web/public/lib/status-view.js`, `lib/status-lines.js:17` (`ofBudget`, `humanSize`), `lib/dom.js`
- Memory: player aesthetic (editorial catalogue) is a user decision — reuse, do not restyle
- Memory: verify UI with a stub harness, never the real player

## Overview
Priority P2. Status: **done 2026-09-26**. `#/settings`: locked form → Telegram section → Cache
section, in the existing System-page idiom.

Built as four files, not three: `settings-telegram.js`'s account rows, sign-in stepper and
sign-out/library/app-form entry points did not fit this project's 200-line ceiling together
with the three secondary forms (library picker, app id/hash, sign-out confirm), so those three
moved to `settings-telegram-forms.js`. Stub-harness walkthrough done (locked → wrong token →
unlock → each Telegram/cache action → sign-out confirm → sign-in through the code step); the
password/2FA step was not exercised live — `computeCheck`'s SRP math needs a real
`account.Password` answer this stub cannot fabricate — and is instead covered by
`settings-sign-in.test.ts`. The walkthrough caught two real bugs, fixed in this phase's commit:
`signOutTelegram()` sent no body and so no `content-type`, which its own route's same-origin
guard then refused with 415; and the sign-out `<dialog>` rendered pinned top-left rather than
centered (needed explicit `position: fixed; inset: 0; margin: auto` — this project's other
overlays are non-`<dialog>` elements, so there was no existing centered-dialog rule to copy).
Also fixed in the same pass: `AdminGate.lock`'s clearing cookie always carried `Secure`
regardless of the caller's flag, found while wiring `routes.ts`'s `/lock` handler to it.

## Key insights
- `offerSystem` already shows how to advertise a gated page without leaking it: HEAD probe,
  hidden on 404. Settings answers 404 off-network and 401 when locked → link shown on 200/401.
- `app.js` is 632 lines; new logic goes into `lib/` modules, `app.js` gains only a route + probe.
- Sign-in is three short steps; each is one field + one button — same shape as Android's `LoginScreen`.
- Password and code inputs: `autocomplete="one-time-code"` / `current-password`, never stored in JS beyond the submit.

## Requirements
- F: nav link "Settings" (class `apart`, beside System), hidden unless probe is 200/401.
- F: Locked state: token field + Unlock; error "That token does not match" (no count leak).
- F: Telegram section (read-only rows): Account (name, @username), Library (title), Datacenter (DC n),
  Session (live / signed in, not connected / signed out). Never shows phone, hash, session.
- F: Actions: Change library (list → choose → progress "Reading the channel's index…" → result sets count);
  Application id/hash (id prefilled, hash field empty with "set" placeholder; Save → "Reconnecting…");
  Sign out (confirm dialog naming the consequence: uncached titles stop playing until signed in);
  Sign in (when signed out): phone → code (says where the code went, `viaApp`) → password if asked → library if asked.
- F: Cache section: "Held X of Y" (reuse `ofBudget`), size input with G/M units (parse like `parseSize`), Save → "Freeing space…" → new figures; min shown.
- F: Lock button (ends admin session).
- NF: each lib file < 200 lines; no new colours/fonts; copy in the player's plain voice.

## Architecture
`app.js route('settings')` → `viewSettings(main)` (`lib/settings-view.js`) → `settings-api.js`
(fetch JSON, same-origin, `content-type: application/json`) → renders sections from
`GET /api/settings`; each action re-fetches the view on success.

## Related code files
Create: `web/public/lib/settings-view.js` (page + sections), `web/public/lib/settings-telegram.js`
(library picker, app creds form, sign-in steps, sign-out confirm), `web/public/lib/settings-api.js`
(request wrapper + size parse/format — pure, testable), `web/test/settings-api.test.ts` (size parse/format, error mapping).
Modify: `web/public/app.js` (route + probe, ~15 lines), `web/public/index.html` (nav link),
`web/public/style.css` (only if an existing status-panel/form class cannot be reused; keep tiny).
Delete: none.

## Implementation steps
1. `settings-api.js` pure helpers + tests.
2. `settings-view.js`: locked form, read rows, cache form; mount/unmount like `watchStatus`.
3. `settings-telegram.js`: library list, creds form, sign-in stepper, sign-out `<dialog>`.
4. Wire route + probe in `app.js`; nav link in `index.html`.
5. Stub harness in scratchpad: real `startServer` + settings router over fakes (fake holder that
   answers `account()`, fake sign-in that accepts code `12345` / password `pw`, fake libraries
   list of 3, real `ChunkCache` in tmp with a few MB of chunks). Start detached per memory note;
   stop with `fuser -k -n tcp <port>`.
6. Walk with gstack `/browse`: locked → wrong token → unlock → each action → lock. Screenshots incl. `<dialog>` via `screenshot --viewport`.

## Todo
- [ ] api helpers + tests
- [ ] settings view
- [ ] telegram actions UI
- [ ] app.js/index.html wiring
- [ ] stub harness walkthrough + screenshots

## Success criteria
- Stub walkthrough completes every flow; screenshots attached to the phase report.
- Off-network stub request (spoofed client) → link hidden, `#/settings` shows the same 404 message System shows.
- Visual check: page indistinguishable in type/colour/spacing from System.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| Aesthetic drift | M×M | reuse status-panel markup/classes; review screenshots side by side with System |
| Stuck mid sign-in after reload | M×L | server holds step; view asks `GET /api/settings` which reports `signIn.step` |
| Starting the real player to test | L×H | stub only (memory rule); real run is the user's call in 09 |

## Security
Token/password/code fields cleared after submit; no `localStorage` of anything secret; cookie is HttpOnly so JS never sees it.

## Next steps
09 docs + versions.
