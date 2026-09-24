/**
 * Bringing the channel's newest index in, and deciding what to serve when it
 * cannot be brought in.
 *
 * The order is the one the user chose: the channel's newest snapshot; failing
 * that, the snapshot installed last time; failing that, nothing here — and the
 * caller serves this machine's own `library.db`. A channel that cannot be read
 * is never a reason to stop serving a library.
 */

import type { FoundIndex } from "./find-newest-channel-index";
import { failureMessage } from "../failure-message";
import { currentDir, installChannelIndex, installedPushedAt } from "./install-channel-index";
import type { NoIndex } from "./pick-newest-index";

export type ChannelRefresh =
  | {
      kind: "installed";
      dir: string;
      /** Seconds, as the caption carries it. */
      pushedAt: number;
      /** Whether this refresh changed anything, or is serving an older one. */
      refresh: "updated" | "unchanged" | "kept";
      reason: string | null;
    }
  | { kind: "none"; reason: string };

/** What a reader is told about each channel that holds no index. */
const NO_INDEX: Record<NoIndex, string> = {
  "nothing-pinned": "the channel has nothing pinned; run `mediagram push-index` on the uploading machine",
  "not-an-index": "nothing pinned in the channel is a library index",
};

export async function refreshFromChannel(
  root: string,
  find: () => Promise<FoundIndex | NoIndex>,
): Promise<ChannelRefresh> {
  let found: FoundIndex | NoIndex;
  try {
    found = await find();
  } catch (error) {
    return fallback(root, `the channel could not be read: ${failureMessage(error)}`);
  }
  if (typeof found === "string") return fallback(root, NO_INDEX[found]);

  const outcome = await installChannelIndex(root, found.pushedAt, found.chunks);
  if (outcome.status === "kept") return fallback(root, outcome.reason);
  return { kind: "installed", dir: outcome.dir, pushedAt: outcome.pushedAt, refresh: outcome.status, reason: null };
}

async function fallback(root: string, reason: string): Promise<ChannelRefresh> {
  const installed = await installedPushedAt(root);
  if (installed === null) return { kind: "none", reason };
  return { kind: "installed", dir: currentDir(root), pushedAt: installed, refresh: "kept", reason };
}

/**
 * Runs `task` one at a time, however often it is asked for.
 *
 * An index event arriving while an install is running is not dropped — the
 * snapshot it announces may be newer than the one being installed — but a
 * burst of them collapses into a single run after the current one.
 */
export function oneAtATime(task: () => Promise<void>): () => Promise<void> {
  let running: Promise<void> | null = null;
  let again = false;
  const run = (): Promise<void> => {
    if (running) {
      again = true;
      return running;
    }
    running = (async () => {
      try {
        do {
          again = false;
          await task().catch((error) => console.error("channel index:", error));
        } while (again);
      } finally {
        running = null;
      }
    })();
    return running;
  };
  return run;
}
