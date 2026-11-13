# Phase 3: TV cards, legend, phone role screens, themes

**Context:** [design.md](design.md) § cards, role card, role bar, naming, legend, phone colours. Look to match: `reports/mockups/sheet-tv-rules-cards-and-legend.jpg`, `reports/mockups/sheet-phones-role-colours.jpg` (police blue, not green). Port markup and CSS from `mockup/mockup.tsx` (`Card`, `cards`, `Line`, `Ring`, `Figure`, `Legend`, `RoleBar`, `HunterRole`, `phone-1-role-fugitive`) and `mockup/mockup.css` (`mk-*` → `fl-*`/`flp-*`), replacing the mockup's DOM patches with real props.

**Overview:** priority high, status todo. Client only (`client/`); needs Phases 1–2's protocol.

**Key insights**
- Everything the cards say comes from the scene: `rounds`, `reveal_rounds`, `fugitive_tickets.schatten/doppel` (the start stock while the cards run), `hunters[].player`, `board` (ring examples via `fieldRings`).
- The theme must colour the page body (`body { background: var(--felt) }` in `src/styles.css`), which `PhoneFluechtig` does not own: set `data-fl-role` on `<html>` in an effect and remove it on unmount; CSS overrides the felt variables under it.
- Badges and "Figur N" appear only for a player with two figures; one helper decides that for TV and phone.

---

### Task 6: Names on TV and phone

**Files:** Create `client/src/ui/fluechtig-naming.ts`, `client/tests/fluechtig-naming.test.ts`; Modify `client/src/tv/TvFluechtig.tsx:59-66`, `client/src/tv/fluechtig/FluechtigBoard.tsx:100-102`, `client/src/phone/PhoneFluechtig.tsx:62-90`, `client/src/phone/fluechtig-phone-parts.tsx` (`Disc`, `WaitHead`, `Pieces`)

**Interfaces:**
- Consumes: `FluechtigMover.piece/runs`, `FluechtigPiece.number` (Task 3).
- Produces in `fluechtig-naming.ts`: `figureOf(hunters: FluechtigHunter[], index: number): number | null` (the figure's number among its player's, null with one); `andList(items: (string | number)[]): string` ("3, 8, 13, 18 und 24"; one item alone; two "A und B"); `genitive(name: string): string` ("Danas", "Max’", "Jens’", "Fritz’"). `Disc` gains `avatar?: string | null` (shows `avatarHeadUrl(avatar)` instead of the label).
- Texts: TV banner `"{name} ist dran"` / `"{name} zieht Figur {k}"`; board badge only when `figureOf` is not null; hunter's own head `"Deine Figur · Feld {at}"` / `"Figur {k} · Feld {at}"` with `me.avatar` in the disc; `WaitHead` `"{name} ist dran"` / `"{name} zieht Figur {k}"` / fugitive `"{name} fährt geheim …"` (decided by `mover.runs`), `"Danach bist du dran mit Figur {k}"` only with two; `Pieces` heading `"Deine Figur"` / `"Deine Figuren"`.

- [ ] **Step 1: Write the failing tests** (`bun test` style, as `client/tests/fluechtig-river.test.ts`): `figureOf` with owners `[2, 3, 4]` → all null; `[2, 2]` → 1, 2; `andList([3, 8, 13, 18, 24]) === "3, 8, 13, 18 und 24"`, `andList(["Cem"]) === "Cem"`, `andList(["Cem", "Ela"]) === "Cem und Ela"`; `genitive("Dana") === "Danas"`, `genitive("Max") === "Max’"`, `genitive("Jens") === "Jens’"`.
- [ ] **Step 2:** `cd client && bun test fluechtig-naming` → FAIL.
- [ ] **Step 3: Implement** the helpers and the texts above.
- [ ] **Step 4:** `bun test` and `bun run typecheck` → PASS.

### Task 7: TV rules cards and line legend

**Files:** Create `client/src/tv/fluechtig/FluechtigIntroCard.tsx`, `client/src/tv/fluechtig/FluechtigLegend.tsx`, `client/src/tv/fluechtig/fluechtig-intro.css`; Modify `client/src/tv/TvFluechtig.tsx`

**Interfaces:**
- Consumes: `FluechtigScene.intro`, `FluechtigIntro` (Task 5); `andList`, `genitive`, `figureOf` (Task 6); `fieldRings` (`plan-lines.ts`).
- Produces: `FluechtigIntroCard({ scene, intro, players }: { scene: FluechtigScene; intro: FluechtigIntro; players: PlayerInfo[] })`; `LineSwatch({ kind, width }: { kind: FluechtigTicket; width?: number })` (a line as the plan draws it; the bus as its stripe on a street); `FluechtigLegend({ runner }: { runner: string })` at the TV's bottom right, always shown.
- Card copy (mockup wording; `R` = runner's name, numbers from the scene): 1 "Wer ist wer?" — R's figure "flüchtig · unsichtbar"; the hunting players' figures, each once, "Jäger · jeder mit einer Figur" (lone hunter: "Jäger · mit zwei Figuren"); "Fährt ein Jäger auf {genitive(R)} Feld, ist R gefangen." / "Hält R {rounds} Runden durch, ist R entkommen." 2 "Die Linien" — Taxi, Bus, U-Bahn, Fähre rows with `LineSwatch` and a ring of the lowest field of that kind. 3 "Unsichtbar – fast immer" — log line; "In Runde {andList(reveal_rounds)} taucht R auf …"; orange circles; "R hat {n} Schattenfahrt(en)" plus "und {d} Doppelzug/Doppelzüge" only when `d > 0`. Footer: dots for `card/cards`, "{leader} kann am Handy überspringen" when a leader is named.

- [ ] **Step 1: Write the failing test** in `client/tests/fluechtig-naming.test.ts` for the copy helper the cards use: `ticketLine(schatten: number, doppel: number, runner: string): string` (export it from `fluechtig-naming.ts`): `(5, 2, "Dana")` mentions "5 Schattenfahrten" and "2 Doppelzüge"; `(1, 0, "Dana")` mentions "1 Schattenfahrt" and no "Doppel".
- [ ] **Step 2:** `bun test fluechtig-naming` → FAIL.
- [ ] **Step 3: Implement** `ticketLine`, the two components and the CSS; `TvFluechtig` renders `{scene.intro && <FluechtigIntroCard … />}` and `<FluechtigLegend runner={runner} />`.
- [ ] **Step 4:** `bun test`, `bun run typecheck` → PASS. Visual check is Task 12's screenshots.

### Task 8: Phone role card, role bar, role themes

**Files:** Create `client/src/phone/FluechtigRole.tsx`; Modify `client/src/phone/PhoneFluechtig.tsx`, `client/src/phone/fluechtig-phone.css`

**Interfaces:**
- Consumes: `FluechtigPhone.intro/may_skip/reveal_rounds/partners` (Task 5); `andList`, `genitive` (Task 6).
- Produces: `RoleCard({ phone, me, conn }: { phone: FluechtigPhone; me: PlayerInfo; conn: Connection })`; `RoleBar({ phone }: { phone: FluechtigPhone })`; `useRoleTheme(runs: boolean): void` (sets `document.documentElement.dataset.flRole` to `"runner"`/`"hunter"`, deletes it on unmount).
- Copy (mockup): fugitive — "Du bist flüchtig", hideout ring with `hiding`, "Dein Versteck: Feld {hiding} – nur du siehst es.", "Halte {rounds} Runden durch …", "In Runde {andList(reveal_rounds)} tauchst du auf." Hunter — "Du bist Jäger", own figure(s) and fields, "Fang {fugitive}: Fahr auf das Feld, auf dem {fugitive} steckt.", orange-circles line, "{andList(partners)} jagt/jagen mit dir." (omitted when empty). Both: "Die Regeln laufen gerade auf dem Fernseher." Skip button "Regeln überspringen" when `may_skip`, sending the same `choose` action as `PhoneFluechtig`'s `pick` with index 0, once. Role bar: "Du bist flüchtig · Nur du siehst dein Versteck" / "Du bist Jäger · Fang {fugitive}!".
- Themes in `fluechtig-phone.css`: `:root[data-fl-role="runner"] { --felt: #1a1d29; --felt-deep: #0f111a; --felt-line: #2c3146; --muted: #9ba3bd; --yellow: #f2a14b; }`, `:root[data-fl-role="hunter"] { --felt: #173f73; --felt-deep: #0f2d55; --felt-line: #24559a; --muted: #a9c2e3; }`.

- [ ] **Step 1:** Implement: `PhoneFluechtig` calls `useRoleTheme(runs)`, returns `<RoleCard …/>` while `phone.intro` is set, otherwise puts `<RoleBar …/>` first on both its screens.
- [ ] **Step 2:** `bun run typecheck`, `bun test`, `bun run build` → PASS.
- [ ] **Step 3:** Render the four phone states with the throwaway `mockup/shoot.mjs` pattern or the live run (Task 12) and compare with `reports/mockups/sheet-phones-role-colours.jpg`.

**Success criteria:** cards and legend match the mockup renders with live data; a hunter's role card cannot be mistaken for the fugitive's (headline, colour, no hideout ring); the theme is gone on the final screen.

**Risks:** the night theme's `--muted` on `--felt-deep` must stay readable (check contrast in the screenshot).
