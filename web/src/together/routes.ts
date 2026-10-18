/** `/api/together`: the browser's whole view of watch together. See `hub.ts`. */

import type { Database } from "bun:sqlite";
import { playableSet } from "../catalog";
import { jsonBody } from "../http/browser-write";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { bodiless } from "../response";
import { json } from "../state/route-shared";
import type { Playback } from "../../public/lib/together/moves.js";
import type { TogetherHub } from "./hub";
import { change, type Body } from "./body";
import { castRoute } from "./cast-routes";

const UNREACHABLE = "Telegram isn't reachable";
const SCREEN = /^[A-Za-z0-9-]{1,64}$/;

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
  // A start or a join reads the channel; a move is queued, and sent once Telegram is back.
  const reachable = hub.available();
  const body = jsonBody(request.body);
  const screen = typeof body?.screen === "string" && SCREEN.test(body.screen) ? body.screen : null;
  if (!body || !screen) return bodiless(400);
  const me = { screen, label: "Browser", remote: false };
  const id = Number(body.id);
  try {
    if ((body.op === "start" || body.op === "join") && !reachable) return reply({ reason: UNREACHABLE }, 503);
    if (body.op === "start") {
      const own = change(body, db, true);
      if (!own) return bodiless(400);
      const { live, conflict } = await hub.start(me, own as Playback, durationOf);
      return reply({ ...live, serverNow: Date.now() }, conflict ? 409 : 200);
    }
    const cast = await castRoute(body, me, Number.isInteger(id) && id > 0 ? id : null, hub, db, reachable);
    if (cast) return cast;
    if (!Number.isInteger(id) || id <= 0) return bodiless(400);
    if (body.op === "join") {
      const live = await hub.join(id, { ...me, remote: body.remote === true });
      return live ? reply({ ...live, serverNow: Date.now() }) : bodiless(404);
    }
    if (body.op === "act") {
      const moved = typeof body.change === "object" && body.change !== null ? change(body.change as Body, db, false) : null;
      if (!moved) return bodiless(400);
      const session = hub.act(id, screen, moved);
      // Unknown while Telegram can't be reached may be a server that restarted: the tab plays on alone.
      if (!session) return reachable ? bodiless(404) : reply({ reason: UNREACHABLE }, 503);
      return reply({ session, serverNow: Date.now(), reason: reachable ? null : UNREACHABLE });
    }
    if (body.op === "leave" || body.op === "end") {
      // Answered once queued: the sender delivers it, after any flood wait.
      if (body.op === "leave") hub.leave(id, screen); else hub.end(id, screen);
      return bodiless(204);
    }
    return bodiless(400);
  } catch {
    return reply({ reason: UNREACHABLE }, 503);
  }
}
