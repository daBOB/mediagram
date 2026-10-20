import type { Session } from "./session.js";

export const CAST_WORDS: {
  playOnTv: string;
  asking: string;
  replace: string;
  cancel: string;
  noAnswer: string;
  leaveFirst: string;
  playingOnTv: string;
  stop: string;
  finished: string;
  stopped: string;
  libraryMissing: string;
  profileUnknown: string;
  joinFirst: string;
};

export function busyQuestion(title: string): string;
export function refusedLine(reason: string): string;
export function noAnswerIn(household: string): string;
export function castFrom(from: string): string;
export function barLine(title: string): string;
export function ageReason(name: string): string;
export function timeReason(name: string): string;
export function sectionReason(name: string): string;

/** `say` for a cast, or `null` for the TV's own joining and leaving. */
export function sayOnRemote(noticed: { change: string; label: string | null }, session: Session): string | null;
