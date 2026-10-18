/** The cast ops of `/api/together`: offer, replace, cancel and tracks. See `cast-desk.ts`. */

import type { Database } from "bun:sqlite";
import type { PlayerResponse } from "../http/contracts";
import { bodiless } from "../response";
import { json } from "../state/route-shared";
import type { Member } from "../../public/lib/together/session.js";
import type { Playback } from "../../public/lib/together/moves.js";
import { change, type Body } from "./body";
import type { TogetherHub } from "./hub";

const UNREACHABLE = "Telegram isn't reachable";
const PROFILE_MAX = 128;
const TRACK_MAX = 64;

const reply = (body: unknown, code = 200) => json(JSON.stringify(body), false, code);
const text = (v: unknown, max: number): v is string => typeof v === "string" && v.length > 0 && v.length <= max;

/** `null` for any op that is not a cast op. Failures of the channel are the caller's to answer. */
export async function castRoute(
  body: Body, me: Member, id: number | null, hub: TogetherHub, db: Database, reachable: boolean,
): Promise<PlayerResponse | null> {
  switch (body.op) {
    case "offer":
    case "replace": {
      if (!reachable) return reply({ reason: UNREACHABLE }, 503);
      const own = change(body, db, true);
      if (!own || !text(body.profile, PROFILE_MAX) || (body.op === "replace" && id === null)) return bodiless(400);
      const answer = body.op === "offer"
        ? await hub.cast.offer(me, own as Playback, body.profile)
        : await hub.cast.replace(id!, me, own as Playback, body.profile);
      return reply({ ...answer, serverNow: Date.now() });
    }
    case "cancel":
      if (id === null) return bodiless(400);
      hub.cast.cancel(id);
      return bodiless(204);
    case "tracks": {
      const { audio, text: subtitle } = body;
      if (id === null || (audio === undefined && subtitle === undefined)) return bodiless(400);
      if ((audio !== undefined && !text(audio, TRACK_MAX)) || (subtitle !== undefined && !text(subtitle, TRACK_MAX))) return bodiless(400);
      const session = hub.cast.tracks(id, me.screen, {
        ...(audio === undefined ? {} : { audio: audio as string }),
        ...(subtitle === undefined ? {} : { text: subtitle as string }),
      });
      return session ? reply({ session, serverNow: Date.now() }) : bodiless(404);
    }
    default:
      return null;
  }
}
