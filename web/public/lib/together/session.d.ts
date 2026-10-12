/**
 * The watch-together session message: `#mlib-together v=1` on the first line
 * and its JSON on the second. Each session is one unpinned message in the
 * library channel, edited in place, and the last edit wins.
 * `android/core/model/src/main/kotlin/together/` is the Kotlin port, held to
 * this by `web/test/fixtures/together/`.
 */

export const TOGETHER_MARKER: "#mlib-together v=1";

export interface Member {
  screen: string;
  /** The surface kind the others see: "TV", "Phone", "Tablet" or "Browser". */
  label: string;
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
}

/** The session a message holds, or `null` for anything that is not exactly one. */
export function parseSession(message: string): Session | null;
export function writeSession(s: Session): string;
