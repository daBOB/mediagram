/**
 * Whether this player has heard its household: taken in a sync round of the
 * channel it follows now.
 *
 * Until it has, a first profile waits (`ProfileManager.createFirst`). Sync
 * knows a viewer by name and the newer PIN wins, so a first profile made
 * blind under a household member's name handed its PIN to that member on
 * every device. Kept per channel: following another library is joining
 * another household, whose names the last one's rounds never brought. Local
 * only, in `state_meta`; the core keeps the same mark (`state/sync/first_round.rs`).
 */

import type { Database } from "bun:sqlite";
import { readMeta, writeMeta } from "./state-meta";

const HEARD_KEY = "first_round_imported";

export class Household {
  /** The channel followed now, `null` while none can be reached; `null` itself for a player that syncs with nobody. */
  private follows: (() => string | null) | null = null;

  constructor(private readonly db: Database | null) {}

  /** This player syncs with its household; `library` names the channel it reads now. */
  syncsWith(library: () => string | null): void {
    this.follows = library;
  }

  /** The channel followed now, or `null`. */
  following(): string | null {
    return this.follows?.() ?? null;
  }

  /** A round of `library` has been taken in. */
  heardFrom(library: string): void {
    writeMeta(this.db, HEARD_KEY, library);
  }

  /** Always, for a player that syncs with nobody: it has no household to hear from. */
  heard(): boolean {
    if (this.follows === null) return true;
    const library = this.follows();
    return library !== null && readMeta(this.db, HEARD_KEY) === library;
  }
}
