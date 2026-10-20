/**
 * The watch-together session message: `#mlib-together v=2` on the first line
 * and its JSON on the second. `v=1` messages from older screens still read,
 * as sessions with no remote members and no cast. Each session is one unpinned message in the
 * library channel, edited in place, and the last edit wins.
 * `android/core/model/src/main/kotlin/together/` is the Kotlin port, held to
 * this by `web/test/fixtures/together/`.
 */

export const TOGETHER_MARKER: "#mlib-together v=2";
/** Read only: writers always write `v=2`. */
export const TOGETHER_MARKER_V1: "#mlib-together v=1";

/** A cast request nobody answered goes stale this long after its last write. */
export const REQUEST_LIFETIME_MS: 60000;

export interface Member {
  screen: string;
  /** The surface kind the others see: "TV", "Phone", "Tablet" or "Browser". */
  label: string;
  /** Controls the session but plays nothing, and never keeps it alive. */
  remote: boolean;
}

export interface Track {
  /** The TV's own id; it means nothing to any other screen. */
  id: string;
  label: string;
}

export interface CastTracks {
  audio: Track[];
  text: Track[];
}

export type CastState = "offer" | "busy" | "refused" | "replace" | "on";

/** A request for a TV to play the session's set, and then the TV's side of it. */
export interface Cast {
  /** The kind of screen asked to play: always "TV". */
  to: "TV";
  /** The caster's surface label. */
  from: string;
  /** The caster's profile id; the TV plays as this profile. */
  profile: string;
  /** The household the casting device joined: only a TV that joined the same one answers. */
  household: string;
  state: CastState;
  /** The screen id of the TV that took the cast; "" until one has. */
  tv: string;
  /** With "busy" the title the TV is playing, with "refused" the reason; "" otherwise. */
  note: string;
  tracks: CastTracks;
  /** The audio track playing on the TV; "" until the TV has listed them. */
  audio: string;
  /** The subtitle track shown on the TV; "" for none. */
  text: string;
}

export interface Session {
  session: string;
  /** The set id playing: a film, or one episode or lesson. */
  set: string;
  playing: boolean;
  /** Seconds into `set` at the moment `at`. */
  pos: number;
  rate: number;
  /** The writer's wall clock, epoch milliseconds. */
  at: number;
  /** The screen whose change this is. */
  by: string;
  members: Member[];
  ended: boolean;
  /** `null` for plain watch together. */
  cast: Cast | null;
}

/** A cast no TV has taken yet: not something to watch, and stale after `REQUEST_LIFETIME_MS`. */
export function isRequest(s: Session): boolean;

/** The session a message holds, or `null` for anything that is not exactly one. */
export function parseSession(message: string): Session | null;
export function writeSession(s: Session): string;
