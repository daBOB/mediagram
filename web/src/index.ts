/**
 * The player backend: catalog and bytes, over HTTP.
 *
 * This process speaks MTProto and holds the session. The browser is an
 * ordinary HTTP client of it and is told what it may play, never where the
 * bytes live.
 */

import { Database } from "bun:sqlite";
import { rm } from "node:fs/promises";
import { join } from "node:path";
import { describe, load } from "./config";
import { EXPECTED_SCHEMA, assertSchema, listPlayable } from "./catalog";
import { startServer } from "./server";
import { isExposed, reachableUrls } from "./listen-address";
import { CachedReader } from "./cache/reader";
import { detectEncoder } from "./transcode/encoders";
import { FfmpegRunner } from "./transcode/ffmpeg";
import { TranscodeRegistry } from "./transcode/registry";
import { TranscodeFiles } from "./transcode/server";
import { ChunkCache } from "./cache/store";
import { parseKey } from "./package/open";
import { PosterStore } from "./package/posters";
import { refreshCatalog } from "./package/refresh";
import { Telegram } from "./telegram/client";
import { TelegramSource } from "./telegram/source";

const config = load();
console.log("player:", describe(config));

/**
 * Refreshes the published catalog, and says which directory to read.
 *
 * `null` means there is no package configured and the index on this machine
 * is the catalog. A refresh that fails never stops the player: it keeps the
 * catalog it already had, and only a first run with nothing held is fatal.
 */
async function openCatalog(cfg: ReturnType<typeof load>): Promise<string | null> {
  if (cfg.packageUrl === null || cfg.packageKey === null) return null;

  const result = await refreshCatalog({
    baseUrl: cfg.packageUrl,
    key: parseKey(cfg.packageKey),
    root: cfg.catalogDir,
    supportedSchema: [EXPECTED_SCHEMA],
  });
  if (result.status === "kept") {
    console.log(`catalog: ${result.reason}`);
    if (result.dir === null) {
      throw new Error("no catalog: the package could not be read and none was held");
    }
    console.log("catalog: keeping the one already held");
  } else {
    console.log(`catalog: ${result.status} from ${cfg.packageUrl}`);
  }
  return result.dir;
}

// Where the catalog comes from. A published package makes the player
// independent of the uploader's filesystem: it fetches `latest.json`,
// decrypts what it names, and reads the index out of it. Without one it falls
// back to reading the index off this machine's disk, which is what a player
// running beside the uploader does.
const catalogDir = await openCatalog(config);

// One or the other is always set: `load()` requires a local index unless a
// package supplies the catalog, and `openCatalog` throws rather than return
// null when a configured package cannot be read and none is held.
const indexPath = catalogDir ? join(catalogDir, "library.db") : config.libraryDb;
if (indexPath === null) throw new Error("no catalog: neither a package nor a local index");

// Read-only: the player never writes, and a writable handle would let it
// checkpoint or migrate an index the uploader owns.
const db = new Database(indexPath, { readonly: true });
assertSchema(db);
const posters = new PosterStore(catalogDir);
console.log(
  `catalog: ${listPlayable(db).length} playable sets` +
    (catalogDir ? `, ${posters.count()} poster(s)` : ""),
);

const telegram = await Telegram.connect(config);

// A budget of zero turns caching off, which is a legitimate choice on a
// machine with no disk to spare.
const cache = config.cacheMaxBytes > 0 ? new ChunkCache(config.cacheDir, config.cacheMaxBytes) : null;
if (cache) {
  const held = await cache.sizeOnDisk();
  console.log(
    `cache: ${(held / 1024 ** 3).toFixed(2)} GB of ${(config.cacheMaxBytes / 1024 ** 3).toFixed(2)} GB in ${config.cacheDir}` +
      `, readahead ${config.cacheReadahead} chunk(s)`,
  );
} else {
  console.log("cache: disabled");
}

// Probed once here rather than at first play: `ffmpeg -encoders` lists what
// was compiled in, not what initialises, and discovering that when someone
// presses play is too late.
const encoder = await detectEncoder();
console.log(`encoder: ${encoder.name}${encoder.kind === "vaapi" ? ` on ${encoder.device}` : ""}`);

// Cleared on startup. ffmpeg dies with this process, so anything here is a
// previous run's segments: stale playlists that a restarted session would
// otherwise be handed, and directories nobody will ever delete.
await rm(config.transcodeDir, { recursive: true, force: true });

const transcodes = new TranscodeRegistry(
  config.transcodeDir,
  new FfmpegRunner({
    encoder,
    baseUrl: `http://127.0.0.1:${config.port}`,
    segmentSeconds: 2,
  }),
);
// Idle sessions hold an encoder and write segments nobody reads.
const reaper = setInterval(() => void transcodes.reapIdle(), 60_000);

const server = await startServer({
  db,
  hls: new TranscodeFiles(transcodes),
  source: new TelegramSource(
    telegram,
    cache ? new CachedReader(cache, config.cacheReadahead) : undefined,
  ),
  posters,
  port: config.port,
  hostname: config.hostname,
  trustProxy: config.trustProxy,
  maxBitrate: config.transcodeMaxrate,
});

const urls = reachableUrls(config.hostname, server.port);
console.log(`serving on ${urls[0]}`);
for (const url of urls.slice(1)) console.log(`          ${url}`);

if (isExposed(config.hostname)) {
  // This API has no authentication of its own. Anyone who can reach the port
  // can browse and stream the whole library, so say so rather than leaving it
  // to be discovered.
  console.log(
    "\n  ! Reachable from the network, and this API has no authentication.\n" +
      "    Anyone who can reach this port can stream the whole library.\n" +
      "    Put a reverse proxy in front of it before exposing it beyond a\n" +
      "    network you trust.\n",
  );
}

for (const signal of ["SIGINT", "SIGTERM"] as const) {
  process.on(signal, () => {
    void (async () => {
      console.log("\nstopping");
      clearInterval(reaper);
      // Before the server: an ffmpeg outlives its parent otherwise, and keeps
      // a hardware encoder session with it.
      await transcodes.stopAll();
      await server.close();
      await telegram.disconnect();
      db.close();
      process.exit(0);
    })();
  });
}
