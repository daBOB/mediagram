# Phase 1 — Shared report text rule + fixtures

**Context:** spec § The message. Pattern to copy: `web/public/lib/daily-limit.js` + `.d.ts`, `web/test/daily-limit.test.ts`, `android/core/model/src/main/kotlin/DailyLimit.kt`, `android/core/model/src/test/kotlin/DailyLimitFixtureTest.kt` (its `locate()` finds the web fixture).

**Priority:** first; phases 2 and 4 consume it. **Status:** pending.

### Task 1.1: Fixture + web rule

**Files:**
- Create: `web/test/fixtures/report/report-text.json`
- Create: `web/public/lib/report/report-text.js`, `web/public/lib/report/report-text.d.ts`
- Test: `web/test/report-text.test.ts`

**Interfaces — Produces:**
- `REPORT_REASONS: {id: ReasonId, words: string}[]` with ids `wont-play`, `picture-sound`, `subtitles`, `wrong-title` (that order)
- `REPORT_WORDS: {button, sent, heading, note, send, cancel, failed}`
- `NOTE_MAX = 200`, `REPORT_DELAY_SECONDS = 60`
- `cleanNote(note: string | null | undefined): string`
- `reportText(facts: ReportFacts): string` where
  `ReportFacts = {reason: ReasonId, title: string, note: string|null, profile: string|null, screen: string, device: string|null, app: string, position: number|null, played: "direct"|"converted"|"copied"|null, held: boolean, error: string|null, setId: string}`

- [ ] **Step 1: Write the fixture** `web/test/fixtures/report/report-text.json`:

```json
{
  "reasons": [
    { "id": "wont-play", "words": "Won't play" },
    { "id": "picture-sound", "words": "Picture or sound wrong" },
    { "id": "subtitles", "words": "Subtitles wrong" },
    { "id": "wrong-title", "words": "Wrong title or poster" }
  ],
  "words": {
    "button": "Report",
    "sent": "Reported",
    "heading": "What's wrong?",
    "note": "Anything else? (optional)",
    "send": "Send",
    "cancel": "Cancel",
    "failed": "Couldn't send. Try again later."
  },
  "noteMax": 200,
  "delaySeconds": 60,
  "cases": [
    {
      "name": "a converted title on the web, with a note and an error",
      "facts": { "reason": "wont-play", "title": "Hot Shots! (1991)", "note": "stops right after the intro", "profile": "andre", "screen": "Browser", "device": null, "app": "Web 0.126.0", "position": 754, "played": "converted", "held": false, "error": "The conversion stopped: ffmpeg exited 1", "setId": "3f2a9c" },
      "text": "#mlib-report\nWon't play: Hot Shots! (1991)\n\"stops right after the intro\"\nandre · Browser · Web 0.126.0 · at 0:12:34\nPlayed: converted · Error: The conversion stopped: ffmpeg exited 1\nCheck: mediagram verify --full 3f2a9c"
    },
    {
      "name": "a kid on the TV, played from the cache, no note",
      "facts": { "reason": "picture-sound", "title": "Bluey · S1E3 · Hotel", "note": null, "profile": "TV kids", "screen": "TV", "device": "Hisense HTV", "app": "Android 0.126.0", "position": 65.9, "played": "direct", "held": true, "error": "ERROR_CODE_DECODER_INIT_FAILED", "setId": "b1" },
      "text": "#mlib-report\nPicture or sound wrong: Bluey · S1E3 · Hotel\nTV kids · TV (Hisense HTV) · Android 0.126.0 · at 0:01:05\nPlayed: direct, from cache · Error: ERROR_CODE_DECODER_INIT_FAILED\nCheck: mediagram verify --full b1"
    },
    {
      "name": "nothing known but the title: no profile, no position, a blank note, no playback line",
      "facts": { "reason": "wrong-title", "title": "Alien (1979)", "note": "   ", "profile": null, "screen": "Phone", "device": "Pixel 8", "app": "Android 0.126.0", "position": null, "played": null, "held": false, "error": null, "setId": "a1" },
      "text": "#mlib-report\nWrong title or poster: Alien (1979)\nPhone (Pixel 8) · Android 0.126.0\nCheck: mediagram verify --full a1"
    },
    {
      "name": "a note across lines is one line; past an hour",
      "facts": { "reason": "subtitles", "title": "Dune (2021)", "note": "  out of sync\n\tby two seconds  ", "profile": "test", "screen": "Tablet", "device": "Lenovo TB", "app": "Android 0.126.0", "position": 3725, "played": "direct", "held": false, "error": null, "setId": "d1" },
      "text": "#mlib-report\nSubtitles wrong: Dune (2021)\n\"out of sync by two seconds\"\ntest · Tablet (Lenovo TB) · Android 0.126.0 · at 1:02:05\nPlayed: direct\nCheck: mediagram verify --full d1"
    },
    {
      "name": "an error with nothing played; held alone says nothing",
      "facts": { "reason": "wont-play", "title": "Film", "note": "", "profile": "andre", "screen": "Browser", "device": null, "app": "Web 0.126.0", "position": 0, "played": null, "held": true, "error": "MEDIA_ERR_DECODE", "setId": "f1" },
      "text": "#mlib-report\nWon't play: Film\nandre · Browser · Web 0.126.0 · at 0:00:00\nError: MEDIA_ERR_DECODE\nCheck: mediagram verify --full f1"
    }
  ],
  "notes": [
    { "name": "cut to 200 and trimmed", "note": "<199 × a> bcd", "clean": "<199 × a>" }
  ]
}
```

Generate the `notes[0]` strings for real (not the `<…>` placeholders): `"a".repeat(199) + " bcd"` → clean `"a".repeat(199)` (cut at 200 leaves a trailing space, which is trimmed). Write them with a one-off `bun -e` that edits the JSON, then check the file by eye.

- [ ] **Step 2: Write the failing test** `web/test/report-text.test.ts`:

```ts
/** The report's words, held to `fixtures/report/report-text.json`, which Android's port reads too. */
import { expect, test } from "bun:test";
import fixture from "./fixtures/report/report-text.json";
import { NOTE_MAX, REPORT_DELAY_SECONDS, REPORT_REASONS, REPORT_WORDS, cleanNote, reportText } from "../public/lib/report/report-text.js";

test("every case reads as the fixture says", () => {
  for (const c of fixture.cases) expect(reportText(c.facts as never), c.name).toBe(c.text);
});
test("notes are cleaned as the fixture says", () => {
  for (const n of fixture.notes) expect(cleanNote(n.note), n.name).toBe(n.clean);
});
test("reasons, words and limits are the fixture's", () => {
  expect(REPORT_REASONS).toEqual(fixture.reasons);
  expect(REPORT_WORDS).toEqual(fixture.words);
  expect(NOTE_MAX).toBe(fixture.noteMax);
  expect(REPORT_DELAY_SECONDS).toBe(fixture.delaySeconds);
});
```

- [ ] **Step 3: Run** `cd web && bun test test/report-text.test.ts` → FAIL (module not found).

- [ ] **Step 4: Implement** `web/public/lib/report/report-text.js`:

```js
/**
 * The words a broken-video report reaches the library's keeper in: a
 * reminder in the account's own Saved Messages. Telegram notifies an
 * account of its own message only when it is a scheduled one there, so the
 * sender schedules it `REPORT_DELAY_SECONDS` ahead. The web is the
 * reference; Android's `ReportText.kt` is held to `report-text.json`.
 */

export const REPORT_REASONS = [
  { id: "wont-play", words: "Won't play" },
  { id: "picture-sound", words: "Picture or sound wrong" },
  { id: "subtitles", words: "Subtitles wrong" },
  { id: "wrong-title", words: "Wrong title or poster" },
];

export const REPORT_WORDS = {
  button: "Report",
  sent: "Reported",
  heading: "What's wrong?",
  note: "Anything else? (optional)",
  send: "Send",
  cancel: "Cancel",
  failed: "Couldn't send. Try again later.",
};

export const NOTE_MAX = 200;
export const REPORT_DELAY_SECONDS = 60;

/** `h:mm:ss`, the hour unpadded, as a player's clock reads past an hour. */
function clock(seconds) {
  const s = Math.floor(seconds);
  const two = (n) => String(n).padStart(2, "0");
  return `${Math.floor(s / 3600)}:${two(Math.floor((s % 3600) / 60))}:${two(s % 60)}`;
}

/** One line, at most `NOTE_MAX` characters; empty when nothing was said. */
export function cleanNote(note) {
  return (note ?? "").replace(/\s+/g, " ").trim().slice(0, NOTE_MAX).trimEnd();
}

export function reportText(f) {
  const reason = REPORT_REASONS.find((r) => r.id === f.reason)?.words ?? f.reason;
  const note = cleanNote(f.note);
  const who = [f.profile, f.device ? `${f.screen} (${f.device})` : f.screen, f.app, f.position == null ? null : `at ${clock(f.position)}`];
  const how = [f.played ? `Played: ${f.played}${f.held ? ", from cache" : ""}` : null, f.error ? `Error: ${f.error}` : null].filter(Boolean);
  return [
    "#mlib-report",
    `${reason}: ${f.title}`,
    note ? `"${note}"` : null,
    who.filter(Boolean).join(" · "),
    how.length > 0 ? how.join(" · ") : null,
    `Check: mediagram verify --full ${f.setId}`,
  ].filter((line) => line !== null).join("\n");
}
```

And `report-text.d.ts` declaring the exports above with the `ReportFacts` / `ReasonId` types from the Interfaces block.

- [ ] **Step 5: Run** the test → PASS. `bun run typecheck` (or the repo's `tsc --noEmit` script) clean.

- [ ] **Step 6: Commit** `feat(web): the broken-video report's words, held to a shared fixture`.

### Task 1.2: Kotlin port

**Files:**
- Create: `android/core/model/src/main/kotlin/ReportText.kt` (package `model`)
- Test: `android/core/model/src/test/kotlin/ReportTextFixtureTest.kt`

**Interfaces — Produces:**
```kotlin
enum class ReportReason(val id: String, val words: String) { WONT_PLAY("wont-play", "Won't play"), PICTURE_SOUND("picture-sound", "Picture or sound wrong"), SUBTITLES("subtitles", "Subtitles wrong"), WRONG_TITLE("wrong-title", "Wrong title or poster") }
object ReportWords { const val BUTTON = "Report"; const val SENT = "Reported"; const val HEADING = "What's wrong?"; const val NOTE = "Anything else? (optional)"; const val SEND = "Send"; const val CANCEL = "Cancel"; const val FAILED = "Couldn't send. Try again later." }
const val REPORT_NOTE_MAX = 200
data class ReportFacts(val reason: ReportReason, val title: String, val note: String?, val profile: String?, val screen: String, val device: String?, val app: String, val positionSeconds: Double?, val played: String?, val held: Boolean, val error: String?, val setId: String)
fun cleanReportNote(note: String?): String
fun reportText(facts: ReportFacts): String
```

- [ ] **Step 1: Write the failing test** — copy `DailyLimitFixtureTest`'s `locate()` and JSON reading; for each `cases[]` build `ReportFacts` (reason by `id`; `position` → `positionSeconds` as `doubleOrNull`; nullable strings via `contentOrNull`) and `assertEquals(case.text, reportText(facts), case.name)`; for each `notes[]` assert `cleanReportNote`; assert `ReportReason.entries.map { it.id to it.words }` equals the fixture's `reasons`, every `ReportWords` constant equals `words.*`, and `REPORT_NOTE_MAX == noteMax`.
- [ ] **Step 2: Run** `cd android && ./gradlew :core:model:test --tests model.ReportTextFixtureTest` → FAIL.
- [ ] **Step 3: Implement** `ReportText.kt` — line-for-line port of `report-text.js`; whitespace collapse with `Regex("""(?U)\s+""")`, then `trim().take(REPORT_NOTE_MAX).trimEnd()`; clock with `floor`; `who` / `how` filtered with `!isNullOrEmpty()`; KDoc says the web rule is the reference and names the fixture.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(android): port the report words, held to the web's fixture`.

**Success criteria:** both suites green on the same fixture.
