# Phase 02 — Shared format v=2 and cast rules (web reference)

**Spec:** §1, §2, §3 (tracks), §4 (ending words), §5 (words). **Status:** pending.
**Pattern to copy:** `web/public/lib/together/*.js` + `.d.ts`, `web/test/together-fixtures.test.ts` (its `load<T>()`), and `web/public/lib/report/report-text.js` for a words object held to a fixture.

### Task 2.1: The message becomes v=2

**Files:**
- Modify: `web/public/lib/together/session.js`, `session.d.ts`
- Modify: `web/test/fixtures/together/format.json`; `web/test/fixtures/channel-updates/together.json` (add one `v=2` case, classified `together`)
- Test: `web/test/together-fixtures.test.ts` (the `format.json` runner is unchanged)

**Interfaces — Produces:**
- `TOGETHER_MARKER = "#mlib-together v=2"`; `TOGETHER_MARKER_V1 = "#mlib-together v=1"` (read only)
- `Member { screen; label; remote: boolean }`
- `Track { id: string; label: string }`; `CastTracks { audio: Track[]; text: Track[] }`
- `CastState = "offer" | "busy" | "refused" | "replace" | "on"`
- `Cast { to: "TV"; from; profile; state: CastState; tv; note; tracks: CastTracks; audio; text }`
- `Session` gains `cast: Cast | null`
- `REQUEST_LIFETIME_MS = 60_000`; `isRequest(s: Session): boolean` (`cast !== null && cast.state !== "on"`), here because `follow.js` and `cast.js` both need it
- `parseSession(message)`:
  - `v=1` still reads: every member gets `remote: false`, the session gets `cast: null`, and a `cast` or `remote` key inside a `v=1` body is ignored;
  - `v=2` requires a boolean `remote` on every member, and `cast` as `null` or a whole valid `Cast` (spec §1 "What a reader accepts");
  - unknown keys are dropped at every level, including inside `cast`, `tracks` and the track entries.
- `writeSession(s)`:
  - always writes `v=2`, with members as `{screen,label,remote}` and `remote` written as `m.remote === true`;
  - then `ended`, then `cast` (`null`, or its keys in the order `to, from, profile, state, tv, note, tracks{audio,text}, audio, text`, each track as `{id,label}`).

- [ ] **Step 1: Rewrite `format.json`.**
  - **Every existing case:** the `v=1` text stays. Each non-null `expect` gains `"remote": false` on each member and `"cast": null`.
  - **Replace** "another version is not a session" with `"#mlib-together v=3\n{}"` → `null`.
  - **Add `v=2` cases**, each with `name`, `text` and `expect`. `C` is the spec §1 example's cast, with `state: "on"`, `tv: "tv-1"`, and two text tracks `{"id":"en","label":"English"}` and `{"id":"de","label":"Deutsch"}`.
    - a v=2 session with `cast: null` and a remote member → parsed;
    - a v=2 session with `C` → parsed whole;
    - a v=2 member without `remote` → `null`;
    - `remote: "yes"` → `null`;
    - `cast.state: "playing"` → `null`;
    - `cast.profile: ""` → `null`;
    - `cast.to: ""` → `null`;
    - `cast.tracks.text[0]` without `label` → `null`;
    - `cast.audio: 3` → `null`;
    - an unknown key inside `cast`, and one inside a track → dropped;
    - a v=1 body carrying `"cast":{...}` and `"remote":true` → parsed with `cast: null`, `remote: false`.
- [ ] **Step 2:** In `channel-updates/together.json`, add the case `"a v=2 session is together too"` (copy the v=1 case's shape).
- [ ] **Step 3: Run** `cd web && bun test test/together-fixtures.test.ts test/channel-updates-fixtures.test.ts` → FAIL (`format.json` only).
- [ ] **Step 4: Implement.**
  - `session.js`: a `trackOf` and a `castOf` validator next to `memberOf`.
  - `memberOf(x, v1)`: with `v1`, `remote` is forced to `false`; otherwise it must be a boolean.
  - `writeSession`: build the object literally in the order above. Never spread `s`, or extra keys leak.
  - Update `session.d.ts`, keeping its header comment true about `v=2` and `v=1`.
- [ ] **Step 5: Run** the tests → PASS. Also run `bun test test/together-*.test.ts` and `bunx tsc --noEmit -p .`. Fix the callers that build a `Member` without `remote`: `src/together/routes.ts:57` gets `remote: false`, plus test helpers.
- [ ] **Step 6: Commit** `feat(together): the session message moves to v=2 with remotes and a cast section`.

### Task 2.2: Liveness and leaving know about casts

**Files:**
- Modify: `web/public/lib/together/follow.js` (`isLive`), `moves.js` (`left`)
- Modify: `web/test/fixtures/together/liveness.json`, `moves.json`

**Interfaces — Produces:**
- `isLive(s, durationSecs, now)`: after the `ended` check, `if (isRequest(s)) return now - s.at < REQUEST_LIFETIME_MS;`
- `left(s, screen)`: `ended` is true when no member is left whose `remote` is false. An empty list counts.

- [ ] **Step 1: Add fixture cases.** `S` is the existing base session with `remote:false` members and `cast:null`.
  - In `liveness.json`:
    - "an offer is live for a minute": `cast.state "offer"`, `now = at + 59_999` → `true`;
    - "an offer is stale after a minute": `now = at + 60_000` → `false`;
    - "a busy answer is a request too": `busy`, `now = at + 60_000` → `false`;
    - "an accepted cast lives as any session": `on`, playing, duration 600, `now = at + 120_000` → `true`.
  - In `moves.json`:
    - "the last player leaving ends it, though a remote stays": members are `tv-1` (not remote) and `tab-1` (remote), `left(s, "tv-1")` → `ended: true`, members `[tab-1]`;
    - "a remote leaving keeps it": `left(s, "tab-1")` → `ended: false`.
- [ ] **Step 2: Run** the fixtures test → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(together): requests go stale in a minute, and remotes never keep a session`.

### Task 2.3: The cast rules and words

**Files:**
- Create: `web/public/lib/together/cast.js`, `cast.d.ts`
- Create: `web/public/lib/together/cast-words.js`, `cast-words.d.ts`
- Create: `web/test/fixtures/together/cast.json`
- Test: `web/test/together-cast.test.ts`

**Interfaces — Produces** (`cast.js`):
- `CAST_ANSWER_MS = 5000`, `AUDIO_TRACKS_MAX = 8`, `TEXT_TRACKS_MAX = 24`, `TRACK_LABEL_MAX = 32`
- `offered(id: string, me: Member, own: Playback, profile: string, now: number): Session`
  - members `[{...me, remote: true}]`;
  - `cast: {to:"TV", from: me.label, profile, state:"offer", tv:"", note:"", tracks:{audio:[],text:[]}, audio:"", text:""}`;
  - `by: me.screen`, `at: now`, `pos` rounded to milliseconds as `started` does.
- `type TvAnswer = {kind:"ignore"} | {kind:"refuse", note} | {kind:"busy", note} | {kind:"accept"}`
- `answerOf(s: Session, tv: {screen: string; busy: string|null; refusal: string|null}, now: number): TvAnswer`, in this order:
  1. `ignore` when `!isRequest(s)`, or `s.ended`, or `state` is `busy` or `refused`, or `!isLive(s, null, now)`;
  2. `refuse` with `refusal` when that is non-null;
  3. `busy` with `busy` when `state === "offer"` and `busy` is non-null;
  4. otherwise `accept`. A `replace` with `busy` set is still `accept`.
- `busied(s, tv: string, note: string): Session`: `state:"busy"`, the `note`, `by: tv`. `pos` and `at` are kept, so staleness counts from the offer.
- `refusedBy(s, tv, note): Session`: the same, with `state:"refused"`.
- `replaced(s, me: string, own: Playback, now): Session`: `state:"replace"`, `note:""`, `set`/`playing`/`pos`/`rate` from `own`, `at: now`, `by: me`.
- `accepted(s, me: Member, now): Session | null`:
  - returns `null` when `cast.tv` is already set;
  - otherwise `joined(s, {...me, remote:false})`, with `cast.tv: me.screen`, `state:"on"`, `note:""`, `playing:false`, `pos: expectedPosition(s, now)` rounded to milliseconds, `at: now`, `by: me.screen`.
- `tracked(s, tv, tracks: CastTracks, audio: string, text: string): Session`:
  - lists cut to `AUDIO_TRACKS_MAX` / `TEXT_TRACKS_MAX`, labels to `TRACK_LABEL_MAX` characters (trimmed);
  - `audio`/`text` set, `by: tv`.
- `chose(s, screen, choice: {audio?: string, text?: string}): Session`: the given field(s) set, `by: screen`.
- `endingOf(s, me: string): "finished" | "stopped" | null`:
  - `null` when the session isn't ended, has no cast, or `by === me`;
  - `"finished"` when `by === cast.tv` and the TV is still a member;
  - otherwise `"stopped"`.

**Interfaces — Produces** (`cast-words.js`):
- `CAST_WORDS = { playOnTv: "Play on TV", asking: "Asking the TV…", replace: "Replace", cancel: "Cancel", noAnswer: "Mediagram isn't open on the TV. Open it there and try again.", leaveFirst: "Leave watch together first", playingOnTv: "Playing on TV", stop: "Stop", finished: "Finished", stopped: "Stopped on the TV", libraryMissing: "the TV's library doesn't have it yet", profileUnknown: "the TV doesn't know this profile yet" }`
- `busyQuestion(title)` → `` `The TV is playing ${title}. Replace it?` ``
- `refusedLine(reason)` → `` `The TV won't play this: ${reason}` ``
- `castFrom(from)` → `` `Cast from ${from}` ``
- `barLine(title)` → `` `Playing on TV · ${title}` ``
- `ageReason(name)` → `` `it's rated above ${name}'s age limit` ``
- `timeReason(name)` → `` `${name}'s time is up for today` ``
- `sayOnRemote(noticed: {change, label}, s: Session): string | null`:
  - `null` for the TV's own `joined`/`left`;
  - otherwise `say(noticed, s)` from `playback/together-words.js`, with a trailing `" by TV"` written as `" on TV"`.

- [ ] **Step 1: Write `cast.json`** as `{ words, offered[], answer[], moves[], ending[], remoteNotices[] }`. Every case has a `name`.
  - `words`: the object and the six formatted lines, each with a sample argument and its expected text.
  - `answer` cases:
    - idle offer → `accept`;
    - busy offer → `busy` with the note;
    - replace while busy → `accept`;
    - refusal → `refuse`, which wins over busy;
    - `on` → `ignore`;
    - `busy` state → `ignore`;
    - stale offer → `ignore`;
    - ended → `ignore`;
    - plain watch together (`cast:null`) → `ignore`.
  - `moves` cases (`move` is one of `busied`/`refusedBy`/`replaced`/`accepted`/`tracked`/`chose`):
    - `accepted` on a playing offer: paused at the expected position, the TV member appended with `remote:false`;
    - `accepted` when `tv` is already set → `null`;
    - `tracked` with 30 text tracks and a 50-character label → 24 entries, the label cut to 32;
    - `chose` with only `text` → `audio` unchanged.
  - `ending` cases:
    - own Stop → `null`;
    - the TV left → `stopped`;
    - the TV finished → `finished`;
    - another remote stopped → `stopped`.
  - `remoteNotices` cases:
    - paused by TV → "Paused on TV";
    - sought by TV → "Skipped to 0:01:40 on TV";
    - paused by Phone → "Paused by Phone";
    - TV joined → `null`.
- [ ] **Step 2: Write `together-cast.test.ts`** in the shape of `together-fixtures.test.ts`.
  - One `describe` per array. The `moves` runner dispatches on `one.move`, as `moves.json`'s runner does.
  - `expect(CAST_WORDS).toEqual(fixture.words.object)`, and each formatter checked against its sample.
  - For `accepted` and `tracked` results, also `expect(parseSession(writeSession(result))).toEqual(result)`.
  - Add `test("a full cast stays under Telegram's 4096 characters")`. It builds `tracked` with 30 audio and 40 text tracks, every label 80 characters and every id 16, on a session with 4 members, and asserts `writeSession(...).length < 4096`.
- [ ] **Step 3: Run** `bun test test/together-cast.test.ts` → FAIL.
- [ ] **Step 4: Implement** `cast.js`, `cast-words.js` and both `.d.ts` files. `cast.js` imports from `session.js`, `moves.js` and `follow.js`.
- [ ] **Step 5: Run** → PASS; then run `bunx tsc --noEmit -p .` and `bunx eslint public --max-warnings 0`.
- [ ] **Step 6: Commit** `feat(together): the cast handshake, track list and words, held to a shared fixture`.

**Success criteria:** every `together-*` test passes on the new fixtures, and `channel-updates` still passes (`cargo test --test shared_channel_update_fixtures` reads the same file and must pass too).
