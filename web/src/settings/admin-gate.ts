/**
 * The lock in front of `/api/settings`.
 *
 * This API otherwise has none: `client-reach.ts` already keeps a caller off
 * this household's network out with a 404, and this gate is the second layer
 * for whoever is on it but should still not see the account's channels or be
 * able to sign it out — a guest on the LAN, or a neighbour sharing the same
 * CGNAT range `isOwnNetwork` also admits.
 *
 * A session is a random id in an HttpOnly cookie, mapped in memory to when it
 * expires; nothing here survives a restart, which is fine — a restart is
 * itself a reason to unlock again. The token is compared by hashing both
 * sides to a fixed-length digest first: `crypto.timingSafeEqual` throws on
 * mismatched lengths, and a comparison that throws faster for a wrong length
 * than for a wrong value is itself a timing side channel.
 */

import { randomBytes, timingSafeEqual, createHash } from "node:crypto";
import { chmod, mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

const COOKIE_NAME = "mediagram_admin";
const SESSION_TTL_MS = 12 * 60 * 60 * 1000;
const RATE_LIMIT_WINDOW_MS = 60_000;
const RATE_LIMIT_MAX_ATTEMPTS = 5;
/** Addresses tracked for rate limiting at once; bounded like `ReadaheadTracker`. */
const MAX_TRACKED_ADDRESSES = 256;

/**
 * The token, from the environment or a file created beside `state.db`.
 *
 * Only the path is ever logged. A file already there is trusted as written by
 * a previous run; nothing here re-derives or validates its contents beyond
 * trimming the trailing newline a shell redirect would leave.
 */
export async function resolveAdminToken(
  env: Record<string, string | undefined>,
  tokenPath: string,
): Promise<string> {
  const fromEnv = env.MEDIAGRAM_ADMIN_TOKEN;
  if (fromEnv) return fromEnv;

  const existing = await readFile(tokenPath, "utf8").catch(() => null);
  if (existing !== null) return existing.trim();

  const token = randomBytes(32).toString("base64url");
  await mkdir(dirname(tokenPath), { recursive: true, mode: 0o700 });
  await writeFile(tokenPath, token, { mode: 0o600 });
  await chmod(tokenPath, 0o600);
  console.log(`settings: admin token created at ${tokenPath}`);
  return token;
}

function timingSafeEqualStrings(a: string, b: string): boolean {
  const left = createHash("sha256").update(a).digest();
  const right = createHash("sha256").update(b).digest();
  return timingSafeEqual(left, right);
}

/** One cookie's value out of a raw `Cookie` header, or `null` when absent. */
export function cookieValue(header: string | null | undefined, name: string): string | null {
  if (!header) return null;
  for (const part of header.split(";")) {
    const eq = part.indexOf("=");
    if (eq < 0) continue;
    if (part.slice(0, eq).trim() === name) return decodeURIComponent(part.slice(eq + 1).trim());
  }
  return null;
}

export type UnlockResult = { ok: true; setCookie: string } | { ok: false; limited: boolean };

export class AdminGate {
  private readonly sessions = new Map<string, number>();
  private readonly attempts = new Map<string, { count: number; windowStart: number }>();

  constructor(
    private readonly token: string,
    private readonly now: () => number = () => Date.now(),
  ) {}

  /** Whether `cookieHeader` names a session that has not expired. */
  isUnlocked(cookieHeader: string | null | undefined): boolean {
    const id = cookieValue(cookieHeader, COOKIE_NAME);
    if (id === null) return false;
    const expiresAt = this.sessions.get(id);
    if (expiresAt === undefined) return false;
    if (expiresAt <= this.now()) {
      this.sessions.delete(id);
      return false;
    }
    return true;
  }

  /**
   * Verifies `candidate` for `address`, minting a session on success.
   *
   * Rate limited before the token is even compared: an address already at
   * the limit gets refused without spending a comparison, so the limit
   * cannot itself be used to keep guessing under cover of "not yet limited".
   */
  unlock(candidate: string, address: string, secure: boolean): UnlockResult {
    if (this.isLimited(address)) return { ok: false, limited: true };
    if (!timingSafeEqualStrings(candidate, this.token)) {
      this.recordFailure(address);
      return { ok: false, limited: false };
    }
    this.attempts.delete(address);
    const id = randomBytes(32).toString("base64url");
    this.sessions.set(id, this.now() + SESSION_TTL_MS);
    this.sweepExpired();
    return { ok: true, setCookie: this.cookie(id, SESSION_TTL_MS / 1000, secure) };
  }

  /** Ends the session `cookieHeader` names, and answers the cookie to clear it. */
  lock(cookieHeader: string | null | undefined, secure: boolean): string {
    const id = cookieValue(cookieHeader, COOKIE_NAME);
    if (id !== null) this.sessions.delete(id);
    return this.cookie("", 0, secure);
  }

  private cookie(value: string, maxAgeSeconds: number, secure: boolean): string {
    const parts = [
      `${COOKIE_NAME}=${value}`,
      "HttpOnly",
      "SameSite=Strict",
      "Path=/api/settings",
      `Max-Age=${maxAgeSeconds}`,
    ];
    if (secure) parts.push("Secure");
    return parts.join("; ");
  }

  private isLimited(address: string): boolean {
    const entry = this.attempts.get(address);
    if (!entry) return false;
    if (this.now() - entry.windowStart > RATE_LIMIT_WINDOW_MS) {
      this.attempts.delete(address);
      return false;
    }
    return entry.count >= RATE_LIMIT_MAX_ATTEMPTS;
  }

  private recordFailure(address: string): void {
    const now = this.now();
    const entry = this.attempts.get(address);
    if (!entry || now - entry.windowStart > RATE_LIMIT_WINDOW_MS) {
      this.attempts.delete(address);
      this.attempts.set(address, { count: 1, windowStart: now });
    } else {
      entry.count += 1;
    }
    if (this.attempts.size > MAX_TRACKED_ADDRESSES) {
      const oldest = this.attempts.keys().next();
      if (!oldest.done) this.attempts.delete(oldest.value);
    }
  }

  private sweepExpired(): void {
    const now = this.now();
    for (const [id, expiresAt] of this.sessions) {
      if (expiresAt <= now) this.sessions.delete(id);
    }
  }
}
