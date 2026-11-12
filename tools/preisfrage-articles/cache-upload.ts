// Writing to the home cache server: where it is, and the signature every PUT carries.
import { createHash, createHmac } from "node:crypto";
import { readFileSync } from "node:fs";

export type CacheServer = { server: string; token: string };

/** `cache_server` and `cache_server_token` from the mediagram config, as `mediagram posters` reads them. */
export function readCacheConfig(file: string): CacheServer {
  let text: string;
  try {
    text = readFileSync(file, "utf8");
  } catch {
    throw new Error(`no mediagram config at ${file}`);
  }
  const config = Bun.TOML.parse(text) as Record<string, unknown>;
  const server = String(config.cache_server ?? "").replace(/\/+$/, "");
  const token = String(config.cache_server_token ?? "");
  if (!server || !token) {
    throw new Error(
      `${file} needs cache_server (e.g. "http://funstation:7788") and cache_server_token ` +
        "(on the funstation: sudo cat /var/lib/mediagram-cache/token)",
    );
  }
  return { server, token };
}

/** `HMAC-SHA256(token, "{method}\n{path}\n{len}\n" + hex(sha256(body)))`, keyed on the token's hex text. */
export function sign(token: string, method: string, path: string, body: Uint8Array): string {
  const digest = createHash("sha256").update(body).digest("hex");
  return createHmac("sha256", token).update(`${method}\n${path}\n${body.length}\n${digest}`).digest("hex");
}

export async function putBlob(cache: CacheServer, path: string, body: Uint8Array<ArrayBuffer>, type: string): Promise<void> {
  const res = await fetch(cache.server + path, {
    method: "PUT",
    headers: { authorization: `MGC1 ${sign(cache.token, "PUT", path, body)}`, "content-type": type },
    body,
  });
  if (!res.ok) throw new Error(`PUT ${path}: HTTP ${res.status}`);
}
