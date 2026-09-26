/**
 * Signing in from the browser: phone → code → password, one step per request.
 *
 * Runs on its own client, on an empty session — a fresh auth key entirely
 * separate from the live one, so an attempt in progress never risks the
 * two-clients-on-one-key failure a restart must avoid (`telegram/client.ts`).
 * Only a finished attempt's session string ever reaches the caller; the
 * pending client itself never becomes the live one, so the live connection
 * always goes through the one path that opens it (`Telegram.open`).
 *
 * The exact shape of `auth.SendCode` / `auth.SignIn` / `auth.CheckPassword`
 * below is not exercised against a real account by any test in this
 * project — sign-in is verified only against the stub harness's fake
 * client, which accepts a fixed code and password. A live mismatch would
 * surface as this module's own error text, not a crash.
 */

import { Api, TelegramClient, sessions } from "teleproto";
import { computeCheck } from "teleproto/Password";
import { sessionName } from "../telegram/session-name";
import { failureMessage } from "../failure-message";

/** How long an attempt may sit between steps before it is discarded. */
const PENDING_TTL_MS = 10 * 60_000;

export type SignInStep =
  | { step: "code"; viaApp: boolean }
  | { step: "password" }
  | { step: "done"; session: string; userId: string };

export type SignInClient = Pick<
  TelegramClient,
  "connect" | "disconnect" | "destroy" | "invoke" | "session"
>;

interface Pending {
  client: SignInClient;
  phone: string;
  phoneCodeHash: string;
  expiresAt: number;
}

function passwordNeeded(error: unknown): boolean {
  return (error as { errorMessage?: unknown } | null)?.errorMessage === "SESSION_PASSWORD_NEEDED";
}

export class SignInFlow {
  private pending: Pending | null = null;

  constructor(
    private readonly makeClient: (apiId: number, apiHash: string) => SignInClient = (apiId, apiHash) =>
      new TelegramClient(new sessions.StringSession(""), apiId, apiHash, {
        connectionRetries: 3,
        // Report a flood wait as this attempt's own failure rather than
        // sleeping through it while the viewer waits on the request.
        floodSleepThreshold: 0,
        ...sessionName(),
      }),
    private readonly now: () => number = () => Date.now(),
  ) {}

  /** Whether an attempt is waiting on its next step. */
  waiting(): boolean {
    this.expireStale();
    return this.pending !== null;
  }

  /** Discards any attempt in progress, disconnecting its client. */
  async cancel(): Promise<void> {
    const pending = this.pending;
    this.pending = null;
    if (pending) await pending.client.disconnect().catch(() => {});
  }

  /** Begins a new attempt, replacing any pending one. */
  async phone(apiId: number, apiHash: string, phoneNumber: string): Promise<SignInStep> {
    await this.cancel();
    const client = this.makeClient(apiId, apiHash);
    await client.connect();
    try {
      const sent = await client.invoke(
        new Api.auth.SendCode({ phoneNumber, apiId, apiHash, settings: new Api.CodeSettings({}) }),
      );
      if (!(sent instanceof Api.auth.SentCode)) throw new Error("Telegram answered unexpectedly to the phone number");
      this.pending = {
        client,
        phone: phoneNumber,
        phoneCodeHash: sent.phoneCodeHash,
        expiresAt: this.now() + PENDING_TTL_MS,
      };
      return { step: "code", viaApp: sent.type instanceof Api.auth.SentCodeTypeApp };
    } catch (error) {
      await client.disconnect().catch(() => {});
      throw new Error(failureMessage(error));
    }
  }

  /** The code Telegram sent. */
  async code(code: string): Promise<SignInStep> {
    const pending = this.activePending();
    try {
      const result = await pending.client.invoke(
        new Api.auth.SignIn({ phoneNumber: pending.phone, phoneCodeHash: pending.phoneCodeHash, phoneCode: code }),
      );
      if (result instanceof Api.auth.AuthorizationSignUpRequired) {
        throw new Error("this phone number has no Telegram account to sign in to");
      }
      return await this.finish(result);
    } catch (error) {
      if (passwordNeeded(error)) return { step: "password" };
      throw new Error(failureMessage(error));
    }
  }

  /** The account's 2FA password, when Telegram asked for one after the code. */
  async password(password: string): Promise<SignInStep> {
    const pending = this.activePending();
    try {
      const info = await pending.client.invoke(new Api.account.GetPassword());
      const check = await computeCheck(info, password);
      const result = await pending.client.invoke(new Api.auth.CheckPassword({ password: check }));
      return await this.finish(result);
    } catch (error) {
      throw new Error(failureMessage(error));
    }
  }

  private activePending(): Pending {
    this.expireStale();
    if (!this.pending) throw new Error("no sign-in is in progress; start again with a phone number");
    return this.pending;
  }

  private expireStale(): void {
    if (this.pending && this.pending.expiresAt <= this.now()) {
      void this.pending.client.disconnect().catch(() => {});
      this.pending = null;
    }
  }

  private async finish(result: unknown): Promise<SignInStep> {
    const pending = this.pending!;
    this.pending = null;
    const session = pending.client.session.save() as unknown as string;
    const authorized = result as { user?: { id?: { toString(): string } } };
    const userId = String(authorized.user?.id ?? "");
    await pending.client.disconnect().catch(() => {});
    await pending.client.destroy?.().catch(() => {});
    return { step: "done", session, userId };
  }
}
