import type { Playback } from "./moves.js";
import type { CastTracks, Member, Session } from "./session.js";

/** How long a caster waits for the TV to answer an offer. */
export const CAST_ANSWER_MS: 5000;
export const AUDIO_TRACKS_MAX: 8;
export const TEXT_TRACKS_MAX: 24;
export const TRACK_LABEL_MAX: 32;

/** A request from `me` for a TV of `household` to play `own`, as the caster's `profile`. */
export function offered(id: string, me: Member, own: Playback, profile: string, household: string, now: number): Session;

/** A cast for another household's TV than `household`'s; for `null`, every cast is. */
export function castElsewhere(s: Session, household: string | null): boolean;

export type TvAnswer =
  | { kind: "ignore" }
  | { kind: "refuse"; note: string }
  | { kind: "busy"; note: string }
  | { kind: "accept" };

/** What a TV should write to a request it hears; `busy` is the title it plays, `refusal` why it will not, `household` the one it joined. */
export function answerOf(
  s: Session,
  tv: { screen: string; busy: string | null; refusal: string | null; household: string | null },
  now: number,
): TvAnswer;
export function busied(s: Session, tv: string, note: string): Session;
export function refusedBy(s: Session, tv: string, note: string): Session;
/** The caster answers "busy" with its own playback again. */
export function replaced(s: Session, me: string, own: Playback, now: number): Session;
/** The TV takes the cast, paused where it had got to; `null` when a TV already has. */
export function accepted(s: Session, me: Member, now: number): Session | null;
export function tracked(s: Session, tv: string, tracks: CastTracks, audio: string, text: string): Session;
export function chose(s: Session, screen: string, choice: { audio?: string; text?: string }): Session;
/** `null` for your own stop, or when nothing ended. */
export function endingOf(s: Session, me: string): "finished" | "stopped" | null;
