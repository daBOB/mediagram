/** The body of a `/api/together` post, and the playback fields it may carry. */

import type { Database } from "bun:sqlite";
import { playableSet } from "../catalog";
import type { Playback } from "../../public/lib/together/moves.js";

export type Body = Record<string, unknown>;

/** The playback fields a body carries, all of them when `whole`; `null` when one is wrong. */
export function change(body: Body, db: Database, whole: boolean): Partial<Playback> | null {
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
