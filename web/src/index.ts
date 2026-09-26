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
import { startBudget } from "./cache/budget";
import { HeldSets, expectedChunks } from "./cache/held";
import { AudioTrackReader } from "./catalog/audio-tracks";
import { WatchState } from "./state/store";
import { PosterStore } from "./package/posters";
import type { RefreshOptions } from "./package/refresh";
import { createStatusRouter } from "./status/routes";
import { dirBytes } from "./status/dir-bytes";
import { currentLink, readLiveFacts } from "./status/live-facts";
import { startLoopLag } from "./status/loop-lag";
import { PlaybackReports } from "./status/playback-reports";
import type { StartupFacts } from "./status/facts";
import { Telegram } from "./telegram/client";
import { TelegramConnection } from "./telegram/connection";
import { TelegramSource, connectionFetcher } from "./telegram/source";
import { SeriesPreload } from "./cache/series-preload";
import { CatalogEvents } from "./catalog-events";
import { findNewestChannelIndex, type FoundIndex } from "./channel-index/find-newest-channel-index";
import { fetchPostersForIndex } from "./channel-index/fetch-posters-for-index";
import type { NoIndex } from "./channel-index/pick-newest-index";

import { openCatalog } from "./application/open-catalog";
import { CatalogFollower } from "./application/catalog-follow";
import { ChannelState, UpdatesBinding } from "./application/telegram-binding";
import { announcingPulls, installShutdownSignals, shutdownFor, syncOnce, type ApplicationResources } from "./application/lifecycle";
import { WriteDebounce } from "./application/write-debounce";
import { readTelegramFile } from "./settings/telegram-file";
import { resolveTelegram } from "./settings/resolve-telegram";
import { resolveAdminToken, AdminGate } from "./settings/admin-gate";
import { SettingsRuntime } from "./settings/context";
import { createSettingsRouter } from "./settings/routes";
import type { PlayerRequest, PlayerResponse } from "./http/contracts";

/**
 * How long a player waits, after the last local write, before running a
 * sync round on its account. A few seconds: long enough to collapse a
 * burst — seeking, pausing, marking a title watched — into one round,
 * short enough that a change reaches another device quickly rather than
 * waiting for `syncEveryMs`.
 */
const WRITE_SYNC_DEBOUNCE_MS = 5_000;

/** Only external network, process, and subscription IO is replaceable. */
interface StartupIo {
  open: typeof Telegram.open;
  findIndex: (telegram: Telegram) => Promise<FoundIndex | NoIndex>;
  detectEncoder: typeof detectEncoder;
  listen: typeof listenForLibraryEvents;
  fetchPosters: typeof fetchPostersForIndex;
  fetch?: RefreshOptions["fetch"];
}
const startupIo: StartupIo = {
  open: (config) => Telegram.open(config), findIndex: findNewestChannelIndex,
  detectEncoder, listen: listenForLibraryEvents, fetchPosters: fetchPostersForIndex,
};

/** Starts the real application; importing this module neither connects nor listens. */
export async function startPlayer(config: Config = load(), overrides: Partial<StartupIo> = {}) {
  const io = { ...startupIo, ...overrides };
  const resources: ApplicationResources = { timers: [] };
  const stop = shutdownFor(resources);
  try {
    // A stored account beats the environment, which stays the bootstrap for
    // a player that has never opened Settings.
    const telegramFile = await readTelegramFile(config.telegramFilePath);
    config = resolveTelegram(config, telegramFile);
    console.log("player:", describe(config));

    // Signed out is a mode, not a startup failure: the catalog, cached
    // chunks and state on this disk are all still servable without a client.
    const connection = new TelegramConnection(await io.open(config));
    resources.telegram = { disconnect: async () => { await connection.current()?.disconnect(); } };
    console.log(connection.current() ? "telegram: connected" : "telegram: signed out");

    // Where the catalog comes from. A published package makes the player
    // independent of the uploader's filesystem: it fetches `latest.json`,
    // decrypts what it names, and reads the index out of it. Without one it
    // takes the channel's newest index instead, as the Android app does, and
    // reads the index on this disk only when neither can be had.
    const findViaConnection = () => connection.ready().then((t) => (t ? io.findIndex(t) : "nothing-pinned" as const));
    const catalog = await openCatalog(config, findViaConnection, io.fetch);
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

    // The one thing this process writes. A store that cannot be opened says so
    // and the player carries on without a memory, because a watch position is
    // not worth refusing to play a library over.
    const state = new WatchState(config.stateDb);
    resources.state = state;
    console.log(state.remembers ? `state: ${config.stateDb}` : "state: not remembered");

    // A stored budget beats `MEDIAGRAM_CACHE_MAX`; env `0` (and no stored
    // value) still turns caching off entirely, exactly as before Settings.
    const cacheMaxBytes = startBudget(state.settings(), config.cacheMaxBytes);
    const cache = cacheMaxBytes > 0 ? new ChunkCache(config.cacheDir, cacheMaxBytes) : null;

    // None of the three below need Telegram or each other, and none of their
    // results are needed until the lines that log or use them: a cold count
    // of the cache (a full stat of every chunk file), probing what ffmpeg can
    // encode with, and clearing a previous run's leftovers. Run together
    // rather than one after another so a restart is not the sum of three
    // waits nobody is blocked on until here.
    const [cacheBytes, encoder] = await Promise.all([
      cache ? cache.sizeOnDisk() : Promise.resolve(null),
      io.detectEncoder(),
      rm(config.transcodeDir, { recursive: true, force: true }),
    ]);

    if (cache) {
      console.log(
        `cache: ${(cacheBytes! / 1024 ** 3).toFixed(2)} GB of ${(cacheMaxBytes / 1024 ** 3).toFixed(2)} GB in ${config.cacheDir}` +
          `, readahead ${config.cacheReadahead} chunk(s)`,
      );
    } else {
      console.log("cache: disabled");
    }
    // Probed here rather than at first play: `ffmpeg -encoders` lists what was
    // compiled in, not what initialises, and discovering that when someone
    // presses play is too late.
    console.log(`encoder: ${encoder.name}${encoder.kind === "vaapi" ? ` on ${encoder.device}` : ""}`);

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
        ? new StateSync(state, new TelegramStateChannel(connection), state.deviceId())
        : null,
      events,
    );
    resources.sync = sync;

    /**
     * A local write reaches another device sooner than the timer, without a
     * second sync path: a few seconds of quiet after the last one runs the
     * same round the timer, a push, and shutdown all already share.
     */
    const writeDebounce = sync ? new WriteDebounce(() => void syncOnce(sync, "write"), WRITE_SYNC_DEBOUNCE_MS) : null;
    resources.writeDebounce = writeDebounce ?? undefined;

    // The channel this player follows for its catalog and its watch-state
    // sync, mutable across a library switch from Settings. Subscribed here,
    // before the follower or the server exist: a listener bound and later
    // torn down on a startup failure must not depend on how far startup got.
    const channel = new ChannelState(config.chatId, config.channelAccessHash, telegramFile?.title ?? null);
    const updatesBinding = new UpdatesBinding(connection, channel, state.deviceId(), sync, null, io.listen);
    resources.updates = { stop: () => updatesBinding.stop() };
    await updatesBinding.start();

    if (sync) {
      console.log(`sync: ${state.deviceId()} every ${Math.round(config.syncEveryMs / 1000)}s`);
      resources.timers.push(setInterval(() => void syncOnce(sync, "timer"), config.syncEveryMs));
    }

    const reader = cache ? new CachedReader(cache, config.cacheReadahead) : null;
    resources.reader = reader ?? undefined;
    const bytes = new TelegramSource(connection, reader ?? undefined);

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
      runtime: { bun: Bun.version },
    };

    // Windowed rather than averaged since startup, so one hiccup at boot does
    // not stay in the figure forever. Scheduled through `resources.timers` so
    // shutdown clears it the same way as every other interval here.
    const loopLag = startLoopLag({
      schedule: (rotate, ms) => {
        const timer = setInterval(rotate, ms).unref();
        resources.timers.push(timer);
        return timer;
      },
    });
    // What each open player says about itself, from any device on the household.
    const playbackReports = new PlaybackReports();

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
            fetcherFor: (messageId) => connectionFetcher(connection, messageId),
            isHeld: (setId) => held.check(setId),
            // So the shelf's offline badge follows at once, not a scan later.
            onHeld: () => held.refresh(),
            log: (line) => console.log(line),
          })
        : undefined;
    resources.preload = preload;
    console.log(`preload: next 2 episodes ${preload ? "on" : "off"}`);

    // Settings, gated behind this household's network and an admin token
    // (`docs/system-architecture.md` §7). Built before the router, since the
    // router needs it, and after everything it touches — cache, held, facts.
    const adminToken = await resolveAdminToken(process.env, config.adminTokenPath);
    const gate = new AdminGate(adminToken);
    const statusHeldByteInvalidation = { current: () => {} };
    // Filled in once `runtime` exists, after `follower` — both need the
    // running server. `createRouter` reads through this box on every
    // request, so the settings route becomes live without rebuilding the
    // router the way a catalog swap does.
    const settingsBox: { route: ((request: PlayerRequest) => Promise<PlayerResponse | null>) | null } = { route: null };

    const server = await startServer({
      db,
      events,
      state,
      onWrite: () => writeDebounce?.touch(),
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
      status: (() => {
        const statusRouter = createStatusRouter({
          facts,
          live: () => readLiveFacts({
            cache, reader: reader ?? null, transcodes, telegram: currentLink(connection), bytes, loopLag,
            diskDirs: [config.cacheDir, config.transcodeDir], playback: playbackReports,
          }),
          heldBytes: cache ? () => cache.sizeOnDisk() : undefined,
          transcodeBytes: () => dirBytes(config.transcodeDir),
          playback: playbackReports,
        });
        statusHeldByteInvalidation.current = () => statusRouter.invalidateHeldBytes();
        return statusRouter;
      })(),
      settings: (request) => settingsBox.route?.(request) ?? Promise.resolve(null),
    });

    boundUrl = server.baseUrl;
    resources.server = server;
    const follower = new CatalogFollower({
      db, catalog, server, facts, events, held: held ?? undefined,
      root: config.channelIndexDir,
      find: findViaConnection,
      fetchPosters: (index) => io.fetchPosters(config.postersCommand, index),
      posterCount: () => posters.count(),
    });
    resources.catalog = follower;

    updatesBinding.setFollower(catalog.origin === "package" ? null : follower);
    const ready = updatesBinding.followCatalog();
    if (catalog.origin === "channel" && catalog.dir !== null) void follower.refreshPosters(catalog.dir);

    const runtime = new SettingsRuntime(
      {
        connection, channel, updatesBinding, follower, facts, settings: state.settings(),
        cache: cache ?? null, held: held ?? undefined,
        invalidateHeldBytes: () => statusHeldByteInvalidation.current(),
        channelCatalogDir: config.channelCatalogDir,
        telegramFilePath: config.telegramFilePath,
      },
      { apiId: config.apiId, apiHash: config.apiHash },
      null,
    );
    settingsBox.route = createSettingsRouter({
      gate,
      runtime,
      secure: (request) => Boolean(config.trustProxy) && request.host !== null,
    });

    const urls = reachableUrls(config.hostname, server.port);
    console.log(`serving on ${urls[0]}`);
    for (const url of urls.slice(1)) console.log(`          ${url}`);

    if (isExposed(config.hostname)) {
      // This API has no authentication beyond Settings' own gate. Anyone who
      // can reach the port can browse and stream the whole library, so say so
      // rather than leaving it to be discovered.
      console.log(
        "\n  ! Reachable from the network. Settings is admin-gated, but the\n" +
          "    catalog and streaming API are not.\n" +
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
