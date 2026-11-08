// What changed between two scenes the host sent. The protocol carries the state, not the events: a plot changing hands (bought,
// taken over by force, won at auction), an entrance placed, a player going broke and cash moving are read off the difference.
// A stage being built needs no event: each hotel compares its levels itself. The first scene a TV sees has no changes, so a TV
// that reconnects replays nothing.

import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";

export interface Changes {
  /** Hotels with a new owner (given back to the bank is not one). */
  owners: number[];
  /** Fields that got an entrance. */
  entrances: number[];
  /** Players who went out. */
  out: number[];
  /** Cash change by player. */
  cash: Map<number, number>;
}

export function changesBetween(prev: GrandAlleeScene | null, next: GrandAlleeScene): Changes {
  const changes: Changes = { owners: [], entrances: [], out: [], cash: new Map() };
  if (!prev) return changes;
  const owner = new Map(prev.estates.map((e) => [e.hotel, e.owner]));
  changes.owners = next.estates.filter((e) => owner.get(e.hotel) !== e.owner).map((e) => e.hotel);
  const had = new Set(prev.entrances.map((e) => `${e.field}:${e.hotel}`));
  changes.entrances = next.entrances.filter((e) => !had.has(`${e.field}:${e.hotel}`)).map((e) => e.field);
  const was = new Map(prev.seats.map((s) => [s.player, s]));
  for (const s of next.seats) {
    const w = was.get(s.player);
    if (!w) continue;
    if (s.out && !w.out) changes.out.push(s.player);
    if (s.cash !== w.cash) changes.cash.set(s.player, s.cash - w.cash);
  }
  return changes;
}

/**
 * Seconds: frames that stopped this long (a WebView paused, a box asleep), or no scene for this long while frames ran (longer
 * than any stage lasts: a question's 20 s clock, an auction's 15 s, so the socket was down), and the next comparison would sum
 * everything missed: the board starts afresh instead.
 */
export const GAP = { frame: 1, silence: 30 };

/** Feeds the scenes the host sends, frame by frame, to changesBetween, starting afresh after a gap. */
export class SceneWatch {
  private prev: GrandAlleeScene | null = null;
  private frameAt = -Infinity;
  private sceneAt = -Infinity;

  /** Every frame (`t`: frame clock, s): null while the scene stands; else what changed, or `fresh` with no changes after a gap. */
  next(scene: GrandAlleeScene, t: number): { changes: Changes; fresh: boolean } | null {
    if (t - this.frameAt > GAP.frame) this.prev = null;
    this.frameAt = t;
    if (scene === this.prev) return null;
    if (t - this.sceneAt > GAP.silence) this.prev = null;
    const fresh = this.prev === null, changes = changesBetween(this.prev, scene);
    this.prev = scene;
    this.sceneAt = t;
    return { changes, fresh };
  }
}
