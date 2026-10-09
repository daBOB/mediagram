/**
 * What each screen writes to the session. Starting, joining and leaving touch
 * only who is in it. Acting is the viewer's own play, pause, seek, speed or
 * title change, stamped with this screen's clock. A change a screen makes
 * because it is following is never one of these. Types are in `moves.d.ts`.
 */

import { expectedPosition } from "./follow.js";

const toMillis = (secs) => Math.round(secs * 1000) / 1000;

export function started(id, me, own, now) {
  return { session: id, ...own, pos: toMillis(own.pos), at: now, by: me.screen, members: [me], ended: false };
}

export function joined(s, me) {
  const others = s.members.filter((m) => m.screen !== me.screen);
  return { ...s, by: me.screen, members: s.members.length === others.length ? [...others, me] : s.members };
}

export function left(s, screen) {
  const members = s.members.filter((m) => m.screen !== screen);
  return { ...s, by: screen, members, ended: members.length === 0 };
}

export function acted(s, change, me, now) {
  return {
    ...s,
    set: change.set ?? s.set,
    playing: change.playing ?? s.playing,
    pos: toMillis(change.pos ?? expectedPosition(s, now)),
    rate: change.rate ?? s.rate,
    at: now,
    by: me,
  };
}

/** The first screen to reach the end of a film, or of the last episode, ends the session for everyone. */
export function finished(s, screen) {
  return { ...s, playing: false, by: screen, ended: true };
}
