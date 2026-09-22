/**
 * The player backend: catalog and bytes, over HTTP.
 *
 * This process speaks MTProto and holds the session. The browser is an
 * ordinary HTTP client of it and is told what it may play, never where the
 * bytes live.
 */

import { Database } from "bun:sqlite";
import { rm } from "node:fs/promises";
import { dirname, join } from "node:path";
import { describe, load } from "./config";
import { EXPECTED_SCHEMA, assertSchema, listPlayable } from "./catalog";
import { startServer } from "./server";
import { SheetStore } from "./thumbs/sheets";
import { StateSync } from "./state/sync";
import { TelegramStateChannel } from "./telegram/state-channel";
import { listenForLibraryEvents } from "./telegram/channel-events";
import { isExposed, reachableUrls } from "./listen-address";
import { CachedReader } from "./cache/reader";
import { detectEncoder } from "./transcode/encoders";
import { FfmpegRunner } from "./transcode/ffmpeg";
import { TranscodeRegistry } from "./transcode/registry";
import { TranscodeFiles } from "./transcode/server";
import { ChunkCache } from "./cache/store";
import { HeldSets, expectedChunks } from "./cache/held";
import { AudioTrackReader } from "./audio-tracks";
import { WatchState } from "./state/store";
import { parseKey } from "./package/open";
import { PosterStore } from "./package/posters";
import { refreshCatalog } from "./package/refresh";
import { createStatusRouter } from "./status/routes";
import { dirBytes } from "./status/dir-bytes";
import type { StartupFacts } from "./status/facts";
import { Telegram, bareChannelId } from "./telegram/client";
import { TelegramSource } from "./telegram/source";

const config = load();
console.log("player:", describe(config));

/**
 * Which catalog is open, and where it came from.
 *
 * A `dir` of `null` means there is no package configured and the index on
 * this machine is the catalog. The rest is what the player is asked about
 * afterwards — by the colophon, which says how old the catalogue is, and by
 * the status route, which says whether the last refresh actually worked.
 */
interface OpenedCatalog {
  dir: string | null;
  origin: "package" | "local";
  /** When the package was built, in milliseconds. `null` for a local index. */
  publishedAt: number | null;
  /** The verdict of this run's refresh, or `null` when none was attempted. */
  refresh: "updated" | "unchanged" | "kept" | null;
  /** Why a refresh was refused, when it was. */
  reason: string | null;
}

/**
 * Refreshes the published catalog, and says which directory to read.
 *
 * A refresh that fails never stops the player: it keeps the catalog it
 * already had, and only a first run with nothing held is fatal.
 */
async function openCatalog(cfg: ReturnType<typeof load>): Promise<OpenedCatalog> {
  if (cfg.packageUrl === null || cfg.packageKey === null) {
    return { dir: null, origin: "local", publishedAt: null, refresh: null, reason: null };
  }

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
  return {
    dir: result.dir,
    origin: "package",
    // Seconds in the package, milliseconds everywhere a browser will read it.
    publishedAt: result.identity ? result.identity.created_at * 1000 : null,
    refresh: result.status,
    reason: result.reason ?? null,
  };
}

// Where the catalog comes from. A published package makes the player
// independent of the uploader's filesystem: it fetches `latest.json`,
// decrypts what it names, and reads the index out of it. Without one it falls
// back to reading the index off this machine's disk, which is what a player
// running beside the uploader does.
const catalog = await openCatalog(config);
const catalogDir = catalog.dir;

// One or the other is always set: `load()` requires a local index unless a
// package supplies the catalog, and `openCatalog` throws rather than return
// null when a configured package cannot be read and none is held.
const indexPath = catalogDir ? join(catalogDir, "library.db") : config.libraryDb;
if (indexPath === null) throw new Error("no catalog: neither a package nor a local index");

// Read-only: the player never writes, and a writable handle would let it
// checkpoint or migrate an index the uploader owns.
const db = new Database(indexPath, { readonly: true });
assertSchema(db);
// Artwork lives beside the index, whichever index this is: inside the
// catalog a package unpacked, or next to the library on this machine, where
// `mediagram posters` puts it. A player reading a local index used to have no
// artwork at all, because posters only ever shipped inside a package.
const posters = new PosterStore(dirname(indexPath));
// Counted once: the catalog is read-only for the life of the process, so the
// startup line and the status route are reporting the same unchanging number.
const playableCount = listPlayable(db).length;
console.log(`catalog: ${playableCount} playable sets, ${posters.count()} poster(s)`);

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

// Reads a title's audio streams off the file, through this server's own Range
// route, the first time a viewer opens it. The index cannot answer this: it
// stores distinct language codes, not stream ordinals.
const audio = new AudioTrackReader(`http://127.0.0.1:${config.port}`);

// The one thing this process writes. A store that cannot be opened says so
// and the player carries on without a memory, because a watch position is
// not worth refusing to play a library over.
const state = new WatchState(config.stateDb);
console.log(state.remembers ? `state: ${config.stateDb}` : "state: not remembered");

/**
 * Sharing that state with this account's other devices, if asked.
 *
 * Off unless `MEDIAGRAM_SYNC_STATE` says otherwise. A player that uploads to
 * the channel on its own is a different kind of thing from one that only ever
 * reads it, and that should be a decision rather than a default somebody
 * discovers afterwards.
 *
 * Nothing here can stop the player: `StateSync.once` does not throw, and a
 * round that fails leaves the local database — which remains the source of
 * truth for this machine — exactly as it was.
 */
const sync =
  config.syncState && state.remembers
    ? new StateSync(state, new TelegramStateChannel(telegram), state.deviceId())
    : null;

async function syncOnce(why: string): Promise<void> {
  if (!sync) return;
  const outcome = await sync.once();
  if (outcome.failed !== undefined) console.warn(`sync (${why}): ${outcome.failed}`);
  else if (outcome.pulled > 0 || outcome.pushed) {
    console.log(`sync (${why}): took ${outcome.pulled}, ${outcome.pushed ? "sent" : "sent nothing"}`);
  }
}

// Another device's write reaches this one in milliseconds instead of at the
// next timer. Subscribed before the first round, which covers whatever was
// written before the subscription existed. A new index is only noted: this
// player's catalog comes from the published package, not the channel.
const stopListening = sync
  ? listenForLibraryEvents(telegram.client, { channel: bareChannelId(config.chatId), ownDevice: state.deviceId() }, (event) => {
      if (event === "state") void syncOnce("push");
      else console.log("library: the channel pinned a new index");
    })
  : null;

// Awaited, so the first page load already shows what the other devices knew
// rather than showing this machine's answer and correcting it a moment later.
if (sync) {
  console.log(`sync: ${state.deviceId()} every ${Math.round(config.syncEveryMs / 1000)}s`);
  await syncOnce("start");
}
const syncTimer = sync ? setInterval(() => void syncOnce("timer"), config.syncEveryMs) : null;

// The same facts the lines above printed, kept this time. Everything here was
// already decided; none of it is worked out twice.
const reader = cache ? new CachedReader(cache, config.cacheReadahead) : null;
const bytes = new TelegramSource(telegram, reader ?? undefined);

// Which titles are on this disk in full, for the shelf's offline badge. The
// expectation is folded once — the catalog cannot change while we run — and
// the first scan is awaited so the first page load is already right.
const held = cache ? new HeldSets(config.cacheDir, expectedChunks(db)) : null;
if (held) {
  await held.refresh();
  console.log(`held: ${held.count} title(s) cached in full`);
}
const facts: StartupFacts = {
  catalog: {
    origin: catalog.origin,
    publishedAt: catalog.publishedAt,
    refresh: catalog.refresh,
    reason: catalog.reason,
    schema: EXPECTED_SCHEMA,
    sets: playableCount,
    posters: posters.count(),
  },
  encoder: {
    name: encoder.name,
    kind: encoder.kind,
    device: encoder.kind === "vaapi" ? encoder.device : null,
  },
  transcodeDir: config.transcodeDir,
  cache: cache
    ? { dir: config.cacheDir, budget: cache.budget, readahead: config.cacheReadahead }
    : null,
  state: { remembered: state.remembers, path: state.remembers ? config.stateDb : null },
  startedAt: Date.now(),
};

/**
 * Preview frames for the scrub bar.
 *
 * Only ever made from sets `held` reports as complete, so generating one never
 * reaches Telegram — see `thumbs/sheets.ts`. Without a cache there is nothing
 * complete to make them from, so there are no previews and the bar is what it
 * always was.
 */
const thumbs = held
  ? new SheetStore({
      directory: config.thumbsDir,
      baseUrl: `http://127.0.0.1:${config.port}`,
      isHeld: (setId) => held.has(setId),
    })
  : undefined;

const server = await startServer({
  db,
  state,
  hls: new TranscodeFiles(transcodes),
  audio,
  source: bytes,
  posters,
  thumbs,
  port: config.port,
  hostname: config.hostname,
  trustProxy: config.trustProxy,
  maxBitrate: config.transcodeMaxrate,
  catalog: { origin: catalog.origin, publishedAt: catalog.publishedAt },
  held: held ?? undefined,
  status: createStatusRouter({
    facts,
    live: () => {
      const stats = cache?.stats();
      return {
      cacheHits: stats?.hits ?? 0,
      cacheMisses: stats?.misses ?? 0,
      cacheEvicted: stats?.evicted ?? 0,
      fetchedBytes: reader?.stats().fetchedBytes ?? 0,
      transcodes: {
        running: transcodes.count(),
        capacity: transcodes.capacity,
        sessions: transcodes.list().map(({ setId, seekSeconds, maxrateBits, audioTrack, watchers }) => ({
          setId,
          seekSeconds,
          maxrateBits,
          audioTrack,
          watchers,
        })),
      },
      telegramConnected: telegram.connected,
      failedReads: bytes.stats().failedReads,
      // Resident set size: the figure that says whether a player left running
      // for a week is still the size it started at.
      memoryBytes: process.memoryUsage.rss(),
      };
    },
    heldBytes: cache ? () => cache.sizeOnDisk() : undefined,
    transcodeBytes: () => dirBytes(config.transcodeDir),
  }),
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
      if (syncTimer !== null) clearInterval(syncTimer);
      stopListening?.();
      // One last round before the session goes: the position from the title
      // that was playing when this was interrupted is the one most worth
      // having on the other machine.
      await syncOnce("stopping");
      // Before the server: an ffmpeg outlives its parent otherwise, and keeps
      // a hardware encoder session with it.
      await transcodes.stopAll();
      await server.close();
      state.close();
      await telegram.disconnect();
      db.close();
      process.exit(0);
    })();
  });
}
