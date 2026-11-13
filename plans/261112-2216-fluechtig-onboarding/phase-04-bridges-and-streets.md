# Phase 4: Bridges, buses on streets, balance

**Context:** [design.md](design.md) § Bridges, § Streets for buses, § U-Bahn; renders `reports/bridges/`. Throwaway sources: `bridges/pick_bridges.py` (the city edit), `mockup/mockup.tsx` `BridgedTv` (deck drawing, Dijkstra route). Code: `cities/fluechtig-big-city.json`, `tools/fluechtig-city/{generate_city.py,balance_sim.py}`, `client/src/tv/fluechtig/{plan-lines.ts,StadtplanGround.tsx,fluechtig.css}`, `src/fluechtig_rules_tests.rs:202` (city totals).

**Overview:** priority high, status todo. Independent of Phases 2–3; needs Task 1's ladder for Task 11.

**Key insights**
- The city file keeps its format (one key per line, as `generate_city.py:252` writes it) and gains no new key: the client finds bridges itself (a taxi/bus link whose ends lie on opposite sides of the river).
- After the edit the river is crossed by taxi at 55–73, 58–76, 68–105, 83–101, 89–108, 117–135 and by bus at 58–76, 68–105 only (checked on `bridges/city-6-bridges.json`); U-Bahn crossings stay (underground, no deck); the ferry stays.
- Totals become taxi 342, bus 89 on 63 stops, U-Bahn 20 on 14, ferry 3 on 4.

---

### Task 9: The bridged city and its tool

**Files:** Create `tools/fluechtig-city/add_bridges.py`; Modify `cities/fluechtig-big-city.json`, `src/fluechtig_rules_tests.rs` (`the_big_city_has_the_classic_totals_and_hangs_together`), `src/fluechtig_map.rs` (doc of `CityFile`: made by `generate_city.py`, then `add_bridges.py`)

**Interfaces:**
- Produces: `python3 -I tools/fluechtig-city/add_bridges.py CITY OUT --bridges 55-73 58-76 117-135 83-101 68-105 89-108 --bus 58-76 68-105 105-106` — `pick_bridges.py`'s hand-picked path only (drop the automatic picker and the env variables): remove taxi/bus links over open water, add the bridges, lay streets where a bus route is over 1.8× its straight distance, split rides at stops they pass; assert every network is still one piece; write generate_city.py's format without a `bridges` key. Add a `--bus` link only when that pair is not already a bus link (checked 2026-11-13: `pick_bridges.py` run on its own output adds 105–106 a second time, bus 89 → 90).

- [ ] **Step 1: Write the failing Rust test changes:** totals `(342, 89, 20, 3)`, stops `(63, 14, 4)`; new test `taxi_and_bus_cross_the_river_only_on_the_six_bridges`: a field is north when `spot.y < river_y(spot.x)` (linear between `city.river` points, written in the test); the taxi crossings as sorted pairs equal `[(55, 73), (58, 76), (68, 105), (83, 101), (89, 108), (117, 135)]`, the bus crossings `[(58, 76), (68, 105)]`.
- [ ] **Step 2:** `cargo test -p mediagram-game big_city` → FAIL.
- [ ] **Step 3: Implement the tool** and run it on the current city: `python3 -I tools/fluechtig-city/add_bridges.py cities/fluechtig-big-city.json cities/fluechtig-big-city.json --bridges … --bus …`. Expected output names the laid streets `114-115, 22-23, 49-68, 135-136, 149-168, 26-43` and the split `104-106 → 104-105-106`.
- [ ] **Step 4: Self-checks:** running the tool again on its output changes nothing (`git diff --stat` shows the city file unchanged after the second run); `python3 -I -c` comparing taxi/bus/ubahn/ferry sets of the output with `plans/261112-2216-fluechtig-onboarding/bridges/city-6-bridges.json` → equal.
- [ ] **Step 5:** `cargo test -p mediagram-game` → PASS.

### Task 10: Decks under bridges, buses down the street

**Files:** Create `client/src/tv/fluechtig/street-routes.ts`, `client/tests/fluechtig-streets.test.ts`; Modify `client/src/tv/fluechtig/plan-lines.ts` (`planLines` returns `bus` from routes and a new `bridges`), `client/src/tv/fluechtig/StadtplanGround.tsx` (deck paths after `.sp-jetty`), `client/src/tv/fluechtig/fluechtig.css` (`.sp-deck-edge`, `.sp-deck`, `.sp-bus` as a stripe narrower than the street, `stroke-linejoin: round`)

**Interfaces:**
- Produces: `streetRoutes(board: FluechtigBoard, rides: { a: number; b: number }[]): number[][]` — for each ride the fields of the shortest taxi route (straight-line weights) from `a` to `b`; `bridgeDecks(board: FluechtigBoard): string` — one straight segment per field pair joined by a taxi or bus link whose ends lie on opposite sides of `riverY`, drawn over the stretch within the river's banks (the mockup's 27-unit reach); `planLines(board).bridges: string`. U-Bahn keeps `linkPath(..., BOW)`; the bus no longer bows.

- [ ] **Step 1: Write the failing tests** on the real city (`import city from "../../cities/fluechtig-big-city.json"`, mapped to a `FluechtigBoard` in the test): every bus route starts at `a`, ends at `b`, and each step is a taxi link; no route passes another bus stop on the way; `bridgeDecks(board).match(/M/g).length === 6`; a board whose only river crossing is a U-Bahn link gets `""`.
- [ ] **Step 2:** `cd client && bun test fluechtig-streets` → FAIL.
- [ ] **Step 3: Implement** (build the street graph once per call; `FluechtigBoard`'s `useMemo` already keys on the city). Deck colours: edge `#7d6f57`, deck `#ece4d2` as CSS classes; widths in `--u` units like the other `.sp-*` strokes.
- [ ] **Step 4:** `bun test`, `bun run typecheck` → PASS; the existing `fluechtig-stadtplan.test.ts` still passes.

### Task 11: The ladder in the balance tool, measured again

**Files:** Modify `tools/fluechtig-city/balance_sim.py`; `plans/261112-2216-fluechtig-onboarding/design.md` (balance table, if numbers moved)

**Interfaces:**
- Produces: rules names `game5`, `game4`, `game3`, `game2` (`game` stays `game5`) from `ladder(figures)` mirroring `Rules::big_for`: `hunters=figures`, reveals `{3, 8, 13, 18, 24}` at 5 else `set(range(3, 25, 3))`, fugitive `black/double` 5/2, 5/2, 2/1, 1/0.

- [ ] **Step 1: Implement** `ladder` and the rule names; update the docstring's usage.
- [ ] **Step 2: Measure:** `python3 -I tools/fluechtig-city/balance_sim.py 1500 cities/fluechtig-big-city.json:game5 cities/fluechtig-big-city.json:game4 cities/fluechtig-big-city.json:game3 cities/fluechtig-big-city.json:game2`. Expected near 43 / 46 / 49 / 50 %. Every value must lie in 40–55 %; if one does not, stop and report to the user instead of tuning.
- [ ] **Step 3:** Write the measured numbers into design.md's table and plan.md's Results.

**Success criteria:** the plan shows six stone decks, buses on streets, no line over open water but U-Bahn and ferry; balance numbers recorded.

**Risks:** a later regeneration with `generate_city.py` alone would bring the open-water links back: the `CityFile` doc names both steps.
