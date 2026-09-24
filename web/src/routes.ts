/**
 * HTTP dispatch over a catalog snapshot. Feature routers own their policies;
 * server.ts writes these response descriptions without changing stream framing.
 */
import { EXPECTED_SCHEMA, playableSet } from "./catalog";
import { createCatalogRouter, type CatalogRouterOptions } from "./catalog/routes";
import type { CatalogEvents } from "./catalog-events";
import { preloadResponse } from "./cache/preload-route";
import type { SeriesPreload } from "./cache/series-preload";
import { isLocalAddress } from "./client-reach";
import { DEFAULT_MAX_BITRATE } from "./config";
import type { PlayerRequest, PlayerResponse } from "./http/contracts";
import { staticResponse } from "./http/static-files";
import { refuseUnsafeBrowserWrite } from "./http/browser-write";
import { streamSet, type ByteSource } from "./http/stream";
import { bodiless, withBody } from "./response";
import { createStateRouter } from "./state/routes";
import type { WatchState } from "./state/store";
import { beginTranscode, hlsResponse, type HlsServer } from "./transcode/routes";

const STREAM_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/stream$/;
const CACHED_STREAM_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/cached-stream$/;
const TRANSCODE_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/transcode$/;
// Both captures reach a filesystem path, so constrain their complete shape.
const HLS_PATH = /^\/hls\/([a-f0-9]{16})\/([A-Za-z0-9_-]{1,64}\.(?:m3u8|ts|m4s|mp4))$/;
const HLS_SESSION_PATH = /^\/hls\/([a-f0-9]{16})\/?$/;

/** Where the catalog came from; never exposes its URL or credentials. */
export interface CatalogOrigin {
  origin: "package" | "channel" | "local";
  /** Publication time in milliseconds; null for a local index. */
  publishedAt: number | null;
}

export interface RouterOptions extends CatalogRouterOptions {
  source: ByteSource;
  /** Optional disk-only source; absent support answers 404. */
  cacheSource?: ByteSource;
  catalog?: CatalogOrigin;
  /** Absent optional features answer 404, except transcode startup (501). */
  status?: (request: PlayerRequest) => Promise<PlayerResponse | null>;
  hls?: HlsServer;
  preload?: SeriesPreload;
  events?: CatalogEvents;
  state?: WatchState;
  /** Maximum remote-link bitrate, in bits per second. */
  maxBitrate?: number;
}

export function createRouter(options: RouterOptions) {
  const { db, source, hls } = options;
  // Each catalog swap builds these together; an in-flight request retains its
  // original router and database rather than mixing two catalog versions.
  const catalogRoute = createCatalogRouter(options);
  const stateRoute = options.state
    ? createStateRouter({ state: options.state, isPlayable: (id) => playableSet(db, id) !== null })
    : null;
  const maxBitrate = options.maxBitrate ?? DEFAULT_MAX_BITRATE;

  return async function route(request: PlayerRequest): Promise<PlayerResponse> {
    const state = stateRoute?.(request);
    if (state) return state;
    const status = await options.status?.(request);
    if (status) return status;

    if (request.method === "DELETE") {
      if (!request.path.startsWith("/hls/")) return bodiless(405);
      const session = HLS_SESSION_PATH.exec(request.path);
      if (!session || !hls) return bodiless(404);
      const refusal = refuseUnsafeBrowserWrite(request);
      if (refusal) return refusal;
      await hls.end(session[1]!);
      return bodiless(204);
    }
    if (request.path === "/api/preload") return preloadResponse(db, options.preload, request);
    if (request.method !== "GET" && request.method !== "HEAD") return bodiless(405);
    const headOnly = request.method === "HEAD";

    if (request.path === "/api/events") {
      if (!options.events) return bodiless(404);
      return {
        status: 200,
        headers: {
          "content-type": "text/event-stream",
          "cache-control": "no-cache",
          "x-accel-buffering": "no",
        },
        body: headOnly ? null : options.events.subscribe(),
      };
    }
    if (request.path === "/api/player") {
      return withBody(JSON.stringify({
        remote: !isLocalAddress(request.client ?? ""),
        maxBitrate,
        catalog: options.catalog ?? { origin: "local", publishedAt: null },
        schema: EXPECTED_SCHEMA,
      }), "application/json", { headOnly });
    }
    const streaming = STREAM_PATH.exec(request.path);
    if (streaming) return streamSet(db, source, request, streaming[1]!);
    const cached = CACHED_STREAM_PATH.exec(request.path);
    if (cached) return options.cacheSource
      ? streamSet(db, options.cacheSource, request, cached[1]!) : bodiless(404);

    const catalog = await catalogRoute(request);
    if (catalog) return catalog;
    const begin = TRANSCODE_PATH.exec(request.path);
    if (begin) return beginTranscode(db, hls, request, begin[1]!, maxBitrate);
    const file = HLS_PATH.exec(request.path);
    if (file) return hls ? hlsResponse(hls, file[1]!, file[2]!, request.method) : bodiless(404);
    if (request.path.startsWith("/hls/")) return bodiless(404);
    return staticResponse(request);
  };
}
