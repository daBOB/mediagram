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
 * `MEDIAGRAM_PROGRESS=lines` asks the command for `progress <step> <done>
 * <total>` lines as it goes, handed to `onProgress` as they arrive so a page
 * can draw the wait; a build that predates them ignores the variable.
 *
 * Nothing here can stop the player. A machine without `mediagram`, or without
 * a TMDB key, serves initials where posters would be, as it always did.
 */

import { failureMessage } from "../failure-message";

export type PosterFetch = { ok: true; summary: string } | { ok: false; reason: string };

/** How far the command has got: `lookup` (titles at TMDB) or `download` (image files), `done` of `total`. */
export interface PosterProgress {
  step: string;
  done: number;
  total: number;
}

export async function fetchPostersForIndex(
  command: string,
  indexPath: string,
  onProgress?: (progress: PosterProgress) => void,
): Promise<PosterFetch> {
  let proc: ReturnType<typeof Bun.spawn>;
  try {
    proc = Bun.spawn([command, "posters", "--index", indexPath], {
      stdout: "pipe",
      stderr: "pipe",
      env: { ...process.env, MEDIAGRAM_PROGRESS: "lines" },
    });
  } catch (error) {
    return { ok: false, reason: `\`${command}\` could not be started: ${failureMessage(error)}` };
  }
  const [code, out, err] = await Promise.all([
    proc.exited,
    withoutProgress(proc.stdout as ReadableStream<Uint8Array>, onProgress),
    new Response(proc.stderr as ReadableStream).text(),
  ]);
  // Its last line is the summary: "1130 image(s) in …: 20 fetched, 1110 already held".
  const last = (text: string) => text.trim().split("\n").at(-1) ?? "";
  if (code === 0) return { ok: true, summary: last(out) };
  return { ok: false, reason: `\`${command} posters\` exited ${code}: ${last(err) || last(out)}` };
}

const PROGRESS_LINE = /^progress (\w+) (\d+) (\d+)$/;

/** Stdout with its progress lines handed on as each arrives, and the rest kept for the summary. */
async function withoutProgress(
  stream: ReadableStream<Uint8Array>,
  onProgress?: (progress: PosterProgress) => void,
): Promise<string> {
  const kept: string[] = [];
  const decoder = new TextDecoder();
  let partial = "";
  const take = (line: string) => {
    const found = PROGRESS_LINE.exec(line);
    if (!found) {
      kept.push(line);
      return;
    }
    try {
      onProgress?.({ step: found[1]!, done: Number(found[2]), total: Number(found[3]) });
    } catch (error) {
      // A page that could not be told is no reason to lose the fetch.
      console.warn(`posters: progress not passed on: ${failureMessage(error)}`);
    }
  };
  for await (const chunk of stream) {
    const lines = (partial + decoder.decode(chunk, { stream: true })).split("\n");
    partial = lines.pop() ?? "";
    lines.forEach(take);
  }
  partial += decoder.decode();
  if (partial) take(partial);
  return kept.join("\n");
}
