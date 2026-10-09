/**
 * Following a session: where it is now, whether it is still live, which edit
 * a screen follows, and what that screen must do to match it. Types are in
 * `follow.d.ts`.
 */

/** Further apart than this, a screen seeks. */
export const DRIFT_TOLERANCE_SECS = 0.5;
/** A playing session lasts this long past its title's end, then it is stale. */
const PAST_END_GRACE_SECS = 120;
const UNKNOWN_LENGTH_LIFETIME_MS = 6 * 60 * 60 * 1000;
const PAUSED_LIFETIME_MS = 3 * 60 * 60 * 1000;
/** A jump this big between two edits reads as a seek, not as playing on. */
const SEEK_NOTICE_SECS = 2;

export function expectedPosition(s, now) {
  return s.playing ? Math.max(0, s.pos + ((now - s.at) / 1000) * s.rate) : s.pos;
}

/**
 * Liveness comes from the state itself, with no keep-alive edits. A film nobody
 * pauses sends no edits for hours, and a keep-alive could overwrite a real
 * action sent in the same second.
 */
export function isLive(s, durationSecs, now) {
  if (s.ended) return false;
  if (!s.playing) return now - s.at < PAUSED_LIFETIME_MS;
  if (durationSecs == null) return now - s.at < UNKNOWN_LENGTH_LIFETIME_MS;
  return expectedPosition(s, now) <= durationSecs + PAST_END_GRACE_SECS;
}

/**
 * The session to follow after hearing `incoming`. A screen's own edit coming
 * back is old news while its current session is still that write. If another
 * screen's edit came in between, the echo arriving after it means this
 * screen's write landed last, and is the message now.
 */
export function follow(current, incoming, me) {
  const stillMine = current !== null && current.session === incoming.session && current.by === me;
  return incoming.by === me && stillMine ? current : incoming;
}

export function decide(own, s, now) {
  const pos = expectedPosition(s, now);
  if (own.set !== s.set) return [{ kind: "open", set: s.set, pos, playing: s.playing, rate: s.rate }];
  const steps = [];
  if (own.rate !== s.rate) steps.push({ kind: "rate", rate: s.rate });
  if (Math.abs(own.pos - pos) > DRIFT_TOLERANCE_SECS) steps.push({ kind: "seek", pos });
  if (own.playing !== s.playing) steps.push({ kind: s.playing ? "play" : "pause" });
  return steps;
}

/** What another screen did, for the two-second notice; `null` when nothing visible changed. */
export function changeOf(prev, next) {
  const label = [...next.members, ...prev.members].find((m) => m.screen === next.by)?.label ?? null;
  const had = new Set(prev.members.map((m) => m.screen));
  const has = new Set(next.members.map((m) => m.screen));
  let change = null;
  if (next.set !== prev.set) change = "title";
  else if (next.members.some((m) => !had.has(m.screen))) change = "joined";
  else if (prev.members.some((m) => !has.has(m.screen))) change = "left";
  else if (next.playing !== prev.playing) change = next.playing ? "played" : "paused";
  else if (next.rate !== prev.rate) change = "rate";
  else if (Math.abs(expectedPosition(prev, next.at) - next.pos) > SEEK_NOTICE_SECS) change = "sought";
  return change === null ? null : { change, label };
}
