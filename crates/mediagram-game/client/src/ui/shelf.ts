import type { GameKind } from "../protocol/GameKind";

/**
 * Where the box's app answers a game's poster or backdrop from its own files. A game without art,
 * or a host without the app in front of it (the dev host), answers 404 and the page draws without.
 */
export const gameArtUrl = (kind: GameKind, part: "poster" | "backdrop") => `/game-art/${part}-${kind.replace(/_/g, "-")}.webp`;

/** One step along the row, stopping at either end, as the remote's Left and Right do. */
export const stepIndex = (i: number, dir: number, count: number) => Math.min(count - 1, Math.max(0, i + dir));

/** The scroll offset that shows the item spanning `start`..`end` whole in a view `width` wide, now at `left`. */
export const scrollToShow = (left: number, width: number, start: number, end: number) =>
  start < left ? start : end > left + width ? end - width : left;

export type ShelfFacts = {
  /** Phones at the table. */
  seated: number;
  /** The room's own minimum, below any game's. */
  minPlayers: number;
  /** The focused game's name. */
  name: string;
  /** The host has the focused game as its pick, so `blocked` is about it. */
  heard: boolean;
  blocked: string | null;
  leader: string | null;
};

/** The line under the game's name: what OK does now, or why it cannot. Empty while the pick is on its way. */
export function shelfLine(f: ShelfFacts): string {
  if (f.seated === 0) return "Zum Mitspielen: Handy-Kamera auf den Code";
  if (!f.heard) return "";
  if (f.blocked) return f.blocked;
  if (f.seated < f.minPlayers) return `Wartet auf ${f.minPlayers - f.seated} Spieler`;
  return f.leader ? `OK startet ${f.name} · ${f.leader} kann auch am Handy starten` : `OK startet ${f.name}`;
}

/** True when OK may start the focused game now; a pick the host has not heard yet is left for it to judge. */
export const okStarts = (f: ShelfFacts) => f.seated >= Math.max(1, f.minPlayers) && (!f.heard || !f.blocked);
