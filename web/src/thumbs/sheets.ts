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

import { sheetArgs, type SpritePlan } from "./args";

export interface SheetOptions {
  /** Where sheets are kept. Beside the posters, not in the chunk cache. */
  directory: string;
  /** This server's own base URL, which is where ffmpeg reads the bytes from. */
  baseUrl: string;
  /** Whether a set is on this disk in full. Nothing else may be generated. */
  isHeld: (setId: string) => boolean;
}

type RunFfmpeg = (args: string[], signal: AbortSignal) => Promise<number>;
const runFfmpeg: RunFfmpeg = async (args, signal) => {
  if (signal.aborted) return -1;
  const child = Bun.spawn(["ffmpeg", ...args], { stdout: "ignore", stderr: "ignore" });
  let force: ReturnType<typeof setTimeout> | undefined;
  const kill = (signal: "SIGTERM" | "SIGKILL") => {
    try { child.kill(signal); } catch { /* Already exited; still await notification. */ }
  };
  const abort = () => {
    kill("SIGTERM");
    force = setTimeout(() => kill("SIGKILL"), 3000);
  };
  signal.addEventListener("abort", abort, { once: true });
  try { return await child.exited; }
  finally {
    clearTimeout(force);
    signal.removeEventListener("abort", abort);
  }
};

export class SheetStore {
  /** Sets being generated right now, so two hovers do not start two ffmpegs. */
  private readonly making = new Set<string>();
  private readonly tasks = new Set<Promise<boolean>>();
  private readonly stopping = new AbortController();

  constructor(private readonly options: SheetOptions, private readonly run: RunFfmpeg = runFfmpeg) {}

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
      const file = await stat(this.path(setId));
      return file.isFile() ? file.size : null;
    } catch {
      return null;
    }
  }

  /**
   * Makes a sheet, if this set may have one and does not already.
   *
   * The initiating call waits for generation and publication. A concurrent
   * call while generation runs returns false. The artwork route deliberately
   * starts this without awaiting it so hover requests stay responsive.
   * Never throws: a player without previews is the player as it was.
   */
  ensure(setId: string, plan: SpritePlan | null): Promise<boolean> {
    if (this.stopping.signal.aborted || plan === null) return Promise.resolve(false);
    const task = this.generate(setId, plan).finally(() => { this.tasks.delete(task); });
    this.tasks.add(task);
    return task;
  }

  /** Close admission, terminate children, and await generation and partial-file cleanup. */
  async stop(): Promise<void> {
    this.stopping.abort();
    await Promise.all(this.tasks);
  }

  private async generate(setId: string, plan: SpritePlan): Promise<boolean> {
    let started = false;
    let partial: string | undefined;
    try {
      if ((await this.sizeOf(setId)) !== null) return true;
      // Only what is already on this disk. See the header: this is the rule
      // the whole design rests on.
      if (this.stopping.signal.aborted || !this.options.isHeld(setId)) return false;
      if (this.making.has(setId)) return false;
      this.making.add(setId);
      started = true;
      await mkdir(this.options.directory, { recursive: true });
      if (this.stopping.signal.aborted) return false;
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
      partial = join(this.options.directory, `${setId}.making.jpg`);
      const args = sheetArgs({
        input: `${this.options.baseUrl}/api/sets/${encodeURIComponent(setId)}/stream`,
        output: partial,
        plan,
      });

      const code = await this.run(args, this.stopping.signal);
      if (code !== 0 || this.stopping.signal.aborted) {
        await rm(partial, { force: true });
        return false;
      }
      await rename(partial, output);
      return true;
    } catch {
      if (partial) await rm(partial, { force: true }).catch(() => {});
      return false;
    } finally {
      if (started) this.making.delete(setId);
    }
  }
}
