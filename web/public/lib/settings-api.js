/**
 * The Settings page's one way of talking to `/api/settings/*`.
 *
 * Every call here is same-origin, JSON, and answers a plain `{ok, ...}` or
 * `{ok:false, error}` shape so the view never has to branch on HTTP status
 * beyond "locked" and "not found". Pure size parsing lives here too, so it
 * can be tested without a browser.
 */

const BASE = "/api/settings";

/** A size with an optional unit — `8G`, `512M`, `1024` — matching the server's own `parseSize`. */
export function parseSizeInput(text) {
  const match = /^(\d+(?:\.\d+)?)\s*([KMGT])?B?$/i.exec(String(text).trim());
  if (!match) return null;
  const scale = { k: 1024, m: 1024 ** 2, g: 1024 ** 3, t: 1024 ** 4 }[(match[2] ?? "").toLowerCase()];
  return Math.floor(Number(match[1]) * (scale ?? 1));
}

/** The inverse of `parseSizeInput`, for prefilling a form: `8589934592` becomes `8G`. */
export function formatSizeInput(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return "";
  const units = [["T", 1024 ** 4], ["G", 1024 ** 3], ["M", 1024 ** 2]];
  for (const [suffix, scale] of units) {
    if (bytes % scale === 0) return `${bytes / scale}${suffix}`;
  }
  return String(bytes);
}

async function call(path, options = {}) {
  let response;
  try {
    response = await fetch(`${BASE}${path}`, {
      method: options.method ?? "GET",
      headers: options.body ? { "content-type": "application/json" } : undefined,
      body: options.body ? JSON.stringify(options.body) : undefined,
    });
  } catch {
    return { ok: false, locked: false, status: 0, error: "the player did not answer" };
  }
  if (response.status === 404) return { ok: false, locked: false, status: 404, error: "not found" };
  if (response.status === 401) return { ok: false, locked: true, status: 401, error: "locked" };
  let body = {};
  try {
    body = await response.json();
  } catch {
    // A bodiless answer (204) is still a success.
  }
  if (!response.ok) return { ok: false, locked: false, status: response.status, error: body.error ?? `answered ${response.status}` };
  return { ok: true, locked: false, status: response.status, ...body };
}

export const probeSettings = () => fetch(`${BASE}`, { method: "HEAD" });
export const readSettings = () => call("");
export const unlockSettings = (token) => call("/unlock", { method: "POST", body: { token } });
export const lockSettings = () => call("/lock", { method: "POST", body: {} });
export const setCacheBudget = (maxBytes) => call("/cache", { method: "PUT", body: { maxBytes } });
export const listLibraries = () => call("/libraries");
export const chooseLibrary = (handle) => call("/library", { method: "POST", body: { handle } });
export const setAppCredentials = (apiId, apiHash) => call("/telegram/app", { method: "PUT", body: { apiId, apiHash } });
export const signInPhone = (phone) => call("/telegram/sign-in/phone", { method: "POST", body: { phone } });
export const signInCode = (code) => call("/telegram/sign-in/code", { method: "POST", body: { code } });
export const signInPassword = (password) => call("/telegram/sign-in/password", { method: "POST", body: { password } });
export const signOutTelegram = () => call("/telegram/sign-out", { method: "POST", body: {} });
export const listSessions = () => call("/sessions");
export const revokeSession = (id) => call("/sessions/revoke", { method: "POST", body: { id } });
