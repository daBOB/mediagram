import type { Session } from "./session.js";

/** Further apart than this, a screen seeks. */
export const DRIFT_TOLERANCE_SECS: 0.5;

export interface ScreenState { set: string | null; playing: boolean; pos: number; rate: number }

export type Step =
  | { kind: "open"; set: string; pos: number; playing: boolean; rate: number }
  | { kind: "rate"; rate: number }
  | { kind: "seek"; pos: number }
  | { kind: "play" }
  | { kind: "pause" };

export type Change = "played" | "paused" | "sought" | "rate" | "title" | "joined" | "left";

export function expectedPosition(s: Session, now: number): number;
export function isLive(s: Session, durationSecs: number | null, now: number): boolean;
export function follow(current: Session | null, incoming: Session, me: string): Session;
export function decide(own: ScreenState, s: Session, now: number): Step[];
export function changeOf(prev: Session, next: Session): { change: Change; label: string | null } | null;
