import type { Live } from "../together-now.js";

/**
 * Asks the TV to play `own.set` from where this tab is, and shows how it went.
 * Resolves to the accepted session, or `null` when nothing was cast.
 */
export function askTv(own: { set: string; playing: boolean; pos: number; rate: number }, profile: string): Promise<Live | null>;
