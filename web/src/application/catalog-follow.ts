/** Keeping the served database, cache expectations, and page notifications on one catalog. */
import { Database } from "bun:sqlite";
import { join } from "node:path";
import { assertSchema, listPlayable } from "../catalog";
import { expectedChunks, type HeldSets } from "../cache/held";
import type { CatalogEvents } from "../catalog-events";
import type { RunningServer } from "../server";
import type { StartupFacts } from "../status/facts";
import type { FoundIndex } from "../channel-index/find-newest-channel-index";
import type { NoIndex } from "../channel-index/pick-newest-index";
import type { PosterFetch } from "../channel-index/fetch-posters-for-index";
import { oneAtATime, refreshFromChannel } from "../channel-index/refresh-from-channel";
import type { OpenedCatalog } from "./open-catalog";

interface FollowOptions {
  db: Database;
  catalog: OpenedCatalog;
  root: string;
  find: () => Promise<FoundIndex | NoIndex>;
  server: Pick<RunningServer, "replaceCatalog">;
  facts: Pick<StartupFacts, "catalog">;
  held?: Pick<HeldSets, "replaceExpected">;
  events: Pick<CatalogEvents, "catalogChanged">;
  fetchPosters: (index: string) => Promise<PosterFetch>;
  posterCount: () => number;
  schedule?: (run: () => void, milliseconds: number) => () => void;
}

const OLD_CATALOG_GRACE_MS = 5 * 60_000;

export class CatalogFollower {
  private db: Database;
  private servingPushedAt: number | null;
  private closed = false;
  private running: Promise<void> = Promise.resolve();
  private readonly retired = new Map<Database, () => void>();
  private readonly pendingPosterFetches = new Set<Promise<void>>();
  private readonly follow = oneAtATime(async () => { if (!this.closed) await this.replace(); });

  constructor(private readonly options: FollowOptions) {
    this.db = options.db;
    this.servingPushedAt = options.catalog.origin === "channel" && options.catalog.publishedAt !== null
      ? options.catalog.publishedAt / 1000 : null;
  }

  refresh(): Promise<void> {
    if (this.closed) return Promise.resolve();
    this.running = this.follow();
    return this.running;
  }

  async stopFollowing(): Promise<void> {
    this.closed = true;
    await this.running;
    await Promise.allSettled(this.pendingPosterFetches);
  }

  /** Called after the listener closes; no request can retain these handles now. */
  close(): void {
    for (const [db, cancel] of this.retired) { cancel(); db.close(); }
    this.retired.clear();
    this.db.close();
  }

  private retire(db: Database): void {
    const schedule = this.options.schedule ?? ((run, ms) => {
      const timer = setTimeout(run, ms);
      return () => clearTimeout(timer);
    });
    const cancel = schedule(() => { this.retired.delete(db); db.close(); }, OLD_CATALOG_GRACE_MS);
    this.retired.set(db, cancel);
  }

  private async replace(): Promise<void> {
    const { root, find, facts, server, held, events } = this.options;
    const result = await refreshFromChannel(root, find);
    if (result.kind === "none") {
      console.warn(`catalog: ${result.reason}`);
      facts.catalog = { ...facts.catalog, reason: result.reason };
      return;
    }
    // The installed snapshot can be newer than the one whose router succeeded.
    if (result.pushedAt === this.servingPushedAt) {
      if (result.reason) console.warn(`catalog: ${result.reason}`);
      facts.catalog = { ...facts.catalog, refresh: result.refresh === "kept" ? "kept" : "unchanged", reason: result.reason };
      return;
    }
    let next: Database | null = null;
    let expected: Map<string, number> | undefined;
    let sets: number;
    const publishedAt = result.pushedAt * 1000;
    try {
      next = new Database(join(result.dir, "library.db"), { readonly: true });
      assertSchema(next);
      expected = held ? expectedChunks(next) : undefined;
      sets = listPlayable(next).length;
      server.replaceCatalog({ db: next, catalog: { origin: "channel", publishedAt } });
    } catch (error) {
      next?.close();
      console.error(`catalog: the installed channel index could not be served: ${(error as Error).message}`);
      return;
    }
    const previous = this.db;
    this.db = next;
    this.servingPushedAt = result.pushedAt;
    this.retire(previous);
    facts.catalog = { ...facts.catalog, origin: "channel", publishedAt, refresh: result.refresh === "kept" ? "kept" : "updated", reason: result.reason, sets };
    if (expected) {
      try { await held!.replaceExpected(expected); }
      catch (error) { console.warn("catalog: cache expectations could not be refreshed:", error); }
    }
    console.log(`catalog: now serving the channel index pushed at ${result.pushedAt}, ${sets} playable sets`);
    events.catalogChanged(publishedAt);
    await this.refreshPosters(result.dir);
  }

  refreshPosters(dir: string): Promise<void> {
    if (this.closed) return Promise.resolve();
    const work = this.fetchArtwork(dir)
      .catch((error) => console.warn("posters: fetch failed:", error))
      .finally(() => this.pendingPosterFetches.delete(work));
    this.pendingPosterFetches.add(work);
    return work;
  }

  private async fetchArtwork(dir: string): Promise<void> {
    const outcome = await this.options.fetchPosters(join(dir, "library.db"));
    if (!outcome.ok) { console.warn(`posters: ${outcome.reason}`); return; }
    console.log(`posters: ${outcome.summary}`);
    const { facts, events, posterCount } = this.options;
    facts.catalog = { ...facts.catalog, posters: posterCount() };
    events.catalogChanged(facts.catalog.publishedAt);
  }
}
