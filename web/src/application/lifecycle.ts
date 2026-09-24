/** Subscription catch-up and ordered shutdown, shared by the entry point and local integration tests. */
import type { LibraryEvent } from "../telegram/updates";
import type { StateSync } from "../state/sync";
import type { RunningServer } from "../server";
import type { TranscodeRegistry } from "../transcode/registry";
import type { CatalogEvents } from "../catalog-events";
import type { CatalogFollower } from "./catalog-follow";
import type { SeriesPreload } from "../cache/series-preload";
import type { SheetStore } from "../thumbs/sheets";
import type { AudioTrackReader } from "../catalog/audio-tracks";

type Sync = Pick<StateSync, "once"> | null;

export async function syncOnce(sync: Sync, why: string): Promise<void> {
  if (!sync) return;
  const outcome = await sync.once();
  if (outcome.failed !== undefined) console.warn(`sync (${why}): ${outcome.failed}`);
  else if (outcome.pulled > 0 || outcome.pushed) {
    console.log(`sync (${why}): took ${outcome.pulled}, ${outcome.pushed ? "sent" : "sent nothing"}`);
  }
}

export class LibraryUpdates {
  private stopped = false;
  private unsubscribe: (() => void) | null = null;
  private onIndex: () => void = () => {};

  constructor(private readonly subscribe: (event: (kind: LibraryEvent) => void) => () => void, private readonly sync: Sync) {}

  async start(): Promise<void> {
    this.unsubscribe = this.subscribe((event) => {
      if (this.stopped) return;
      if (event === "state") void syncOnce(this.sync, "push");
      else this.onIndex();
    });
    await syncOnce(this.sync, "start");
  }

  followCatalog(follower: Pick<CatalogFollower, "refresh"> | null): Promise<void> {
    if (this.stopped || !follower) return Promise.resolve();
    this.onIndex = () => { void follower.refresh(); };
    // Telegram does not replay an index published before subscription.
    return follower.refresh();
  }

  stop(): void {
    this.stopped = true;
    this.unsubscribe?.();
    this.unsubscribe = null;
  }
}

export interface ApplicationResources {
  timers: Array<ReturnType<typeof setInterval>>;
  updates?: LibraryUpdates;
  events?: Pick<CatalogEvents, "close">;
  sync?: Sync;
  transcodes?: Pick<TranscodeRegistry, "stopAll">;
  preload?: Pick<SeriesPreload, "stop">;
  sheets?: Pick<SheetStore, "stop">;
  audio?: Pick<AudioTrackReader, "stop">;
  server?: Pick<RunningServer, "close">;
  state?: { close(): void };
  telegram?: { disconnect(): Promise<void> };
  catalog?: { close(): void; stopFollowing?(): Promise<void> };
}

/** The same cleanup handles a signal and a startup that failed partway through. */
export function shutdownFor(resources: ApplicationResources): () => Promise<void> {
  let stopping: Promise<void> | null = null;
  return () => stopping ??= (async () => {
    const failures: unknown[] = [];
    const attempt = async (run: () => unknown) => {
      try { await run(); } catch (error) { failures.push(error); }
    };
    // Close admission before the first wait. Keep HTTP and Telegram available
    // until the work already using them has stopped and cleaned up.
    const mediaStopping = [
      attempt(() => resources.preload?.stop()),
      attempt(() => resources.sheets?.stop()),
      attempt(() => resources.audio?.stop()),
    ];
    for (const timer of resources.timers.splice(0)) clearInterval(timer);
    await attempt(() => resources.updates?.stop());
    await attempt(() => resources.events?.close());
    await attempt(() => resources.catalog?.stopFollowing?.());
    await attempt(() => syncOnce(resources.sync ?? null, "stopping"));
    await Promise.all(mediaStopping);
    await attempt(() => resources.transcodes?.stopAll());
    await attempt(() => resources.server?.close());
    await attempt(() => resources.state?.close());
    await attempt(() => resources.telegram?.disconnect());
    await attempt(() => resources.catalog?.close());
    if (failures.length) throw new AggregateError(failures, "application shutdown failed");
  })();
}

interface ProcessSignals {
  on(signal: "SIGINT" | "SIGTERM", handler: () => void): unknown;
  off(signal: "SIGINT" | "SIGTERM", handler: () => void): unknown;
  exit(code: number): unknown;
}

/** Only the executable entry point binds process signals or exits the process. */
export function installShutdownSignals(stop: () => Promise<void>, target: ProcessSignals = process): () => void {
  let stopping = false;
  const signals = ["SIGINT", "SIGTERM"] as const;
  const dispose = () => { for (const signal of signals) target.off(signal, onSignal); };
  const onSignal = () => {
    if (stopping) return;
    stopping = true;
    console.log("\nstopping");
    void stop().then(
      () => { dispose(); target.exit(0); },
      (error) => { console.error("shutdown failed:", error); dispose(); target.exit(1); },
    );
  };
  for (const signal of signals) target.on(signal, onSignal);
  return dispose;
}
