/**
 * Everything a settings action needs to touch, in one place.
 *
 * `web/src/settings/routes.ts` is HTTP shape only — parsing a body, checking
 * the gate, turning an outcome into a status code. Every actual effect (a
 * restart, a channel switch, a budget change) happens here, against the same
 * connection, followed channel and catalog follower `index.ts` built at
 * startup, so a setting changed from the browser is indistinguishable from
 * one that had always been that way.
 *
 * Account identity (app id/hash, sign in/out) is `account-actions.ts`,
 * reached as `account`; this file keeps the cache and library actions, and
 * the read view both draw on.
 *
 * Order throughout is prove, swap, rebind, save: nothing is written to
 * `telegram.json` until the change it describes actually took, and a save
 * that fails after that is reported, not thrown.
 */

import { Telegram } from "../telegram/client";
import type { TelegramConnection } from "../telegram/connection";
import type { FollowedChannel, UpdatesBinding } from "../application/telegram-binding";
import type { CatalogFollower } from "../application/catalog-follow";
import type { FoundIndex } from "../channel-index/find-newest-channel-index";
import type { NoIndex } from "../channel-index/pick-newest-index";
import type { ChunkCache } from "../cache/store";
import type { HeldSets } from "../cache/held";
import { applyBudget, validateBudget } from "../cache/budget";
import { MIN_CACHE_BUDGET_BYTES, type Settings } from "../state/settings";
import { listLibraries, type LibraryCandidate } from "../telegram/libraries";
import { HandleMap } from "./handles";
import { AccountActions, type ActionResult } from "./account-actions";
import { listSessions, revokeSession, type RevokeOutcome, type SessionSummary } from "./sessions";
import { failureMessage } from "../failure-message";
import { join } from "node:path";

export interface SettingsDeps {
  connection: TelegramConnection;
  channel: FollowedChannel;
  updatesBinding: UpdatesBinding;
  follower: Pick<CatalogFollower, "tryChannel">;
  findIndex: (telegram: Telegram) => Promise<FoundIndex | NoIndex>;
  settings: Settings;
  cache: Pick<ChunkCache, "setBudget" | "budget" | "sizeOnDisk"> | null;
  held?: Pick<HeldSets, "refresh">;
  invalidateHeldBytes?: () => void;
  channelCatalogDir: string;
  telegramFilePath: string;
}

export interface SettingsView {
  telegram: {
    signedIn: boolean;
    connected: boolean | null;
    account: { name: string | null; username: string | null } | null;
    dc: number | null;
    library: { title: string } | null;
    app: { apiId: number; apiHashSet: boolean };
  };
  cache: { enabled: boolean; budget: number; heldBytes: number | null; min: number };
}

/** A library entry the browser may pick, without the access hash it must never see. */
export interface LibraryListing {
  handle: string;
  title: string;
  current: boolean;
}

export class SettingsRuntime {
  private readonly handles = new HandleMap<LibraryCandidate>();
  readonly account: AccountActions;

  constructor(
    private readonly deps: SettingsDeps,
    creds: { apiId: number; apiHash: string },
  ) {
    this.account = new AccountActions(deps, creds);
  }

  async view(): Promise<SettingsView> {
    const telegram = this.deps.connection.current();
    const account = telegram ? await telegram.account() : null;
    const heldBytes = this.deps.cache ? await this.deps.cache.sizeOnDisk() : null;
    const creds = this.account.credentials;
    return {
      telegram: {
        signedIn: telegram !== null,
        connected: telegram?.connected ?? null,
        account: account ? { name: account.name, username: account.username } : null,
        dc: account?.dcId ?? null,
        library: this.deps.channel.title !== null ? { title: this.deps.channel.title } : null,
        app: { apiId: creds.apiId, apiHashSet: creds.apiHash !== "" },
      },
      cache: {
        enabled: this.deps.cache !== null,
        budget: this.deps.cache?.budget ?? 0,
        heldBytes,
        min: MIN_CACHE_BUDGET_BYTES,
      },
    };
  }

  async setCacheBudget(bytes: number): Promise<ActionResult<{ budget: number; freedBytes: number }>> {
    if (!this.deps.cache) return { ok: false, error: "caching is off; there is nothing to size" };
    if (!validateBudget(bytes)) return { ok: false, error: "that is not a size Settings can use" };
    const result = await applyBudget(this.deps.settings, this.deps.cache, this.deps.held, bytes);
    this.deps.invalidateHeldBytes?.();
    return { ok: true, ...result };
  }

  async listLibraries(): Promise<ActionResult<{ libraries: LibraryListing[] }>> {
    const telegram = this.deps.connection.current();
    if (!telegram) return { ok: false, error: "cannot list channels while signed out" };
    let candidates: LibraryCandidate[];
    try {
      candidates = await listLibraries(telegram);
    } catch (error) {
      return { ok: false, error: failureMessage(error) };
    }
    const entries = this.handles.reset(candidates);
    return {
      ok: true,
      libraries: entries.map(({ handle, value }) => ({
        handle,
        title: value.title,
        current: value.chatId === this.deps.channel.chatId,
      })),
    };
  }

  async chooseLibrary(handle: string): Promise<ActionResult<{ title: string; sets: number }>> {
    const candidate = this.handles.get(handle);
    if (!candidate) return { ok: false, error: "that channel is no longer in the list; refresh and choose again" };
    const current = this.deps.connection.current();
    if (!current) return { ok: false, error: "cannot choose a library while signed out" };

    const probe = Telegram.withChannel(current, candidate.chatId, candidate.accessHash);
    const outcome = await this.deps.follower.tryChannel(
      join(this.deps.channelCatalogDir, String(candidate.chatId)),
      () => this.deps.findIndex(probe),
    );
    if (!outcome.served) return { ok: false, error: outcome.reason };

    this.deps.connection.withChannel((existing) => Telegram.withChannel(existing, candidate.chatId, candidate.accessHash));
    this.deps.channel.chatId = candidate.chatId;
    this.deps.channel.accessHash = candidate.accessHash;
    this.deps.channel.title = candidate.title;
    const saved = await this.account.commit();
    if (!saved.ok) return saved;
    return { ok: true, title: candidate.title, sets: outcome.sets };
  }

  async sessions(): Promise<ActionResult<{ sessions: SessionSummary[] }>> {
    const telegram = this.deps.connection.current();
    if (!telegram) return { ok: false, error: "cannot list sessions while signed out" };
    try {
      return { ok: true, sessions: await listSessions(telegram, this.account.credentials.apiId) };
    } catch (error) {
      return { ok: false, error: failureMessage(error) };
    }
  }

  async revokeSession(id: string): Promise<RevokeOutcome> {
    const telegram = this.deps.connection.current();
    if (!telegram) return { ok: false, error: "cannot revoke a session while signed out" };
    return revokeSession(telegram, id);
  }
}
