/**
 * Everything about the account itself: which app id/hash speaks for it,
 * whether it is signed in, and ending or starting a session.
 *
 * Split out of `context.ts`'s `SettingsRuntime`, which delegates every
 * identity action here and keeps the cache and library actions, which touch
 * a different set of collaborators.
 */

import { Api } from "teleproto";
import type { Config } from "../config";
import { Telegram } from "../telegram/client";
import type { TelegramConnection } from "../telegram/connection";
import type { ChannelState, UpdatesBinding } from "../application/telegram-binding";
import { SignInFlow, type SignInStep } from "./sign-in";
import { writeTelegramFile, type TelegramFile } from "./telegram-file";
import { failureMessage } from "../failure-message";
import type { ActionResult } from "./context";

export interface AccountDeps {
  connection: TelegramConnection;
  channel: ChannelState;
  updatesBinding: UpdatesBinding;
  telegramFilePath: string;
}

export class AccountActions {
  constructor(
    private readonly deps: AccountDeps,
    private creds: { apiId: number; apiHash: string },
    private accountUserId: string | null,
    /** Overridable so a stub harness can answer sign-in without teleproto ever reaching Telegram. */
    private readonly signInFlow: SignInFlow = new SignInFlow(),
  ) {}

  get credentials(): { apiId: number; apiHash: string } {
    return this.creds;
  }

  async setAppCredentials(apiId: number, apiHash: string): Promise<ActionResult<{ signedIn: boolean; connected: boolean | null }>> {
    if (!Number.isInteger(apiId) || apiId <= 0) return { ok: false, error: "the application id must be a positive number" };
    if (!/^[0-9a-f]{32}$/i.test(apiHash)) return { ok: false, error: "the application hash is 32 hexadecimal characters" };

    const previous = this.creds;
    const session = this.sessionOf(this.deps.connection.current());
    try {
      await this.deps.connection.restart(
        () => Telegram.open(this.configFor(apiId, apiHash, session)),
        () => Telegram.open(this.configFor(previous.apiId, previous.apiHash, session)),
      );
    } catch (error) {
      return { ok: false, error: failureMessage(error) };
    }
    this.creds = { apiId, apiHash };
    await (await this.deps.updatesBinding.rebind()).ready;
    await this.persist();
    const telegram = this.deps.connection.current();
    return { ok: true, signedIn: telegram !== null, connected: telegram?.connected ?? null };
  }

  async signInPhone(phoneNumber: string): Promise<ActionResult<SignInStep>> {
    try {
      return { ok: true, ...(await this.signInFlow.phone(this.creds.apiId, this.creds.apiHash, phoneNumber)) };
    } catch (error) {
      return { ok: false, error: failureMessage(error) };
    }
  }

  async signInCode(code: string): Promise<ActionResult<SignInStep & { differentAccount?: boolean }>> {
    return this.advanceSignIn(() => this.signInFlow.code(code));
  }

  async signInPassword(password: string): Promise<ActionResult<SignInStep & { differentAccount?: boolean }>> {
    return this.advanceSignIn(() => this.signInFlow.password(password));
  }

  /**
   * `differentAccount` warns the caller that the previously chosen channel's
   * access hash belongs to the account just left: it stops answering for
   * this one, and the browser should offer "Change library" next. It is not
   * cleared here — the same recoverable failure a channel that changed
   * ownership out from under this account would produce either way.
   */
  private async advanceSignIn(
    step: () => Promise<SignInStep>,
  ): Promise<ActionResult<SignInStep & { differentAccount?: boolean }>> {
    let result: SignInStep;
    try {
      result = await step();
    } catch (error) {
      return { ok: false, error: failureMessage(error) };
    }
    if (result.step !== "done") return { ok: true, ...result };

    const signedInBefore = this.deps.connection.current();
    const differentAccount = this.accountUserId !== null && this.accountUserId !== result.userId;
    // Best effort, and only while the old session is still connected: once
    // `restart` disconnects it, Telegram no longer takes requests on it.
    if (differentAccount && signedInBefore) await this.logOut(signedInBefore).catch(() => {});

    try {
      await this.deps.connection.restart(() => Telegram.open(this.configFor(this.creds.apiId, this.creds.apiHash, result.session)));
    } catch (error) {
      return { ok: false, error: failureMessage(error) };
    }
    this.accountUserId = result.userId;
    await (await this.deps.updatesBinding.rebind()).ready;
    await this.persist();
    return { ok: true, ...result, differentAccount };
  }

  async signOut(): Promise<{ ok: true } | { ok: false; error: string }> {
    const telegram = this.deps.connection.current();
    if (telegram) await this.logOut(telegram).catch(() => {});
    await this.deps.connection.restart(() => Promise.resolve(null));
    await (await this.deps.updatesBinding.rebind()).ready;
    await this.persist();
    return { ok: true };
  }

  /** Best effort, capped: an offline account still forgets the local session. */
  private async logOut(telegram: Telegram): Promise<void> {
    await Promise.race([
      telegram.client.invoke(new Api.auth.LogOut()),
      new Promise((resolve) => setTimeout(resolve, 10_000)),
    ]);
  }

  private sessionOf(telegram: Telegram | null): string | null {
    if (!telegram) return null;
    return telegram.client.session.save() as unknown as string;
  }

  /**
   * A `Config` shaped just enough for `Telegram.open`, which reads only
   * these five fields. Cast rather than padded with the rest of `Config`'s
   * unrelated fields (cache, paths, ports) that a connection attempt never
   * touches.
   */
  private configFor(apiId: number, apiHash: string, session: string | null): Config {
    return {
      apiId,
      apiHash,
      session,
      chatId: this.deps.channel.chatId,
      channelAccessHash: this.deps.channel.accessHash,
    } as Config;
  }

  /** Writes the account and chosen channel to disk. Public: a library switch persists through here too. */
  async persist(): Promise<void> {
    const file: TelegramFile = {
      apiId: this.creds.apiId,
      apiHash: this.creds.apiHash,
      session: this.sessionOf(this.deps.connection.current()),
      chatId: this.deps.channel.chatId,
      accessHash: String(this.deps.channel.accessHash),
      title: this.deps.channel.title,
    };
    await writeTelegramFile(this.deps.telegramFilePath, file);
  }
}
