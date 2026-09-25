/** Public assets and the installed HLS client, with bounded filesystem paths. */
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { join, normalize } from "node:path";
import type { PlayerRequest, PlayerResponse } from "./contracts";
import { bodiless, withBody } from "../response";

/** The page and its script, served from `web/public`. */
const PUBLIC_DIR = new URL("../../public/", import.meta.url).pathname;

/**
 * hls.js, served out of the installed package rather than copied into
 * `public`.
 *
 * Only Safari plays an HLS playlist from a plain `<video src>`; in Chrome and
 * Firefox the transcode route would be unreachable without this. Reading it
 * from the dependency keeps one copy of it and ties its version to the
 * lockfile instead of to whenever someone last re-copied the file.
 */
const HLS_LIBRARY_PATH = "/lib/hls.mjs";

let hlsLibrary: Uint8Array | null = null;

function hlsLibraryBytes(): Uint8Array {
  if (hlsLibrary === null) {
    const resolve = createRequire(import.meta.url).resolve;
    hlsLibrary = new Uint8Array(readFileSync(resolve("hls.js/dist/hls.min.mjs")));
  }
  return hlsLibrary;
}

const CONTENT_TYPES: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".woff2": "font/woff2",
};

/**
 * Reads a file from the public directory, or `null`.
 *
 * The path is normalized and then checked to still start with the public
 * directory, so `..` cannot walk out of it and serve, say, the session file
 * sitting two levels up.
 */
function staticFile(urlPath: string): { body: Uint8Array; type: string } | null {
  let relative: string;
  try {
    relative = urlPath === "/" ? "index.html" : decodeURIComponent(urlPath).replace(/^\/+/, "");
  } catch {
    return null; // Malformed URL escapes name no file.
  }
  if (relative.includes("\0")) return null;
  const resolved = normalize(join(PUBLIC_DIR, relative));
  if (!resolved.startsWith(PUBLIC_DIR)) return null;

  try {
    const dot = resolved.lastIndexOf(".");
    const type = CONTENT_TYPES[resolved.slice(dot)] ?? "application/octet-stream";
    return { body: new Uint8Array(readFileSync(resolved)), type };
  } catch (error) {
    const code = (error as NodeJS.ErrnoException).code;
    if (code === "ENOENT" || code === "ENOTDIR" || code === "EISDIR") return null;
    throw error;
  }
}

export function staticResponse(request: PlayerRequest): PlayerResponse {
  const headOnly = request.method === "HEAD";
  if (request.path === HLS_LIBRARY_PATH) {
    return withBody(hlsLibraryBytes(), "text/javascript; charset=utf-8", {
      headOnly, headers: { "cache-control": "public, max-age=86400" },
    });
  }
  if (!request.path.startsWith("/api/")) {
    const file = staticFile(request.path);
    if (file !== null) return withBody(file.body, file.type, {
      headOnly, headers: { "cache-control": "no-cache" },
    });
  }
  return bodiless(404);
}
