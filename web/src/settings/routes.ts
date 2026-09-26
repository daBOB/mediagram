/**
 * `/api/settings/*`: the one router with its own lock in front of it.
 *
 * 404 off this household's network, the same rule `status/routes.ts` uses —
 * there is nothing here to confirm to an outside caller either. On the
 * network, every path past `/unlock` and `/lock` also needs an unlocked
 * admin cookie, and every write needs the same-origin, JSON-only guard every
 * other write route in this project already enforces.
 */

import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { isOwnNetwork } from "../client-reach";
import { refuseUnsafeBrowserWrite } from "../http/browser-write";
import { bodiless, withBody } from "../response";
import type { AdminGate } from "./admin-gate";
import type { ActionResult, SettingsRuntime } from "./context";

const PREFIX = "/api/settings";

export interface SettingsRouterOptions {
  gate: AdminGate;
  runtime: SettingsRuntime;
  /** Whether a `Set-Cookie` this response carries should be marked `Secure`. */
  secure: (request: PlayerRequest) => boolean;
}

const json = (body: unknown, status = 200, setCookie?: string): PlayerResponse =>
  withBody(JSON.stringify(body), "application/json", {
    status,
    headers: { "cache-control": "no-store", ...(setCookie ? { "set-cookie": setCookie } : {}) },
  });

/** Turns an action's outcome into a response, `ok` first. */
function answer<T extends object>(result: ActionResult<T>): PlayerResponse {
  if (result.ok) {
    const { ok: _ok, ...rest } = result;
    return json(rest);
  }
  return json({ error: result.error }, 400);
}

function parse(body: string | null | undefined): Record<string, unknown> {
  if (typeof body !== "string" || body === "") return {};
  try {
    const value: unknown = JSON.parse(body);
    return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

export function createSettingsRouter(options: SettingsRouterOptions) {
  const { gate, runtime } = options;

  return async function settingsRoute(request: PlayerRequest): Promise<PlayerResponse | null> {
    if (!request.path.startsWith(PREFIX)) return null;
    if (!isOwnNetwork(request.client ?? "")) return bodiless(404);

    const reading = request.method === "GET" || request.method === "HEAD";
    if (!reading) {
      const refusal = refuseUnsafeBrowserWrite(request);
      if (refusal) return refusal;
    }

    if (request.path === `${PREFIX}/unlock`) {
      if (request.method !== "POST") return bodiless(405);
      const token = parse(request.body).token;
      if (typeof token !== "string") return json({ error: "a token is required" }, 400);
      const secure = options.secure(request);
      const result = gate.unlock(token, request.client ?? "", secure);
      if (!result.ok) {
        return json(
          { error: result.limited ? "too many attempts; wait a minute" : "that token does not match" },
          result.limited ? 429 : 401,
        );
      }
      return json({ locked: false }, 200, result.setCookie);
    }

    if (request.path === `${PREFIX}/lock`) {
      if (request.method !== "POST") return bodiless(405);
      return json({ locked: true }, 200, gate.lock(request.cookie));
    }

    if (!gate.isUnlocked(request.cookie)) return json({ locked: true }, 401);

    if (request.path === PREFIX) {
      if (!reading) return bodiless(405);
      return json(await runtime.view(), 200, undefined);
    }

    if (request.path === `${PREFIX}/cache`) {
      if (request.method !== "PUT") return bodiless(405);
      const maxBytes = Number(parse(request.body).maxBytes);
      if (!Number.isFinite(maxBytes)) return json({ error: "a byte count is required" }, 400);
      return answer(await runtime.setCacheBudget(maxBytes));
    }

    if (request.path === `${PREFIX}/libraries`) {
      if (!reading) return bodiless(405);
      return answer(await runtime.listLibraries());
    }

    if (request.path === `${PREFIX}/library`) {
      if (request.method !== "POST") return bodiless(405);
      const handle = parse(request.body).handle;
      if (typeof handle !== "string") return json({ error: "a channel is required" }, 400);
      return answer(await runtime.chooseLibrary(handle));
    }

    if (request.path === `${PREFIX}/telegram/app`) {
      if (request.method !== "PUT") return bodiless(405);
      const body = parse(request.body);
      const apiId = Number(body.apiId);
      const apiHash = body.apiHash;
      if (typeof apiHash !== "string") return json({ error: "an application hash is required" }, 400);
      return answer(await runtime.setAppCredentials(apiId, apiHash));
    }

    if (request.path === `${PREFIX}/telegram/sign-in/phone`) {
      if (request.method !== "POST") return bodiless(405);
      const phone = parse(request.body).phone;
      if (typeof phone !== "string") return json({ error: "a phone number is required" }, 400);
      return answer(await runtime.signInPhone(phone));
    }

    if (request.path === `${PREFIX}/telegram/sign-in/code`) {
      if (request.method !== "POST") return bodiless(405);
      const code = parse(request.body).code;
      if (typeof code !== "string") return json({ error: "a code is required" }, 400);
      return answer(await runtime.signInCode(code));
    }

    if (request.path === `${PREFIX}/telegram/sign-in/password`) {
      if (request.method !== "POST") return bodiless(405);
      const password = parse(request.body).password;
      if (typeof password !== "string") return json({ error: "a password is required" }, 400);
      return answer(await runtime.signInPassword(password));
    }

    if (request.path === `${PREFIX}/telegram/sign-out`) {
      if (request.method !== "POST") return bodiless(405);
      return answer(await runtime.signOut());
    }

    return json({ error: "not found" }, 404);
  };
}
