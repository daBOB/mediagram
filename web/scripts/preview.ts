/**
 * A preview of the player for UI work: the real router, the real pages and
 * the real artwork, over copies of this machine's library and watch state —
 * and no Telegram.
 *
 * Why it exists: `bun run dev` restarts the real player on every edit, and
 * that process holds the account's auth key; a second connection breaks
 * both, and a restart mid-upload loses the upload. This serves the same
 * pages from the same `startServer`, reads a *copy* of the channel snapshot
 * and of `state.db` (so nothing the preview does reaches the real ones), and
 * answers media requests with nothing — pages, posters, search, profiles and
 * pins all work; playback does not.
 *
 *   bun run preview                 → http://127.0.0.1:8795
 *   PREVIEW_PORT=9000 bun run preview
 *   PREVIEW_INDEX=/path/library.db bun run preview   (a different index)
 *
 * Paths default to the ones the player itself uses (see `src/config.ts`).
 */

import { Database } from "bun:sqlite";
import { copyFileSync, existsSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";

import { startServer } from "../src/server";
import { PosterStore } from "../src/package/posters";
import { WatchState } from "../src/state/store";

const home = process.env.HOME ?? "";
const channelIndexDir = process.env.MEDIAGRAM_CHANNEL_INDEX_DIR ?? `${home}/.cache/mediagram-channel-index`;
const index = process.env.PREVIEW_INDEX ?? join(channelIndexDir, "current", "library.db");
const stateDb = process.env.MEDIAGRAM_STATE_DB ?? `${home}/.local/share/mediagram-player/state.db`;
// Where `mediagram posters` writes artwork: beside this machine's index.
const posterDir = process.env.MEDIAGRAM_LIBRARY_DB
  ? dirname(process.env.MEDIAGRAM_LIBRARY_DB)
  : `${home}/.local/share/mediagram`;
const port = Number(process.env.PREVIEW_PORT ?? 8795);

if (!existsSync(index)) {
  console.error(`preview: no index at ${index}; set PREVIEW_INDEX to one`);
  process.exit(1);
}

// Copies, so the preview can never write to the player's own files.
const scratch = mkdtempSync(join(tmpdir(), "mediagram-preview-"));
const indexCopy = join(scratch, "library.db");
copyFileSync(index, indexCopy);
const stateCopy = join(scratch, "state.db");
if (existsSync(stateDb)) copyFileSync(stateDb, stateCopy);

const db = new Database(indexCopy, { readonly: true });
const server = await startServer({
  db,
  // Media is the one thing a preview cannot serve without Telegram.
  source: { stream: () => new ReadableStream() } as never,
  posters: new PosterStore(posterDir),
  state: new WatchState(stateCopy),
  hostname: "127.0.0.1",
  port,
});

console.log(`preview: http://127.0.0.1:${port}  (index ${index}, artwork ${posterDir})`);
console.log("preview: watch state is a copy; nothing here reaches the real player");

const stop = async () => {
  await server.close();
  db.close();
  rmSync(scratch, { recursive: true, force: true });
  process.exit(0);
};
process.on("SIGINT", stop);
process.on("SIGTERM", stop);
