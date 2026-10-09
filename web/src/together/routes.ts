/** `/api/together`: the browser's whole view of watch together. See `hub.ts`. */

import type { Database } from "bun:sqlite";
import { playableSet } from "../catalog";
import { jsonBody } from "../http/browser-write";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { bodiless } from "../response";
import { json } from "../state/route-shared";
import type { Playback } from "../../public/lib/together/moves.js";
import type { TogetherHub } from "./hub";

const UNREACHABLE = "Telegram isn't reachable";
const SCREEN = /^[A-Za-z0-9-]{1,64}$/;

type Body = Record<string, unknown>;

/** The playback fields a body carries, all of them when `whole`; `null` when one is wrong. */
function change(body: Body, db: Database, whole: boolean): Partial<Playback> | null {
  const out: Partial<Playback> = {};
  if (body.set !== undefined || whole) {
    if (typeof body.set !== "string" || playableSet(db, body.set) === null) return null;
    out.set = body.set;
  }
  if (body.playing !== undefined || whole) {
    if (typeof body.playing !== "boolean") return null;
    out.playing = body.playing;
  }
  if (body.pos !== undefined || whole) {
    if (typeof body.pos !== "number" || !Number.isFinite(body.pos) || body.pos < 0) return null;
    out.pos = body.pos;
  }
  if (body.rate !== undefined || whole) {
    if (typeof body.rate !== "number" || !(body.rate > 0 && body.rate <= 4)) return null;
    out.rate = body.rate;
  }
  return out;
}

export async function togetherRoute(request: PlayerRequest, hub: TogetherHub | undefined, db: Database): Promise<PlayerResponse | null> {
  if (request.path !== "/api/together") return null;
  if (!hub) return bodiless(404);
  const headOnly = request.method === "HEAD";
  const reply = (body: unknown, code = 200) => json(JSON.stringify(body), headOnly, code);
  const durationOf = (set: string) => playableSet(db, set)?.duration ?? null;
  if (request.method === "GET" || headOnly) {
    // A channel that cannot be read is as unreachable as no connection at all.
    const live = hub.available() ? await hub.live(durationOf).catch(() => undefined) : undefined;
    const available = live !== undefined;
    return reply({ available, reason: available ? null : UNREACHABLE, live: live ?? null, serverNow: Date.now() });
  }
  if (request.method !== "POST") return bodiless(405);
  if (!hub.available()) return reply({ reason: UNREACHABLE }, 503);
  const body = jsonBody(request.body);
  const screen = typeof body?.screen === "string" && SCREEN.test(body.screen) ? body.screen : null;
  if (!body || !screen) return bodiless(400);
  const me = { screen, label: "Browser" };
  const id = Number(body.id);
  try {
    if (body.op === "start") {
      const own = change(body, db, true);
      if (!own) return bodiless(400);
      const { live, conflict } = await hub.start(me, own as Playback, durationOf);
      return reply({ ...live, serverNow: Date.now() }, conflict ? 409 : 200);
    }
    if (!Number.isInteger(id) || id <= 0) return bodiless(400);
    if (body.op === "join") {
      const live = await hub.join(id, me);
      return live ? reply({ ...live, serverNow: Date.now() }) : bodiless(404);
    }
    if (body.op === "act") {
      const moved = typeof body.change === "object" && body.change !== null ? change(body.change as Body, db, false) : null;
      if (!moved) return bodiless(400);
      const session = hub.act(id, screen, moved);
      return session ? reply({ session, serverNow: Date.now() }) : bodiless(404);
    }
    if (body.op === "leave" || body.op === "end") {
      await (body.op === "leave" ? hub.leave(id, screen) : hub.end(id, screen));
      return bodiless(204);
    }
    return bodiless(400);
  } catch {
    return reply({ reason: UNREACHABLE }, 503);
  }
}
