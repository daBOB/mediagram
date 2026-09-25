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
import { describe, load, type Config } from "./config";
import { EXPECTED_SCHEMA, assertSchema, listPlayable } from "./catalog";
import { startServer } from "./server";
import { SheetStore } from "./thumbs/sheets";
import { StateSync } from "./state/sync";
import { TelegramStateChannel } from "./telegram/state-channel";
import { listenForLibraryEvents } from "./telegram/channel-events";
import { isExposed, reachableUrls } from "./application/listen-address";
import { CachedReader } from "./cache/reader";
import { detectEncoder } from "./transcode/encoders";
import { FfmpegRunner } from "./transcode/ffmpeg";
import { TranscodeRegistry } from "./transcode/registry";
import { TranscodeFiles } from "./transcode/server";
import { ChunkCache } from "./cache/store";
import { HeldSets, expectedChunks } from "./cache/held";
import { AudioTrackReader } from "./catalog/audio-tracks";
import { WatchState } from "./state/store";
import { PosterStore } from "./package/posters";
import type { RefreshOptions } from "./package/refresh";
import { createStatusRouter } from "./status/routes";
import { dirBytes } from "./status/dir-bytes";
import type { StartupFacts } from "./status/facts";
import { Telegram, bareChannelId } from "./telegram/client";
import { TelegramSource, partFetcher } from "./telegram/source";
import { SeriesPreload } from "./cache/series-preload";
import { CatalogEvents } from "./catalog-events";
import { findNewestChannelIndex } from "./channel-index/find-newest-channel-index";
import { fetchPostersForIndex } from "./channel-index/fetch-posters-for-index";

import { openCatalog } from "./application/open-catalog";
import { CatalogFollower } from "./application/catalog-follow";
import { LibraryUpdates, announcingPulls, installShutdownSignals, shutdownFor, syncOnce, type ApplicationResources } from "./application/lifecycle";

/** Only external network, process, and subscription IO is replaceable. */
interface StartupIo {
  connect: typeof Telegram.connect;
  findIndex: typeof findNewestChannelIndex;
  detectEncoder: typeof detectEncoder;
  listen: typeof listenForLibraryEvents;
  fetchPosters: typeof fetchPostersForIndex;
  fetch?: RefreshOptions["fetch"];
}
const startupIo: StartupIo = {
  connect: (config) => Telegram.connect(config), findIndex: findNewestChannelIndex,
  detectEncoder, listen: listenForLibraryEvents, fetchPosters: fetchPostersForIndex,
};

/** Starts the real application; importing this module neither connects nor listens. */
export async function startPlayer(config: Config = load(), overrides: Partial<StartupIo> = {}) {
  const io = { ...startupIo, ...overrides };
  const resources: ApplicationResources = { timers: [] };
  const stop = shutdownFor(resources);
  try {
    console.log("player:", describe(config));

    // Connected before the catalog is chosen: without a package, the catalog is
    // whatever the channel last pinned.
    const telegram = await io.connect(config);
    resources.telegram = telegram;

    // Where the catalog comes from. A published package makes the player
    // independent of the uploader's filesystem: it fetches `latest.json`,
    // decrypts what it names, and reads the index out of it. Without one it
    // takes the channel's newest index instead, as the Android app does, and
    // reads the index on this disk only when neither can be had.
    const catalog = await openCatalog(config, () => io.findIndex(telegram), io.fetch);
    const catalogDir = catalog.dir;

    // One or the other is always set: `load()` requires a local index unless a
    // package supplies the catalog, and `openCatalog` throws rather than return
    // null when a configured package cannot be read and none is held.
    const indexPath = catalogDir ? join(catalogDir, "library.db") : config.libraryDb;
    if (indexPath === null) throw new Error("no catalog: neither a package nor a local index");

    // Read-only: the player never writes, and a writable handle would let it
    // checkpoint or migrate an index the uploader owns. CatalogFollower owns
    // this handle once the listener starts and replaces it when an index arrives.
    const db = new Database(indexPath, { readonly: true });
    resources.catalog = { close: () => db.close() };
    assertSchema(db);
    // Artwork lives beside the index when the index brought it: inside the
    // catalog a package unpacked, or next to the library on this machine, where
    // `mediagram posters` puts it. A channel snapshot is only `library.db`, so a
    // player following the channel reads the posters on this machine too.
    const posterDir =
      catalog.origin === "channel" && config.libraryDb !== null ? dirname(config.libraryDb) : dirname(indexPath);
    const posters = new PosterStore(posterDir);
    const playableCount = listPlayable(db).length;
    console.log(`catalog: ${playableCount} playable sets, ${posters.count()} poster(s)`);

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
    const encoder = await io.detectEncoder();
    console.log(`encoder: ${encoder.name}${encoder.kind === "vaapi" ? ` on ${encoder.device}` : ""}`);

    // Cleared on startup. ffmpeg dies with this process, so anything here is a
    // previous run's segments: stale playlists that a restarted session would
    // otherwise be handed, and directories nobody will ever delete.
    await rm(config.transcodeDir, { recursive: true, force: true });

    // Constructors perform no requests. The listener resolves before a request
    // can start media work, including when the OS chooses the port.
    let boundUrl: string | undefined;
    const endpoint = {
      get baseUrl(): string {
        if (boundUrl === undefined) throw new Error("the media listener is not bound");
        return boundUrl;
      },
    };
    const transcodes = new TranscodeRegistry(
      config.transcodeDir,
      new FfmpegRunner({
        encoder,
        get baseUrl() { return endpoint.baseUrl; },
        segmentSeconds: 2,
      }),
    );
    resources.transcodes = transcodes;
    // Idle sessions hold an encoder and write segments nobody reads.
    resources.timers.push(setInterval(() => {
      void transcodes.reapIdle().catch((error) => console.warn("transcode cleanup failed:", error));
    }, 60_000));

    // Reads a title's audio streams off the file, through this server's own Range
    // route, the first time a viewer opens it. The index cannot answer this: it
    // stores distinct language codes, not stream ordinals.
    const audio = new AudioTrackReader(endpoint);
    resources.audio = audio;

    // The one thing this process writes. A store that cannot be opened says so
    // and the player carries on without a memory, because a watch position is
    // not worth refusing to play a library over.
    const state = new WatchState(config.stateDb);
    resources.state = state;
    console.log(state.remembers ? `state: ${config.stateDb}` : "state: not remembered");

    /**
     * Sharing that state with this account's other devices, if asked.
     *
     * Off unless `MEDIAGRAM_SYNC_STATE` says otherwise. A player that uploads to
     * the channel on its own is a different kind of thing from one that only ever
     * reads it, and that should be a decision rather than a default somebody
     * discovers afterwards.
     *
     * Nothing here can stop the player: `StateSync.once` reports failures, and
     * the local database remains the source of truth for this machine.
     */
    // Where open pages hear that the library or another device's watch state
    // changed. Made before the sync so every round, whatever started it, can
    // say it took something.
    const events = new CatalogEvents();
    resources.events = events;

    const sync = announcingPulls(
      config.syncState && state.remembers
        ? new StateSync(state, new TelegramStateChannel(telegram), state.deviceId())
        : null,
      events,
    );

    resources.sync = sync;
    const updates = new LibraryUpdates(
      (onEvent) => io.listen(telegram.client, { channel: bareChannelId(config.chatId), ownDevice: state.deviceId() }, onEvent),
      sync,
    );
    resources.updates = updates;
    if (sync) console.log(`sync: ${state.deviceId()} every ${Math.round(config.syncEveryMs / 1000)}s`);
    await updates.start();
    if (sync) resources.timers.push(setInterval(() => void syncOnce(sync, "timer"), config.syncEveryMs));

    // The same facts the lines above printed, kept this time. Everything here was
    // already decided; none of it is worked out twice.
    const reader = cache ? new CachedReader(cache, config.cacheReadahead) : null;
    resources.reader = reader ?? undefined;
    const bytes = new TelegramSource(telegram, reader ?? undefined);

    // Which titles are on this disk in full, for the shelf's offline badge. The
    // expectation is folded again whenever the catalog is swapped, and the first
    // scan is awaited so the first page load is already right.
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
     * Admitted when `held` reports a complete set, then read through the disk-only
     * route: eviction during generation fails without reaching Telegram.
     * Without a cache there is nothing complete to make them from, so there are
     * no previews and the bar is what it always was.
     */
    const thumbs = held
      ? new SheetStore({
          directory: config.thumbsDir,
          get baseUrl() { return endpoint.baseUrl; },
          isHeld: (setId) => held.has(setId),
        })
      : undefined;
    resources.sheets = thumbs;

    /**
     * The next two episodes, taken into the cache while one plays.
     *
     * Needs the cache to have somewhere to put them and `held` to know when one
     * is already there; without either there is nothing to preload into.
     */
    const preload =
      config.seriesPreload && reader && held
        ? new SeriesPreload({
            fill: (setId, partIdx, partLength, fetch) => reader.fill(setId, partIdx, partLength, fetch),
            fetcherFor: (messageId) => partFetcher(telegram, messageId),
            isHeld: (setId) => held.check(setId),
            // So the shelf's offline badge follows at once, not a scan later.
            onHeld: () => held.refresh(),
            log: (line) => console.log(line),
          })
        : undefined;
    resources.preload = preload;
    console.log(`preload: next 2 episodes ${preload ? "on" : "off"}`);

    const server = await startServer({
      db,
      events,
      state,
      hls: new TranscodeFiles(transcodes),
      audio,
      source: bytes,
      cacheSource: reader ? { stream: (...args) => bytes.streamCached(...args) } : undefined,
      posters,
      thumbs,
      port: config.port,
      hostname: config.hostname,
      trustProxy: config.trustProxy,
      maxBitrate: config.transcodeMaxrate,
      catalog: { origin: catalog.origin, publishedAt: catalog.publishedAt },
      held: held ?? undefined,
      preload,
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

    boundUrl = server.baseUrl;
    resources.server = server;
    const follower = new CatalogFollower({
      db, catalog, server, facts, events, held: held ?? undefined,
      root: config.channelIndexDir,
      find: () => io.findIndex(telegram),
      fetchPosters: (index) => io.fetchPosters(config.postersCommand, index),
      posterCount: () => posters.count(),
    });
    resources.catalog = follower;
    const ready = updates.followCatalog(catalog.origin === "package" ? null : follower);
    if (catalog.origin === "channel" && catalog.dir !== null) void follower.refreshPosters(catalog.dir);

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

    return { server, stop, ready };
  } catch (error) {
    await stop().catch((cleanup) => console.error("startup cleanup failed:", cleanup));
    throw error;
  }
}

if (import.meta.main) {
  const player = await startPlayer();
  installShutdownSignals(player.stop);
}
