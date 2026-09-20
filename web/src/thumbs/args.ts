/**
 * The ffmpeg command line for one sprite sheet.
 *
 * Pure and tested for the same reason `transcode/args.ts` is: a mistake here
 * produces a picture rather than an error, and a sheet of the wrong frames
 * looks exactly like a sheet of the right ones until someone hovers the bar.
 */

import type { SpritePlan } from "./sheets";

export interface SheetRequest {
  /** The player's own Range route for this set. */
  input: string;
  /** Where the sheet goes. */
  output: string;
  plan: SpritePlan;
}

export function sheetArgs(request: SheetRequest): string[] {
  const { plan } = request;
  const tile = `${plan.tileWidth}:${plan.tileHeight}`;

  return [
    "-hide_banner",
    "-loglevel",
    "error",
    "-y",
    /**
     * Keyframes only, which is the whole reason this is affordable.
     *
     * A full decode of a fifty-nine minute film to take a hundred and eighteen
     * pictures is almost all wasted work: every frame between the ones wanted
     * is decoded and thrown away. Keyframes are self-contained, so this reads
     * orders of magnitude less.
     *
     * The cost is that a frame is the nearest keyframe rather than the exact
     * second, which for a preview of what a scene looks like is not a cost.
     */
    "-skip_frame",
    "nokey",
    "-i",
    request.input,
    "-vf",
    [
      // One frame per interval, from whatever keyframes came through.
      `fps=1/${plan.interval}`,
      // `decrease` then pad, so a 2.39:1 film is letterboxed into the tile
      // rather than squashed to its shape. A stretched preview is worse than
      // a small one: it does not look like the film.
      `scale=${tile}:force_original_aspect_ratio=decrease`,
      `pad=${tile}:(ow-iw)/2:(oh-ih)/2`,
      `tile=${plan.columns}x${plan.rows}`,
    ].join(","),
    // One image out, however many went in.
    "-frames:v",
    "1",
    // Visibly lossy up close and indistinguishable at 160 pixels wide, which
    // is the only size anybody sees this at.
    "-q:v",
    "6",
    request.output,
  ];
}
