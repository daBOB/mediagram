/**
 * What the clip panel decides, apart from the elements it decides it about.
 *
 * Pure so the two things worth getting right — where a clip starts and when a
 * finished one is fetched — can be proved without a browser or a server.
 */

/** Instagram's Reel limit; the server refuses more, so the panel says so first. */
export const CLIP_MAX_SECONDS = 180;
/** The shortest the server will cut, the same second a loop needs. */
const CLIP_MIN_SECONDS = 1;
/** Either side of the playhead: a scene worth marking is rarely shorter than this. */
const AROUND = 15;

/** A range centred on `at`, kept inside the film. */
export function presetAround(at, runtime) {
  return { from: Math.max(0, at - AROUND), to: Math.min(runtime ?? Infinity, at + AROUND) };
}

/** Why this range cannot be exported, or `null` when it can. An unset end is a viewer still picking. */
export function clipProblem(from, to) {
  if (from === null || to === null) return "Seek, then set Start and End";
  const length = to - from;
  if (length > CLIP_MAX_SECONDS) return "Clips can be up to 3 minutes";
  return length < CLIP_MIN_SECONDS ? "Clips need at least a second" : null;
}

/**
 * What to do about the job now.
 *
 * A hidden tab neither polls nor downloads — browsers throttle the one and
 * drop the other — so it waits, and the return to visible asks again. A file
 * is fetched once: the same answer on every later poll would download it again.
 */
export function nextClipStep(job, { visible, downloaded }) {
  if (job === null) return "idle";
  if (job.state === "running") return visible ? "poll" : "wait";
  if (job.state !== "done" || downloaded.has(job.id)) return "idle";
  return visible ? "download" : "wait";
}
