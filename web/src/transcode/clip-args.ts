/**
 * The ffmpeg command line for cutting one scene into a file made for posting.
 *
 * Pure and tested like `transcodeArgs`, for the same reason: a wrong flag
 * here yields a file that looks fine and posts badly. Unlike a conversion for
 * the player this one always encodes on the CPU, to a single SDR BT.709
 * 8-bit shape, so one filter chain serves every source.
 */

import { READ_BURST_SECONDS, READ_RATE } from "./args";

/** The catalog's `hdr` column; null means the title is SDR. */
export type ClipHdr = "SDR" | "HDR10" | "HLG" | "DV" | null;

export interface ClipRequest {
  /** The Range server's URL for this set. */
  input: string;
  output: string;
  /** Start and end of the scene, in seconds into the film. */
  from: number;
  to: number;
  /** ffmpeg's `0:a:N` ordinal of the track the viewer was listening to. */
  audioTrack: number;
  hdr: ClipHdr;
  /** Set once the `encoders.ts` probe finds ffmpeg accepts `-readrate`. */
  pacedReads: boolean;
}

/** Longest scene a viewer may cut; Instagram's own limit for a reel is longer, this keeps a job short. */
export const CLIP_MAX_SECONDS = 180;

/**
 * Fits inside 1080p keeping the shape and never enlarges. Even sides because
 * yuv420p cannot represent an odd one. The quotes keep the commas in `min()`
 * from being read as the next filter in the chain.
 */
const FIT_1080P =
  "scale=w='min(1920,iw)':h='min(1080,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2";

/**
 * HDR10 and HLG are folded to SDR in linear light: tone mapping gamma-encoded
 * values crushes the highlights. `npl=100` is SDR reference white. The scale
 * comes after, on the already-SDR picture.
 */
const TONE_MAP_HDR =
  "zscale=t=linear:npl=100,format=gbrpf32le,tonemap=tonemap=hable:desat=0," +
  "zscale=t=bt709:m=bt709:p=bt709:r=tv";

/**
 * Dolby Vision profile 5 carries no HDR10 base layer, so zscale would read
 * its reshaped colours as plain BT.2020 and come out green and purple;
 * libplacebo applies the dynamic metadata. It scales as well, so only the
 * final fit follows.
 */
const TONE_MAP_DV =
  "libplacebo=tonemapping=bt.2390:colorspace=bt709:color_primaries=bt709:color_trc=bt709:range=tv:format=yuv420p";

function colourChain(hdr: ClipHdr): string[] {
  if (hdr === "HDR10" || hdr === "HLG") return [TONE_MAP_HDR];
  if (hdr === "DV") return [TONE_MAP_DV];
  return [];
}

export function clipArgs(request: ClipRequest): string[] {
  const args: string[] = ["-hide_banner", "-loglevel", "error", "-y"];

  // Before -i: ffmpeg seeks the input instead of decoding up to the scene.
  // The encode below is not a copy, so the cut is still frame exact.
  args.push("-ss", String(request.from));

  // Also an input option; see `transcodeArgs` for why it is probed first.
  if (request.pacedReads) {
    args.push("-readrate", String(READ_RATE), "-readrate_initial_burst", String(READ_BURST_SECONDS));
  }

  args.push("-i", request.input, "-t", String(request.to - request.from));

  // Named explicitly for the reasons given in `transcodeArgs`: the default
  // "best" audio is often a commentary, and a subtitle track can stall.
  args.push("-map", "0:v:0", "-map", `0:a:${request.audioTrack}`, "-sn", "-dn");

  args.push(
    "-vf",
    [...colourChain(request.hdr), FIT_1080P, "format=yuv420p"].join(","),
    "-c:v",
    "libx264",
    "-crf",
    "18",
    "-preset",
    "medium",
    "-pix_fmt",
    "yuv420p",
    // Tagged so a player does not guess the colours of an untagged stream.
    "-colorspace",
    "bt709",
    "-color_primaries",
    "bt709",
    "-color_trc",
    "bt709",
    "-c:a",
    "aac",
    "-b:a",
    "160k",
    "-ac",
    "2",
    // The index first, so a phone can start the file before it has all of it.
    "-movflags",
    "+faststart",
    request.output,
  );
  return args;
}
