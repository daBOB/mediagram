/** Playback negotiation and HLS responses. The main router dispatches paths. */
import type { Database } from "bun:sqlite";
import { playableSet } from "../catalog";
import { isLocalAddress } from "../client-reach";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { bodiless as empty, withBody } from "../response";
import { copyVideoAs } from "./video-copy";
import type { SessionSpec } from "./registry";

/** A session's answer: the file, or the reason there is none. */
export type HlsFile =
  | { body: Uint8Array; type: string }
  /** Running, but has not written this yet. Worth asking again. */
  | "not-ready"
  /** No such session: stopped, reaped, or never started. */
  | "gone";

/** Serves HLS playlists and segments for a transcode in progress. */
export interface HlsServer {
  /**
   * Starts (or joins) a transcode and returns its playlist URL.
   *
   * Joining rather than always starting is what stops two viewers of the same
   * title running two encoders — but only when they want the same encode, so
   * everything in the spec is part of what identifies one.
   */
  begin(spec: SessionSpec): Promise<string>;

  /**
   * The file for a session, or why there isn't one.
   *
   * Not-ready is a real answer rather than an error: the playlist does not
   * exist until ffmpeg has written a first segment, and a player handed an
   * empty playlist treats it as a failure instead of waiting. It is kept
   * apart from `gone` because a player retries one and gives up on the other,
   * and only the session itself knows which it is.
   */
  file(sessionId: string, name: string): Promise<HlsFile>;

  /**
   * Releases one acquired viewer share and stops the session after its last
   * release. Each acquisition must be released exactly once. Releasing an
   * absent session is harmless: it may already have been reaped.
   */
  end(sessionId: string): Promise<void>;
}

/**
 * The lowest a conversion may be asked to aim for.
 *
 * Below this the picture is no longer worth the encoder it costs; a viewer on
 * a link this slow is better told so than handed a smear.
 */
const MIN_BITRATE = 600_000;

/**
 * The bitrate to encode at, given what a player asked for.
 *
 * Clamped rather than trusted: the number comes from a browser that measured
 * its own link, and a browser is free to say anything. Above the configured
 * cap it would saturate the uplink that cap exists to protect; absent or
 * nonsensical, the cap is the answer.
 */
function requestedBitrate(asked: string | null | undefined, cap: number): number {
  const wanted = Number(asked);
  if (!Number.isFinite(wanted) || wanted <= 0) return cap;
  return Math.min(cap, Math.max(MIN_BITRATE, Math.floor(wanted)));
}

/**
 * The codecs a transcode request says its browser decodes.
 *
 * Passed as given: `decidePlayback` keeps only `NEGOTIABLE` names, so a
 * request cannot talk the server into copying a codec the policy knows
 * nothing about — the worst a lying client can do is receive a stream its own
 * browser then refuses.
 */
function requestedDecodes(asked: string | null | undefined): string[] {
  return asked ? asked.split(",").map((name) => name.trim()) : [];
}

/**
 * Which audio stream a transcode request asked for, as `0:a:N`.
 *
 * Floored and clamped at zero for the same reason the seek position is: the
 * number comes from a browser and reaches an ffmpeg command line. A negative
 * or fractional one would become `-map 0:a:-1`, which ffmpeg exits on while
 * the request waits out the whole readiness timeout. A track past the end of
 * the file is left to ffmpeg, which fails cleanly and says so.
 */
function requestedAudioTrack(asked: string | null | undefined): number {
  const wanted = Number(asked);
  if (!Number.isFinite(wanted) || wanted <= 0) return 0;
  return Math.floor(wanted);
}

export async function beginTranscode(
  db: Database, hls: HlsServer | undefined, request: PlayerRequest, setId: string, maxBitrate: number,
): Promise<PlayerResponse> {
  if (!hls) return empty(501);
  // Looked up once and kept: this is both the check that the title exists
  // and the profile the copy decision below is made from.
  const profile = playableSet(db, setId);
  if (profile === null) return empty(404);
  // `Number.isFinite`, not a NaN check: `Infinity` survives one of those
  // and reaches the command line as `-ss Infinity`, which ffmpeg exits on
  // at once while the request waits out the whole readiness timeout.
  const asked = Number(request.seek ?? 0);
  const seek = Number.isFinite(asked) ? Math.max(0, Math.floor(asked)) : 0;
  const rate = requestedBitrate(request.maxrate, maxBitrate);
  const track = requestedAudioTrack(request.audio);

  /**
   * Whether this conversion has to touch the picture at all.
   *
   * Decided here rather than taken from the page: the page would only be
   * repeating what the index already says, and a client that got it wrong
   * — or was made to say so — would be asking for a stream the browser
   * then refuses. The index is the same source `decidePlayback` uses, so
   * both ends still answer from one policy.
   */
  const copied = copyVideoAs(profile, {
    remote: !isLocalAddress(request.client ?? ""),
    maxBitrate,
    capAsked: request.maxrate != null && request.maxrate !== "",
    decodes: requestedDecodes(request.vcodecs),
  });
  const copyVideo = copied !== false;
  // A negotiated copy is HEVC — the only codec `NEGOTIABLE` names — and
  // needs its own segment format, which is all this decides.
  const hevcCopy = copied === "negotiated";

  // A conversion that produces nothing is a 503 carrying the reason
  // rather than a 500: it is a title that could not be started now, and
  // the page has somewhere to show why.
  let playlist: string;
  try {
    playlist = await hls.begin({
      setId,
      seekSeconds: seek,
      maxrateBits: rate,
      audioTrack: track,
      copyVideo,
      hevcCopy,
    });
  } catch (error) {
    const reason = error instanceof Error ? error.message : "the conversion did not start";
    return withBody(JSON.stringify({ error: reason }), "application/json", { status: 503, headOnly: request.method === "HEAD" });
  }
  // `copied` so the page can say what is actually happening. "Converting
  // as you watch" is a promise about the picture, and when the picture is
  // being carried across untouched it is the wrong promise.
  return withBody(JSON.stringify({ playlist, copied: copyVideo }), "application/json", {
    headOnly: request.method === "HEAD",
  });
}

export async function hlsResponse(
  hls: HlsServer,
  sessionId: string,
  name: string,
  method: string,
): Promise<PlayerResponse> {
  const found = await hls.file(sessionId, name);
  // A player retries a 503 and gives up on a 404, so the two have to mean
  // what they say: still starting is worth waiting for, stopped or reaped is
  // not, and a player told to wait for a session that is never coming back
  // waits instead of falling back to direct play.
  if (found === "gone") return empty(404);
  if (found === "not-ready") return empty(503);

  return withBody(found.body, found.type, {
    headOnly: method === "HEAD", headers: { "cache-control": "no-store" },
  });
}
