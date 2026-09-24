/**
 * Cover art for the titles a channel snapshot brought in.
 *
 * A snapshot is only `library.db`: the uploader's machine has the artwork, this
 * one does not. Rather than grow a third TMDB client beside the uploader's and
 * the Android core's, the server runs the uploader's own
 * `mediagram posters --index <snapshot>`, which writes the art beside this
 * machine's index — where `PosterStore` already looks, file by file, per
 * request. So the art appears as soon as it is on disk; the caller only has to
 * tell open pages to read the catalog again.
 *
 * Nothing here can stop the player. A machine without `mediagram`, or without
 * a TMDB key, serves initials where posters would be, as it always did.
 */

import { failureMessage } from "../failure-message";

export type PosterFetch = { ok: true; summary: string } | { ok: false; reason: string };

export async function fetchPostersForIndex(command: string, indexPath: string): Promise<PosterFetch> {
  let proc: ReturnType<typeof Bun.spawn>;
  try {
    proc = Bun.spawn([command, "posters", "--index", indexPath], { stdout: "pipe", stderr: "pipe" });
  } catch (error) {
    return { ok: false, reason: `\`${command}\` could not be started: ${failureMessage(error)}` };
  }
  const [code, out, err] = await Promise.all([
    proc.exited,
    new Response(proc.stdout as ReadableStream).text(),
    new Response(proc.stderr as ReadableStream).text(),
  ]);
  // Its last line is the summary: "565 poster(s) in …: 20 fetched, 545 already held".
  const last = (text: string) => text.trim().split("\n").at(-1) ?? "";
  if (code === 0) return { ok: true, summary: last(out) };
  return { ok: false, reason: `\`${command} posters\` exited ${code}: ${last(err) || last(out)}` };
}
