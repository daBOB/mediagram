/**
 * What this player is doing, for whoever is standing in front of it.
 *
 * Kept apart from `routes.ts` for the reason `state/routes.ts` is: that
 * module is already twice the size the rest of this codebase holds itself to,
 * and a surface with its own access rule is exactly the kind of thing that
 * should not be remembered halfway down it.
 *
 * **The rule is local-only, and the refusal is a 404.** This API has no
 * authentication of its own — anyone who can reach the port can stream the
 * whole library — and a 403 would confirm to a caller from outside that there
 * is something here worth the next request. There is nothing to confirm.
 */

import type { PlayerRequest, PlayerResponse } from "../routes";
import { isLocalAddress } from "../client-reach";
import { buildSnapshot, type LiveFacts } from "./snapshot";
import type { StartupFacts } from "./facts";

const STATUS = /^\/api\/status$/;

/**
 * How long a cache measurement is reused.
 *
 * Counting bytes on disk means statting every chunk, and a twenty-gigabyte
 * cache is some forty thousand of them. The panel polls every two seconds;
 * doing that scan on each poll would make watching the player the most
 * expensive thing the player does. The number moves slowly enough that a
 * reading a few seconds old is the same reading.
 */
const HELD_BYTES_TTL_MS = 15_000;

export interface StatusRouterOptions {
  facts: StartupFacts;
  /** Everything that has to be read at the moment of asking. */
  live: () => Omit<LiveFacts, "cacheHeldBytes" | "transcodeBytes" | "now">;
  /** Bytes the cache holds, measured by scanning. Absent when caching is off. */
  heldBytes?: () => Promise<number>;
  /** Bytes the conversions hold, measured the same way. */
  transcodeBytes?: () => Promise<number>;
  now?: () => number;
}

function json(body: string, bodiless: boolean): PlayerResponse {
  const bytes = new TextEncoder().encode(body);
  return {
    status: 200,
    headers: {
      "content-type": "application/json",
      "content-length": String(bytes.byteLength),
      // A reading is true for the instant it was taken and no longer.
      "cache-control": "no-store",
    },
    body: bodiless ? null : bytes,
  };
}

function status(code: number): PlayerResponse {
  return { status: code, headers: { "content-length": "0" }, body: null };
}

/**
 * Answers a status request, or `null` when the path is not this one.
 *
 * `null` rather than a 404 so the caller can go on to its own routes; this
 * module knows about its own path and nothing else.
 */
export function createStatusRouter(options: StatusRouterOptions) {
  const { facts, live, heldBytes } = options;
  const now = options.now ?? (() => Date.now());

  /**
   * Wraps a directory scan so a poll every two seconds does not cause one.
   *
   * Two things share this: the cache, whose thousands of chunks are the
   * expensive case, and the transcode directory. A scan in flight is shared
   * rather than started again, so a burst of polls costs one.
   *
   * A scan that fails answers with the last reading rather than failing the
   * whole request — a directory that cannot be measured is still a working
   * directory, and the rest of the snapshot is unaffected by it.
   */
  function memoizedScan(scan?: () => Promise<number>) {
    let held: { bytes: number; at: number } | null = null;
    let measuring: Promise<number> | null = null;

    return async function read(): Promise<number | null> {
      if (!scan) return null;
      if (held && now() - held.at < HELD_BYTES_TTL_MS) return held.bytes;
      if (!measuring) {
        measuring = scan()
          .then((bytes) => {
            held = { bytes, at: now() };
            return bytes;
          })
          .catch(() => held?.bytes ?? 0)
          .finally(() => {
            measuring = null;
          });
      }
      return measuring;
    };
  }

  const cacheHeldBytes = memoizedScan(heldBytes);
  const transcodeHeldBytes = memoizedScan(options.transcodeBytes);

  return async function statusRoute(request: PlayerRequest): Promise<PlayerResponse | null> {
    if (!STATUS.test(request.path)) return null;

    // Before the method check, so a caller from outside cannot learn the
    // difference between "wrong method here" and "nothing here".
    if (!isLocalAddress(request.client ?? "")) return status(404);

    const reading = request.method === "GET" || request.method === "HEAD";
    if (!reading) return status(405);

    // Both scans at once: they are independent, and one after the other would
    // make the slow case the sum of two directory walks rather than the
    // longer of them.
    const [cached, transcoded] = await Promise.all([cacheHeldBytes(), transcodeHeldBytes()]);
    const snapshot = buildSnapshot(facts, {
      ...live(),
      cacheHeldBytes: cached,
      transcodeBytes: transcoded,
      now: now(),
    });
    return json(JSON.stringify(snapshot), request.method === "HEAD");
  };
}
