# Phase 2 — Web: route, sender, player sheet, error capture

**Context:** spec § Where the viewer finds it, § How it is sent, § Collecting the technical detail. Patterns: `web/src/together/routes.ts` (validation, 503), `web/src/telegram/together-channel.ts` + `web/test/together-channel-plain-text.test.ts` (fake client), `web/test/together-routes.test.ts` (router harness), `web/public/lib/playback/notes/player-notes.js` (a player panel's lifetime). Consumes phase 1 (`reportText`, `REPORT_REASONS`, `REPORT_WORDS`, `REPORT_DELAY_SECONDS`).

**Status:** pending.

### Task 2.1: Sender

**Files:** Create `web/src/telegram/report-sender.ts`; Test `web/test/report-sender.test.ts`.

**Produces:** `interface ReportSender { available(): boolean; send(text: string): Promise<void> }` (exported from `report-sender.ts`) and `class TelegramReportSender implements ReportSender` (`constructor(connection: TelegramConnection, now = () => Date.now())`).

- [ ] **Step 1: Failing test** (same fake-client shape as `together-channel-plain-text.test.ts`):

```ts
/** A report goes to the account itself, scheduled, as plain text, and not silent: a scheduled message in Saved Messages is the only own message Telegram notifies about. */
import { expect, test } from "bun:test";
import { Telegram } from "../src/telegram/client";
import { TelegramConnection } from "../src/telegram/connection";
import { TelegramReportSender } from "../src/telegram/report-sender";

test("a report is scheduled a minute ahead into Saved Messages", async () => {
  const asked: unknown[][] = [];
  const client = { connected: true, sendMessage: async (peer: unknown, params: unknown) => { asked.push([peer, params]); return { id: 1 }; } };
  const telegram = Telegram.withChannel({ client } as unknown as Telegram, -1001234567890, 1n);
  const sender = new TelegramReportSender(new TelegramConnection(telegram), () => 1_700_000_000_500);
  await sender.send("#mlib-report\n__x__");
  expect(asked).toEqual([["me", { message: "#mlib-report\n__x__", parseMode: false, schedule: 1_700_000_060 }]]);
});
```

- [ ] **Step 2: Run** `cd web && bun test test/report-sender.test.ts` → FAIL.
- [ ] **Step 3: Implement**: `available()` exactly as `TelegramTogetherChannel.available`; `send` awaits `connection.ready()` (throw `Error("Telegram isn't reachable")` on null) then `client.sendMessage("me", { message: text, parseMode: false, schedule: Math.floor(now() / 1000) + REPORT_DELAY_SECONDS })`. No `silent`. Import `REPORT_DELAY_SECONDS` from `../../public/lib/report/report-text.js`.
- [ ] **Step 4: Run** → PASS. **Step 5: Commit** `feat(web): send a report as a reminder to Saved Messages`.

### Task 2.2: Route `POST /api/report`

**Files:** Create `web/src/report/routes.ts`; Modify `web/src/routes.ts` (option + dispatch before the GET-only gate, beside `togetherRoute`), `web/src/index.ts` (`report: new TelegramReportSender(connection)` in the `createServer` options next to `together`); Test `web/test/report-routes.test.ts`.

**Produces:** `reportRoute(request: PlayerRequest, sender: ReportSender | undefined, db: Database, state: WatchState | undefined): Promise<PlayerResponse | null>`; `RouterOptions.report?: ReportSender`.

Body: `{set, reason, title, note?, position?, played?, held?, error?, profileId?}`. Rules:
- path ≠ `/api/report` → `null`; no sender → 404; method ≠ POST → 405
- 400 unless: `set` is a string and `playableSet(db, set) !== null`; `reason` ∈ `REPORT_REASONS` ids; `title` a string of 1–300 chars; `note` absent/null or a string ≤ 2000; `position` absent/null or finite ≥ 0; `played` absent/null or one of `direct|converted|copied`; `held` absent or boolean; `error` absent/null or string ≤ 300; `profileId` absent/null or string
- profile name: `state?.profiles().find((p) => p.id === profileId)?.name ?? null`
- `!sender.available()` → 503 `{reason: "Telegram isn't reachable"}`; `send` throws → log `report: send failed` with the error, 503
- success → 204. Text: `reportText({...validated, screen: "Browser", device: null, app: \`Web ${version}\`})`, `version` read once from `web/package.json` (`import pkg from "../../package.json"`).

- [ ] **Step 1: Failing tests** (harness copied from `together-routes.test.ts`, with a `FakeSender {available = true; sent: string[] = []; fail = false; async send(t) { if (this.fail) throw new Error("x"); this.sent.push(t); }}`):
  - valid body → 204 and `sent[0]` equals `reportText` of the same facts with `screen: "Browser"`, `app` `Web <package version>`
  - unknown set → 400, nothing sent
  - unknown reason → 400; `note` of 2001 chars → 400; `position: "NaN"` / `-1` → 400; `played: "magic"` → 400
  - `available = false` → 503, nothing sent; `fail = true` → 503
  - GET → 405; no sender option → 404
- [ ] **Step 2: Run** `bun test test/report-routes.test.ts` → FAIL.
- [ ] **Step 3: Implement** the route and wiring.
- [ ] **Step 4: Run** → PASS; run the whole `bun test` (the code-standards line ceilings may need a dated raise for `routes.ts`/`index.ts` — raise only with a dated note, as before).
- [ ] **Step 5: Commit** `feat(web): POST /api/report checks a report and sends it`.

### Task 2.3: Player sheet + error capture

**Files:** Modify `web/public/index.html` (top bar: `<button id="report" class="ghost">Report</button>` between `#notes` and `#close`; inside the player `<dialog>`, after `#notes-panel`: `<aside class="report" id="report-panel" hidden aria-labelledby="report-heading">` holding a heading `#report-heading`, a radio group `#report-reasons`, `<input id="report-note" maxlength="200">`, `#report-said` (status line), buttons `#report-send` / `#report-cancel`); Create `web/public/lib/playback/player-report.js`; Modify `web/public/lib/playback/player.js`, `web/public/styles/playback.css`.

**Produces:** `mountPlayerReport({ facts }): { open(set), clear(), failed(error: string) }` where `facts()` answers `{position, played, held}` from the player; `failed` records the last error.

Behaviour:
- Reasons and words rendered from `REPORT_REASONS` / `REPORT_WORDS` (no literal strings in the module); none checked on open; *Send* disabled until one is.
- `open(set)`: forget the last error, button back to *Report*, enabled; panel hidden.
- Send: disable *Send*, POST `/api/report` with `{set: set.setId, reason, title: titleLine(set), note, position, played, held, error: lastError, profileId: state.profileId()}`, `AbortSignal.timeout(8000)`. 204 → panel hidden, button text *Reported*, `disabled`. Anything else / throw → `#report-said` = `REPORT_WORDS.failed`, *Send* enabled again, panel stays.
- *Cancel* and Escape close the panel without sending (Escape must not also close the player dialog: `preventDefault` on the dialog's `cancel` while the panel is open — check how notes/menus handle it in `player.js` and reuse that).
- `played` from `playbackMeta(...).mode`: `direct` → `direct`, `transcode` → `converted`, `copy`/`hevc-copy` → `copied`.
- player.js: `const report = mountPlayerReport({ facts: () => ({ position: filmTime(), played: …, held }) })`; `report.open(set)` beside `marks.open(set)`; `report.failed(...)` at the conversion-stopped (`:254`), conversion-start (`:271`) and play-refused (`:430`) note sites with the same words; add `video.addEventListener("error", …)` recording `MEDIA_ERR_ABORTED|NETWORK|DECODE|SRC_NOT_SUPPORTED` by `video.error.code` plus `: ${video.error.message}` when non-empty — ignored while `source === null` (the deliberate `removeAttribute("src")` in `stop()` fires one); `report.clear()` where the player closes. Keep player.js growth to the few wiring lines.
- CSS: a centred small card over the picture (`position: absolute`, the paper colour opaque — the daily-limit notice taught that a tint over pictures is unreadable), focus ring on radios/buttons as the player's `.ghost` buttons have.

- [ ] **Step 1:** Implement the module and wiring.
- [ ] **Step 2:** Check in the preview harness, never the real player: `cd web && PREVIEW_PORT=8790 MEDIAGRAM_STATE_DB=<copy of the live state.db> bun run preview`, drive with gstack browse (`$HOME/.claude/skills/gstack/browse/di""st/browse`, via the scratchpad `br.sh` wrapper). The preview has no Telegram, so *Send* must show *Couldn't send. Try again later.* (503) — that is the failure path verified. Also: the reasons render, *Send* stays disabled until a reason is picked, Escape closes only the sheet, the button is back to *Report* on the next title. Screenshot to `plans/261017-1747-report-broken-video/reports/web-report-sheet.png`.
- [ ] **Step 3:** `bun test` + typecheck green.
- [ ] **Step 4: Commit** `feat(web): Report in the player, with the last error attached`.

**Success criteria:** route tests green; sheet verified in the preview; no literal report words outside `report-text.js`.
