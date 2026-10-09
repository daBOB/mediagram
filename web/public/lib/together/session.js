/**
 * The watch-together session message: `#mlib-together v=1` on the first line
 * and its JSON on the second. Each session is one unpinned message in the
 * library channel, edited in place, and the last edit wins.
 * `android/core/model/src/main/kotlin/together/` is the Kotlin port, held to
 * this by `web/test/fixtures/together/`. Types are in `session.d.ts`.
 */

export const TOGETHER_MARKER = "#mlib-together v=1";

const named = (value) => typeof value === "string" && value.length > 0;
const finite = (value) => typeof value === "number" && Number.isFinite(value);

function memberOf(value) {
  if (typeof value !== "object" || value === null) return null;
  const { screen, label } = value;
  return named(screen) && named(label) ? { screen, label } : null;
}

export function parseSession(message) {
  const newline = message.indexOf("\n");
  if (newline < 0 || message.slice(0, newline) !== TOGETHER_MARKER) return null;
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
    !finite(v.pos) || v.pos < 0 || !finite(v.rate) || v.rate <= 0 || !finite(v.at) ||
    !named(v.by) || !Array.isArray(v.members) || typeof v.ended !== "boolean"
  ) return null;
  const members = v.members.map(memberOf);
  if (members.some((m) => m === null)) return null;
  return {
    session: v.session, set: v.set, playing: v.playing, pos: v.pos, rate: v.rate,
    at: v.at, by: v.by, members, ended: v.ended,
  };
}

export function writeSession(s) {
  const { session, set, playing, pos, rate, at, by, ended } = s;
  const members = s.members.map(({ screen, label }) => ({ screen, label }));
  return `${TOGETHER_MARKER}\n${JSON.stringify({ session, set, playing, pos, rate, at, by, members, ended })}`;
}
