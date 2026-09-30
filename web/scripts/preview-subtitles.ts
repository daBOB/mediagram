/**
 * `PREVIEW_SUBTITLES=<setId>` wires the shared bundle fixture onto one set in
 * the preview's index copy, so the picker, the forced-track rule and the
 * profile setting can all be walked by hand.
 *
 * A preview never plays real media (see `preview.ts`), so what shows has to
 * be read from the browser's own `textTracks`, not the screen — but the
 * catalog route, the picker and 'c' all work exactly as they do live, since
 * this wires the same `SubtitleBundles` the real player would build, over
 * the same fixture `crates/mlib-spec` tests its own codec against.
 */

import { createHash } from "node:crypto";
import { gzipSync } from "node:zlib";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import type { Database } from "bun:sqlite";
import { SubtitleBundles } from "../src/catalog/subtitle-bundles";

const FIXTURE_PATH = join(import.meta.dir, "..", "..", "crates", "mlib-spec", "tests", "fixtures", "subtitle-bundle-v1.json");

interface FixtureTrack { lang: string; forced: boolean; sdh: boolean; label: string }

function ensureTables(db: Database): void {
  db.run(`CREATE TABLE IF NOT EXISTS subtitle_files(
    set_id TEXT PRIMARY KEY REFERENCES sets(set_id) ON DELETE CASCADE,
    chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL,
    bytes INTEGER NOT NULL, sha256 TEXT NOT NULL, uploaded_at INTEGER NOT NULL)`);
  db.run(`CREATE TABLE IF NOT EXISTS subtitle_tracks(
    set_id TEXT NOT NULL REFERENCES subtitle_files(set_id) ON DELETE CASCADE,
    track INTEGER NOT NULL, lang TEXT NOT NULL,
    forced INTEGER NOT NULL DEFAULT 0, sdh INTEGER NOT NULL DEFAULT 0,
    label TEXT NOT NULL, PRIMARY KEY(set_id, track))`);
}

/**
 * Wires `PREVIEW_SUBTITLES` onto `db` (writable) and returns the router's
 * `SubtitleBundles`, or `undefined` when the variable is unset — the router
 * treats an absent one exactly as it would an uploader that predates v13.
 */
export function wirePreviewSubtitles(db: Database, heldDir: string): Pick<SubtitleBundles, "vtt" | "hold" | "reconcile"> | undefined {
  // Comma-separated set ids; `forced:<id>` lists only the forced track, the
  // shape a dubbed film with sign subtitles alone has.
  const ids = process.env.PREVIEW_SUBTITLES?.split(",").filter(Boolean) ?? [];
  if (ids.length === 0) return undefined;

  const raw = readFileSync(FIXTURE_PATH, "utf8");
  const bundle = JSON.parse(raw) as { v: number; tracks: FixtureTrack[] };
  const gz = gzipSync(Buffer.from(raw));
  const sha256 = createHash("sha256").update(gz).digest("hex");

  ensureTables(db);
  const insertTrack = db.query(
    "INSERT INTO subtitle_tracks(set_id, track, lang, forced, sdh, label) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
  );
  for (const id of ids) {
    const forcedOnly = id.startsWith("forced:");
    const setId = forcedOnly ? id.slice("forced:".length) : id;
    db.run("DELETE FROM subtitle_tracks WHERE set_id = ?1", [setId]);
    db.run("INSERT OR REPLACE INTO subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at) VALUES (?1, -1001, 1, ?2, ?3, ?4)", [
      setId, gz.byteLength, sha256, Math.floor(Date.now() / 1000),
    ]);
    bundle.tracks.forEach((track, index) => {
      if (forcedOnly && !track.forced) return;
      insertTrack.run(setId, index, track.lang, track.forced ? 1 : 0, track.sdh ? 1 : 0, track.label);
    });
    console.log(`preview: wired the subtitle fixture onto set ${setId}${forcedOnly ? " (forced only)" : ""}`);
  }
  return new SubtitleBundles({ fetch: async () => gz, background: async () => gz, heldDir });
}
