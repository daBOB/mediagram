/** The clip export routes: start, poll, list, and download. */

import type { Database } from "bun:sqlite";
import { existsSync } from "node:fs";

import { playableSet } from "../catalog";
import { jsonBody } from "../http/browser-write";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { bodiless, withBody } from "../response";
import { json, P } from "../state/route-shared";
import type { WatchState } from "../state/store";
import { titleLine } from "../../public/lib/format.js";
import { CLIP_MAX_SECONDS, type ClipHdr } from "./clip-args";
import { CLIP_BUSY, type ClipJobs } from "./clip-jobs";

const PROFILE_CLIPS = new RegExp(`^/api/profiles/${P}/clips$`);
const CLIP = /^\/api\/clips\/([a-f0-9]{16})$/;
const CLIP_FILE = /^\/api\/clips\/([a-f0-9]{16})\/file$/;

/** RFC 5987 leaves `'()*` bare, which `encodeURIComponent` also does. */
const encodeName = (name: string) =>
  encodeURIComponent(name).replace(/['()*]/g, (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);

function start(profile: string, request: PlayerRequest, jobs: ClipJobs, db: Database, state: WatchState | undefined): PlayerResponse {
  const said = jsonBody(request.body);
  const set = typeof said?.setId === "string" ? playableSet(db, said.setId) : null;
  if (!said || !state?.has(profile) || !set) return bodiless(said ? 404 : 400);
  const { from, to, audio } = said;
  if (typeof from !== "number" || typeof to !== "number" || !Number.isFinite(from) || !Number.isFinite(to)) return bodiless(400);
  if (from < 0 || to - from < 1 || to - from > CLIP_MAX_SECONDS) return bodiless(400);
  if (typeof audio !== "number" || !Number.isInteger(audio) || audio < 0) return bodiless(400);
  const started = jobs.start({
    profileId: profile, setId: set.setId, title: titleLine(set), from, to, audioTrack: audio, hdr: set.hdr as ClipHdr,
  });
  if (started === "busy") return json(JSON.stringify({ reason: CLIP_BUSY }), false, 409);
  return json(JSON.stringify({ id: started.id }), false, 202);
}

function download(id: string, request: PlayerRequest, jobs: ClipJobs): PlayerResponse {
  const file = jobs.file(id);
  // A sweep or shutdown can remove the file after the job was last looked at.
  if (!file || !existsSync(file.path)) return bodiless(404);
  if (file.profileId !== request.profile) return bodiless(403);
  const bun = Bun.file(file.path);
  return {
    status: 200,
    headers: {
      "content-type": "video/mp4",
      "content-length": String(bun.size),
      "content-disposition": `attachment; filename*=UTF-8''${encodeName(file.name)}`,
    },
    body: request.method === "HEAD" ? null : bun.stream(),
  };
}

/** Answers a clip route, or `null` when the path is not one. */
export function clipRoute(
  request: PlayerRequest, jobs: ClipJobs | undefined, db: Database, state: WatchState | undefined,
): PlayerResponse | null {
  const { method, path } = request;
  const forProfile = PROFILE_CLIPS.exec(path);
  const one = CLIP.exec(path);
  const file = CLIP_FILE.exec(path);
  if (!forProfile && !one && !file) return null;
  if (!jobs) return bodiless(404);

  if (forProfile) {
    if (method === "POST") return start(forProfile[1]!, request, jobs, db, state);
    if (method !== "GET") return bodiless(405);
    return json(JSON.stringify(jobs.ready(forProfile[1]!, request.setId ?? "")), false);
  }
  if (method !== "GET" && method !== "HEAD") return bodiless(405);
  if (file) return download(file[1]!, request, jobs);
  const status = jobs.status(one![1]!);
  return status ? withBody(JSON.stringify(status), "application/json", { headOnly: method === "HEAD" }) : bodiless(404);
}
