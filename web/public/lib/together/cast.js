/**
 * Casting to a TV: the handshake (offer, then the TV's answer), the TV's track
 * list, and how a cast ends. A cast is an ordinary session with a remote in
 * it; these are its extra moves. Types are in `cast.d.ts`.
 */

import { expectedPosition, isLive } from "./follow.js";
import { joined } from "./moves.js";
import { isRequest } from "./session.js";

/** How long a caster waits for the TV to answer an offer. */
export const CAST_ANSWER_MS = 5000;
/** Lists and labels are cut so a full cast fits in one Telegram message. */
export const AUDIO_TRACKS_MAX = 8;
export const TEXT_TRACKS_MAX = 24;
export const TRACK_LABEL_MAX = 32;

const toMillis = (secs) => Math.round(secs * 1000) / 1000;

export function offered(id, me, own, profile, household, now) {
  const cast = { to: "TV", from: me.label, profile, household, state: "offer", tv: "", note: "", tracks: { audio: [], text: [] }, audio: "", text: "" };
  return { session: id, ...own, pos: toMillis(own.pos), at: now, by: me.screen, members: [{ ...me, remote: true }], ended: false, cast };
}

/**
 * Whether `s` is a cast for another household than `household` (`null`, a
 * device that has joined none, has none of its own): that household's TV, not
 * this device's to answer, join or steer.
 */
export const castElsewhere = (s, household) => s.cast != null && s.cast.household !== household;

/**
 * Refusing wins over busy: a TV that will not play it has nothing to replace.
 * Another household's TV says nothing at all, not no: its refusal would
 * overwrite the answer of the TV the cast is for.
 */
export function answerOf(s, tv, now) {
  if (!isRequest(s) || castElsewhere(s, tv.household) || s.ended || s.cast.state === "busy" || s.cast.state === "refused" || !isLive(s, null, now)) {
    return { kind: "ignore" };
  }
  if (tv.refusal !== null) return { kind: "refuse", note: tv.refusal };
  if (s.cast.state === "offer" && tv.busy !== null) return { kind: "busy", note: tv.busy };
  return { kind: "accept" };
}

/** `pos` and `at` stay the offer's, so the request goes stale a minute after it was made. */
const answered = (s, state, tv, note) => ({ ...s, by: tv, cast: { ...s.cast, state, note } });

export const busied = (s, tv, note) => answered(s, "busy", tv, note);
export const refusedBy = (s, tv, note) => answered(s, "refused", tv, note);

export function replaced(s, me, own, now) {
  return { ...s, ...own, pos: toMillis(own.pos), at: now, by: me, cast: { ...s.cast, state: "replace", note: "" } };
}

/** The first TV to write wins; a later one finds `tv` set and leaves it. */
export function accepted(s, me, now) {
  if (s.cast.tv !== "") return null;
  const tv = joined(s, { ...me, remote: false });
  return {
    ...tv,
    playing: false,
    pos: toMillis(expectedPosition(s, now)),
    at: now,
    cast: { ...s.cast, tv: me.screen, state: "on", note: "" },
  };
}

const capped = (list, max) => list.slice(0, max).map((t) => ({ id: t.id, label: t.label.trim().slice(0, TRACK_LABEL_MAX).trim() }));

export function tracked(s, tv, tracks, audio, text) {
  const lists = { audio: capped(tracks.audio, AUDIO_TRACKS_MAX), text: capped(tracks.text, TEXT_TRACKS_MAX) };
  return { ...s, by: tv, cast: { ...s.cast, tracks: lists, audio, text } };
}

export function chose(s, screen, choice) {
  return { ...s, by: screen, cast: { ...s.cast, audio: choice.audio ?? s.cast.audio, text: choice.text ?? s.cast.text } };
}

/** How a cast ended for `me`: the TV reached the end, or someone or something stopped it. */
export function endingOf(s, me) {
  if (!s.ended || s.cast === null || s.by === me) return null;
  return s.by === s.cast.tv && s.members.some((m) => m.screen === s.cast.tv) ? "finished" : "stopped";
}
