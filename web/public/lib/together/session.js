/**
 * The watch-together session message: `#mlib-together v=2` on the first line
 * and its JSON on the second. `v=1` messages from older screens still read,
 * as sessions with no remote members and no cast. Each session is one unpinned message in the
 * library channel, edited in place, and the last edit wins.
 * `android/core/model/src/main/kotlin/together/` is the Kotlin port, held to
 * this by `web/test/fixtures/together/`. Types are in `session.d.ts`.
 */

export const TOGETHER_MARKER = "#mlib-together v=2";
export const TOGETHER_MARKER_V1 = "#mlib-together v=1";

/** A cast request nobody answered goes stale this long after its last write. */
export const REQUEST_LIFETIME_MS = 60_000;

const CAST_STATES = ["offer", "busy", "refused", "replace", "on"];

const named = (value) => typeof value === "string" && value.length > 0;
const finite = (value) => typeof value === "number" && Number.isFinite(value);

function object(value) {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function memberOf(value, v1) {
  if (typeof value !== "object" || value === null) return null;
  const { screen, label, remote } = value;
  if (!named(screen) || !named(label)) return null;
  if (v1) return { screen, label, remote: false };
  return typeof remote === "boolean" ? { screen, label, remote } : null;
}

function trackOf(value) {
  if (!object(value)) return null;
  const { id, label } = value;
  return named(id) && named(label) ? { id, label } : null;
}

function tracksOf(value) {
  if (!Array.isArray(value)) return null;
  const tracks = value.map(trackOf);
  return tracks.some((t) => t === null) ? null : tracks;
}

function castOf(value) {
  if (!object(value)) return null;
  const { to, from, profile, state, tv, note, audio, text } = value;
  if (!object(value.tracks)) return null;
  const tracks = { audio: tracksOf(value.tracks.audio), text: tracksOf(value.tracks.text) };
  if (
    !named(to) || !named(from) || !named(profile) || !CAST_STATES.includes(state) ||
    typeof tv !== "string" || typeof note !== "string" || typeof audio !== "string" || typeof text !== "string" ||
    tracks.audio === null || tracks.text === null
  ) return null;
  return { to, from, profile, state, tv, note, tracks, audio, text };
}

/** A cast the TV has not yet taken is a request, not something to watch. */
export const isRequest = (s) => s.cast !== null && s.cast.state !== "on";

export function parseSession(message) {
  const newline = message.indexOf("\n");
  if (newline < 0) return null;
  const marker = message.slice(0, newline);
  const v1 = marker === TOGETHER_MARKER_V1;
  if (!v1 && marker !== TOGETHER_MARKER) return null;
  let json;
  try {
    json = JSON.parse(message.slice(newline + 1));
  } catch {
    return null;
  }
  if (typeof json !== "object" || json === null || Array.isArray(json)) return null;
  const v = json;
  if (
    !named(v.session) || !named(v.set) || typeof v.playing !== "boolean" ||
    !finite(v.pos) || v.pos < 0 || !finite(v.rate) || v.rate <= 0 || !Number.isInteger(v.at) || v.at < 0 ||
    !named(v.by) || !Array.isArray(v.members) || typeof v.ended !== "boolean"
  ) return null;
  const members = v.members.map((m) => memberOf(m, v1));
  if (members.some((m) => m === null)) return null;
  const cast = v1 || v.cast === null ? null : castOf(v.cast);
  if (!v1 && cast === null && v.cast !== null) return null;
  return {
    session: v.session, set: v.set, playing: v.playing, pos: v.pos, rate: v.rate,
    at: v.at, by: v.by, members, ended: v.ended, cast,
  };
}

export function writeSession(s) {
  const { session, set, playing, pos, rate, at, by, ended } = s;
  const members = s.members.map(({ screen, label, remote }) => ({ screen, label, remote: remote === true }));
  const c = s.cast ?? null; // a session built by a move has no cast key yet
  const cast = c === null ? null : {
    to: c.to, from: c.from, profile: c.profile, state: c.state, tv: c.tv, note: c.note,
    tracks: {
      audio: c.tracks.audio.map(({ id, label }) => ({ id, label })),
      text: c.tracks.text.map(({ id, label }) => ({ id, label })),
    },
    audio: c.audio, text: c.text,
  };
  return `${TOGETHER_MARKER}\n${JSON.stringify({ session, set, playing, pos, rate, at, by, members, ended, cast })}`;
}
