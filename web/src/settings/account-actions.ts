/**
 * Everything about the account itself: which app id/hash speaks for it,
 * whether it is signed in, and ending or starting a session.
 *
 * The settings router reaches it as `SettingsRuntime.account`; the runtime
 * keeps the cache and library actions, which touch a different set of
 * collaborators.
 */

import { Api } from "teleproto";
import type { Config } from "../config";
import { Telegram } from "../telegram/client";
import type { TelegramConnection } from "../telegram/connection";
import type { ChannelState, UpdatesBinding } from "../application/telegram-binding";
import { SignInFlow, type SignInStep } from "./sign-in";
import { writeTelegramFile, type TelegramFile } from "./telegram-file";
import { failureMessage } from "../failure-message";

export type ActionResult<T> = ({ ok: true } & T) | { ok: false; error: string };

export interface AccountDeps {
  connection: TelegramConnection;
  channel: ChannelState;
  updatesBinding: UpdatesBinding;
  telegramFilePath: string;
}

export class AccountActions {
  private readonly signInFlow = new SignInFlow();

  constructor(
    private readonly deps: AccountDeps,
    private creds: { apiId: number; apiHash: string },
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
    const saved = await this.commit();
    if (!saved.ok) return saved;
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

  async signInCode(code: string): Promise<ActionResult<SignInStep>> {
    return this.advanceSignIn(() => this.signInFlow.code(code));
  }

  async signInPassword(password: string): Promise<ActionResult<SignInStep>> {
    return this.advanceSignIn(() => this.signInFlow.password(password));
  }

  private async advanceSignIn(step: () => Promise<SignInStep>): Promise<ActionResult<SignInStep>> {
    let result: SignInStep;
    try {
      result = await step();
    } catch (error) {
      return { ok: false, error: failureMessage(error) };
    }
    if (result.step !== "done") return { ok: true, ...result };

    try {
      await this.deps.connection.restart(() => Telegram.open(this.configFor(this.creds.apiId, this.creds.apiHash, result.session)));
    } catch (error) {
      return { ok: false, error: failureMessage(error) };
    }
    const saved = await this.commit();
    if (!saved.ok) return saved;
    return { ok: true, ...result };
  }

  async signOut(): Promise<{ ok: true } | { ok: false; error: string }> {
    const telegram = this.deps.connection.current();
    if (telegram) await this.logOut(telegram).catch(() => {});
    await this.deps.connection.restart(() => Promise.resolve(null));
    return this.commit();
  }

  /**
   * Rebinds the listener to the connection and channel as they now are, then
   * saves them. The change has already taken effect by now, so a failed save
   * is reported rather than thrown: the caller still answers, and says the
   * change will not survive a restart.
   */
  async commit(): Promise<{ ok: true } | { ok: false; error: string }> {
    await this.deps.updatesBinding.rebind();
    try {
      await this.persist();
    } catch (error) {
      return { ok: false, error: `the change took effect but could not be saved: ${failureMessage(error)}` };
    }
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

  /** Writes the account and chosen channel to disk. */
  private async persist(): Promise<void> {
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
