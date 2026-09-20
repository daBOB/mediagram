/**
 * Preview frames for the scrub bar.
 *
 * Hovering the bar and seeing the frame you would land on is the single thing
 * that most makes a player feel like it knows what it is holding. It is also
 * the most expensive thing in this player, and the expense is the design.
 *
 * **A sheet is only ever made from bytes the cache already holds.** A film in
 * the channel is several gigabytes, and reading all of it to make a few
 * hundred kilobytes of pictures would be the most wasteful thing this program
 * does — a whole library pulled down so a bar can be prettier. So generation
 * is offered only for a set `HeldSets` reports as complete, which is the same
 * predicate behind the offline badge and means the read never leaves the disk.
 *
 * Which gives the honest behaviour, and it is worth stating plainly because a
 * viewer will notice it: **a title you have watched has previews, and one you
 * have not does not.** The bar behaves exactly as it does today for the rest.
 *
 * Asking ffmpeg for a single frame on each hover was the obvious alternative
 * and is much worse: a hover fires many times a second, each frame is a seek
 * into a remote file, and the result is a request storm for pictures nobody
 * will look at twice.
 */

import { mkdir, rename, rm, stat } from "node:fs/promises";
import { join } from "node:path";

import { sheetArgs } from "./args";

/** The layout `sprite-plan.js` decided. Re-declared for the server's types. */
export interface SpritePlan {
  interval: number;
  tiles: number;
  columns: number;
  rows: number;
  tileWidth: number;
  tileHeight: number;
}

export interface SheetOptions {
  /** Where sheets are kept. Beside the posters, not in the chunk cache. */
  directory: string;
  /** This server's own base URL, which is where ffmpeg reads the bytes from. */
  baseUrl: string;
  /** Whether a set is on this disk in full. Nothing else may be generated. */
  isHeld: (setId: string) => boolean;
}

export class SheetStore {
  /** Sets being generated right now, so two hovers do not start two ffmpegs. */
  private readonly making = new Set<string>();

  constructor(private readonly options: SheetOptions) {}

  /** Where a set's sheet lives, whether or not it exists. */
  path(setId: string): string {
    // The set id is `[A-Za-z0-9]{1,64}` at every route that reaches this, so
    // it cannot carry a separator — but this builds a filesystem path, so the
    // check is here as well rather than trusted from a caller.
    if (!/^[A-Za-z0-9]{1,64}$/.test(setId)) throw new Error("not a set id");
    return join(this.options.directory, `${setId}.jpg`);
  }

  /** The sheet's size, or `null` when there is not one. */
  async sizeOf(setId: string): Promise<number | null> {
    try {
      return (await stat(this.path(setId))).size;
    } catch {
      return null;
    }
  }

  /**
   * Makes a sheet, if this set may have one and does not already.
   *
   * Returns whether a sheet exists *now*. A first hover will usually get
   * `false` and no preview, and a later one will get the sheet — which is the
   * right shape for something that takes a while and that nothing is waiting
   * on. Never throws: a player without previews is the player as it was.
   */
  async ensure(setId: string, plan: SpritePlan | null): Promise<boolean> {
    if (plan === null) return false;
    if ((await this.sizeOf(setId)) !== null) return true;
    // Only what is already on this disk. See the header: this is the rule the
    // whole design rests on.
    if (!this.options.isHeld(setId)) return false;
    if (this.making.has(setId)) return false;

    this.making.add(setId);
    try {
      await mkdir(this.options.directory, { recursive: true });
      const output = this.path(setId);
      /**
       * Written aside and moved into place, so a reader is never handed a
       * half-written image: `rename` within one directory is atomic, and a
       * sheet that appears is therefore a sheet that is finished.
       *
       * The suffix goes before the extension, not after. ffmpeg picks its
       * output format from the extension, and a file ending `.jpg.making` has
       * one it has never heard of — it refuses to write anything at all,
       * which presents as a sheet that silently never appears.
       */
      const partial = join(this.options.directory, `${setId}.making.jpg`);
      const args = sheetArgs({
        input: `${this.options.baseUrl}/api/sets/${encodeURIComponent(setId)}/stream`,
        output: partial,
        plan,
      });

      const process = Bun.spawn(["ffmpeg", ...args], { stdout: "ignore", stderr: "pipe" });
      const code = await process.exited;
      if (code !== 0) {
        await rm(partial, { force: true });
        return false;
      }
      await rename(partial, output);
      return true;
    } catch {
      return false;
    } finally {
      this.making.delete(setId);
    }
  }
}
