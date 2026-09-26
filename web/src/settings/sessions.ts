/**
 * The account's active sessions: which devices are signed in through this
 * app, and revoking one remotely.
 *
 * Scoped to this app's `api_id` (plus the current row, which has no `api_id`
 * of its own to compare): the account's official Telegram apps are not this
 * app's business, and revoking one from a media player would be out of
 * proportion — they keep doing that in Telegram. `shape` and `revokeError`
 * are pure and pinned to `web/test/fixtures/authorizations/cases.json`,
 * which `crates/mediagram-core`'s own port of this reads too.
 */

import { Api } from "teleproto";
import type { Telegram } from "../telegram/client";
import { failureMessage } from "../failure-message";

export interface RawAuthorization {
  current: boolean;
  officialApp: boolean;
  unconfirmed: boolean;
  /** Decimal, because a bigint does not survive JSON or `JSON.stringify`. */
  hash: string;
  deviceModel: string;
  platform: string;
  apiId: number;
  appName: string;
  appVersion: string;
  /** Seconds since the epoch. */
  dateCreated: number;
  dateActive: number;
  country: string;
  region: string;
}

export interface SessionSummary {
  id: string;
  device: string;
  platform: string;
  app: string;
  appVersion: string;
  /** `country` alone, or `country, region` — never the address itself. */
  location: string | null;
  lastActive: number;
  created: number;
  current: boolean;
  unconfirmed: boolean;
}

function locationOf(row: RawAuthorization): string | null {
  const country = row.country.trim();
  if (country === "") return null;
  const region = row.region.trim();
  return region === "" ? country : `${country}, ${region}`;
}

/** This app's sessions, plus the current one, newest active first. */
export function shape(raw: RawAuthorization[], apiId: number): SessionSummary[] {
  return raw
    .filter((row) => row.current || row.apiId === apiId)
    .map((row) => ({
      id: row.hash,
      device: row.deviceModel,
      platform: row.platform,
      app: row.appName,
      appVersion: row.appVersion,
      location: locationOf(row),
      lastActive: row.dateActive,
      created: row.dateCreated,
      current: row.current,
      unconfirmed: row.unconfirmed,
    }))
    .sort((a, b) => b.lastActive - a.lastActive);
}

/** Whether revoking `id` is refused before any request: the current session has no button. */
export function isCurrentSession(id: string): boolean {
  return id === "0";
}

/** A sentence for a revoke that failed, from Telegram's own reason. */
export function revokeError(rpcName: string | undefined): string {
  if (rpcName === "FRESH_RESET_AUTHORISATION_FORBIDDEN") {
    return "This device signed in less than a day ago; Telegram allows removing other sessions after 24 hours.";
  }
  return rpcName ? `Telegram refused (${rpcName})` : "Telegram could not be reached";
}

function toRaw(entry: Api.TypeAuthorization): RawAuthorization {
  const a = entry as Api.Authorization;
  return {
    current: a.current === true,
    officialApp: a.officialApp === true,
    unconfirmed: a.unconfirmed === true,
    hash: String(a.hash),
    deviceModel: a.deviceModel,
    platform: a.platform,
    apiId: a.apiId,
    appName: a.appName,
    appVersion: a.appVersion,
    dateCreated: a.dateCreated,
    dateActive: a.dateActive,
    country: a.country,
    region: a.region,
  };
}

/** This app's sessions, read live. Never returns the current session's IP. */
export async function listSessions(telegram: Telegram, apiId: number): Promise<SessionSummary[]> {
  const answer = await telegram.client.invoke(new Api.account.GetAuthorizations());
  const raw = (answer as Api.account.Authorizations).authorizations.map(toRaw);
  return shape(raw, apiId);
}

export type RevokeOutcome = { ok: true } | { ok: false; error: string };

/** Revokes `id`. A session already gone counts as success — it is gone either way. */
export async function revokeSession(telegram: Telegram, id: string): Promise<RevokeOutcome> {
  if (isCurrentSession(id)) return { ok: false, error: "sign out to end this device's own session" };
  let hash: bigint;
  try {
    hash = BigInt(id);
  } catch {
    return { ok: false, error: "not a session id" };
  }
  try {
    await telegram.client.invoke(new Api.account.ResetAuthorization({ hash: hash as never }));
    return { ok: true };
  } catch (error) {
    const rpcName = (error as { errorMessage?: string } | null)?.errorMessage;
    if (rpcName === "HASH_INVALID") return { ok: true };
    return { ok: false, error: revokeError(rpcName ?? failureMessage(error)) };
  }
}
