/**
 * Everything a settings action needs to touch, in one place.
 *
 * `web/src/settings/routes.ts` is HTTP shape only — parsing a body, checking
 * the gate, turning an outcome into a status code. Every actual effect (a
 * restart, a channel switch, a budget change) happens here, against the same
 * connection, channel state and catalog follower `index.ts` built at
 * startup, so a setting changed from the browser is indistinguishable from
 * one that had always been that way.
 *
 * Account identity (app id/hash, sign in/out) is `account-actions.ts`; this
 * file keeps the cache and library actions, and the read view both draw on.
 *
 * Order throughout is prove, then persist, then swap: nothing is written to
 * `telegram.json` until the change it describes actually took.
 */

import type { StartupFacts } from "../status/facts";
import { Telegram } from "../telegram/client";
import type { TelegramConnection } from "../telegram/connection";
import type { ChannelState, UpdatesBinding } from "../application/telegram-binding";
import type { CatalogFollower } from "../application/catalog-follow";
import type { ChunkCache } from "../cache/store";
import type { HeldSets } from "../cache/held";
import { applyBudget, validateBudget, MIN_CACHE_BUDGET_BYTES } from "../cache/budget";
import type { Settings } from "../state/settings";
import { listLibraries, type LibraryCandidate } from "../telegram/libraries";
import { HandleMap } from "./handles";
import type { SignInStep } from "./sign-in";
import { AccountActions } from "./account-actions";
import { failureMessage } from "../failure-message";
import { join } from "node:path";

export interface SettingsDeps {
  connection: TelegramConnection;
  channel: ChannelState;
  updatesBinding: UpdatesBinding;
  follower: Pick<CatalogFollower, "refresh" | "retarget">;
  facts: StartupFacts;
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

export type ActionResult<T> = ({ ok: true } & T) | { ok: false; error: string };

/** A library entry the browser may pick, without the access hash it must never see. */
export interface LibraryListing {
  handle: string;
  title: string;
  current: boolean;
}

export class SettingsRuntime {
  private readonly handles = new HandleMap<LibraryCandidate>();
  private readonly account: AccountActions;

  constructor(
    private readonly deps: SettingsDeps,
    creds: { apiId: number; apiHash: string },
    accountUserId: string | null,
  ) {
    this.account = new AccountActions(deps, creds, accountUserId);
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
    if (!this.deps.connection.current()) return { ok: false, error: "cannot choose a library while signed out" };

    this.deps.connection.withChannel((existing) => Telegram.withChannel(existing, candidate.chatId, candidate.accessHash));
    this.deps.follower.retarget(join(this.deps.channelCatalogDir, String(candidate.chatId)));
    const publishedBefore = this.deps.facts.catalog.publishedAt;
    await this.deps.follower.refresh();

    if (this.deps.facts.catalog.origin !== "channel" || this.deps.facts.catalog.publishedAt === publishedBefore) {
      return { ok: false, error: this.deps.facts.catalog.reason ?? "the channel's index could not be installed" };
    }

    this.deps.channel.chatId = candidate.chatId;
    this.deps.channel.accessHash = candidate.accessHash;
    this.deps.channel.title = candidate.title;
    await (await this.deps.updatesBinding.rebind()).ready;
    await this.account.persist();
    return { ok: true, title: candidate.title, sets: this.deps.facts.catalog.sets };
  }

  setAppCredentials(apiId: number, apiHash: string): Promise<ActionResult<{ signedIn: boolean; connected: boolean | null }>> {
    return this.account.setAppCredentials(apiId, apiHash);
  }

  signInPhone(phoneNumber: string): Promise<ActionResult<SignInStep>> {
    return this.account.signInPhone(phoneNumber);
  }

  signInCode(code: string): Promise<ActionResult<SignInStep & { differentAccount?: boolean }>> {
    return this.account.signInCode(code);
  }

  signInPassword(password: string): Promise<ActionResult<SignInStep & { differentAccount?: boolean }>> {
    return this.account.signInPassword(password);
  }

  signOut(): Promise<{ ok: true } | { ok: false; error: string }> {
    return this.account.signOut();
  }
}
