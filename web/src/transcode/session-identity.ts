/**
 * What a session *is*, apart from the bookkeeping that tracks it running.
 *
 * Kept apart from `registry.ts`, which was already at this codebase's file
 * size limit: a session's identity — its mode, and the hash that names its
 * directory — does not need any of the registry's runtime state to work out.
 */

import { createHash } from "node:crypto";
import type { SessionSpec } from "./registry";

export type TranscodeMode = "encode" | "copy" | "hevc-copy";

/** What a session's picture is, in the words the panel and the hash below both use. */
export function modeOf(spec: Pick<SessionSpec, "copyVideo" | "hevcCopy">): TranscodeMode {
  if (!spec.copyVideo) return "encode";
  return spec.hevcCopy === true ? "hevc-copy" : "copy";
}

/**
 * A stable id for one title, at one offset, at one bitrate.
 *
 * The bitrate is part of the identity because it is part of what the session
 * *is*: a viewer whose link cannot carry the default needs a different
 * encode, and an id that ignored the cap would hand them the one already
 * failing.
 *
 * Hashed rather than composed from the set id: this reaches a URL and then a
 * path, and a set id comes from a caption, so it must not be able to carry a
 * separator or a `..` into either.
 */
export function sessionId(spec: SessionSpec): string {
  return createHash("sha256")
    .update(spec.setId)
    .update(new Uint8Array([0]))
    .update(String(spec.seekSeconds))
    .update(new Uint8Array([0]))
    .update(String(spec.maxrateBits))
    .update(new Uint8Array([0]))
    // Part of the identity, not a detail of it: two viewers watching the same
    // film in different languages want different encodes, and sharing one
    // would hand the second viewer the first one's audio.
    .update(String(spec.audioTrack))
    .update(new Uint8Array([0]))
    // And likewise a copy: the same title, offset and track, copied for one
    // viewer and encoded for a capped one, are two different streams. An id
    // that ignored this would hand the capped viewer the uncapped bytes their
    // cap exists to prevent. An HEVC copy is fMP4 rather than TS: a different
    // stream again — `modeOf` tells the three apart.
    .update(modeOf(spec))
    .digest("hex")
    .slice(0, 16);
}
