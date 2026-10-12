import type { Member, Session } from "./session.js";

export interface Playback { set: string; playing: boolean; pos: number; rate: number }

export function started(id: string, me: Member, own: Playback, now: number): Session;
export function joined(s: Session, me: Member): Session;
/** `ended` when no members are left. */
export function left(s: Session, screen: string): Session;
export function acted(s: Session, change: Partial<Playback>, me: string, now: number): Session;
/** The end of the last title: over for everyone. */
export function finished(s: Session, screen: string): Session;
