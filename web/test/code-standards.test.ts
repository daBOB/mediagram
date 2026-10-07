/**
 * Source files stay under 200 lines, as the Rust crates' `code_standards.rs`
 * holds theirs.
 *
 * A ratchet rather than a wall: the files already past the line are listed
 * with their size when this was written, and may shrink but not grow. A new
 * file, or one not listed, must fit. When a listed file is split below the
 * line, delete its entry.
 */

import { expect, test } from "bun:test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const LIMIT = 200;
const ROOT = join(import.meta.dir, "..");

/**
 * Files over the limit on 2026-09-25, and the size each may not exceed.
 * Revised 2026-09-26 for the watched-removal sync and the streaming/startup
 * work, written alongside this list and merged after it. Revised again the
 * same day for the settings menu: a stored account and cache budget, an
 * admin-gated router, and the runtime wiring to swap either live. Lowered
 * 2026-09-27 for `app.js`, once the catalog and its update stream moved out
 * to `library-session.js`; raised the same day, by 7, for the re-entrant-draw
 * guard and the reactive player hold that review found still belonged there.
 * Also 2026-09-27: `src/index.ts` grew by six lines wiring the paced-reads probe
 * through startup alongside the encoder probe it already ran next to. Lowered
 * again the same day for `app.js` and `shelf-view.js`, once the hash format
 * moved out to `lib/address.js` and every hand-built `#/…` string in the app
 * became one call into it. Lowered once more for `app.js`, when what plays
 * next and what the server preloads became one answer in
 * `lib/playback/plays-next.js`. Raised 2026-09-28 for `app.js`, by 11, for the
 * Anime department's routing, nav count and the show route's shelf-by-section
 * lookup, and for `shelf-view.js`, by 2, for the Anime empty state's upload
 * hint and its section unions. Raised 2026-09-30 for `src/cache/held.ts`, by
 * 5, for the `ids` getter the subtitle bundle reconcile enumerates held sets
 * through. Lowered the same day for `playback/transport.js`, once its
 * subtitle picker, 'c' toggle and menu-building moved out to the new
 * `playback/subtitle-picker.js`. Raised 2026-09-30 for `src/index.ts`, by 10,
 * wiring the subtitle bundle store into the router, the catalog follower and
 * the startup reconcile of held titles. Lowered 2026-10-03 for `app.js`,
 * `src/state/routes.ts` and `src/state/schema.ts`, once the shelf toggle, the
 * shared route helpers and the test-only `migrationsUpTo` moved out to make
 * room for viewing stats.
 * Lowered again for `src/state/routes.ts`, once the stats route moved to
 * `src/routes.ts`, the one router that holds the catalog its achievements
 * are counted against, and for `app.js`, once the player's lazy loader moved
 * out to `lib/playback/player-loader.js`. Lowered 2026-10-04 for `app.js`,
 * once marking the current nav link moved out to `lib/nav-current.js`.
 * Raised 2026-10-04 for `src/state/schema.ts`, by 2, to list the profile-roles migration group.
 * Lowered the same day for `app.js`, once a kid's own age limit replaced the one-flag kids check,
 * and for `watch-state.js`, once profile management moved to `lib/profile-api.js`.
 * Lowered the same day for `src/state/store.ts`, `src/state/routes.ts` and
 * `src/state/sync-record.ts`, once profile rows, the profile routes and the
 * record's scalar readers moved out to `profiles*.ts` and `record-scalars.ts`.
 * Lowered 2026-10-05 for `player.js`, `transport.js` and `playback.css`, once the
 * player's controls became one card with its menus, framing and stats in modules
 * and styles of their own. Lowered 2026-10-06 for `src/state/store.ts`, once its
 * hand-rolled transactions became `db.transaction()` and the progress exchange
 * moved to `stats-recorder.ts`. Lowered the same day for `src/state/routes.ts`, once
 * the write guard moved to the dispatcher and its body reads to `jsonBody`.
 * Lowered the same day for `src/index.ts`, once the status router became a
 * plain value built before the server and the cache budget left startup facts.
 * Lowered the same day for `app.js` and `shelf-view.js`, once `SECTIONS` was
 * imported from `sections.js` rather than forwarded, and for
 * `src/state/sync-record.ts`, once `parseRows` moved to `record-scalars.ts`.
 * Lowered the same day for `src/state/store.ts`, once its foreign-key check
 * read the SQLite code through `errorCode`.
 */
const CEILINGS: Record<string, number> = {
  "public/app.js": 693,
  "public/lib/catalog/course-view.js": 234,
  "public/lib/catalog/featured-reel.js": 212,
  "public/lib/catalog/series-summary.js": 201,
  "public/lib/catalog/shelf-view.js": 285,
  "public/lib/library.js": 288,
  "public/lib/playback/notes/markdown.js": 227,
  "public/lib/playback/player.js": 994,
  "public/lib/playback/streaming/buffer-health.js": 258,
  "public/lib/playback/streaming/hls-playback.js": 220,
  "public/lib/playback/transport.js": 350,
  "public/lib/watch-state.js": 478,
  "public/styles/home.css": 363,
  "public/styles/playback.css": 625,
  "public/styles/shell.css": 257,
  "public/styles/theme.css": 209,
  "src/cache/held.ts": 207,
  "src/cache/reader.ts": 293,
  "src/cache/store.ts": 313,
  "src/config.ts": 265,
  "src/index.ts": 446,
  "src/package/refresh.ts": 272,
  "src/server.ts": 269,
  "src/state/routes.ts": 209,
  "src/state/schema.ts": 245,
  "src/state/store.ts": 723,
  "src/state/sync-record.ts": 293,
  "src/transcode/registry.ts": 338,
};

function sources(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) sources(path, out);
    else if (/\.(ts|js|css)$/.test(name) && !name.endsWith(".d.ts") && !name.startsWith("hls")) out.push(path);
  }
  return out;
}

test("every source file stays under the line limit, and none over it grows", () => {
  const over: string[] = [];
  for (const dir of ["src", "public", "scripts"]) {
    for (const file of sources(join(ROOT, dir))) {
      const name = relative(ROOT, file);
      const lines = readFileSync(file, "utf8").split("\n").length - 1;
      const ceiling = CEILINGS[name] ?? LIMIT;
      if (lines > ceiling) over.push(`${name}: ${lines} lines (limit ${ceiling})`);
    }
  }
  expect(over).toEqual([]);
});
