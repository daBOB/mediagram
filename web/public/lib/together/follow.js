/**
 * Following a session: where it is now, whether it is still live, which edit
 * a screen follows, and what that screen must do to match it. Types are in
 * `follow.d.ts`.
 */

export function expectedPosition(s, now) {
  return s.playing ? Math.max(0, s.pos + ((now - s.at) / 1000) * s.rate) : s.pos;
}
