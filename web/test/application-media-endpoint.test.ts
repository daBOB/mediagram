import { afterEach, beforeEach, expect, spyOn, test } from "bun:test";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { startPlayer } from "../src/index";
import { ChunkCache } from "../src/cache/store";
import { configIn, deferred, library, telegramBoundary } from "./application-fixture";

let root: string;
beforeEach(async () => { root = await mkdtemp(join(tmpdir(), "application-media-endpoint-")); });
afterEach(async () => { await rm(root, { recursive: true, force: true }); });

const MEDIA = "0123456789";
const JPEG = new Uint8Array([255, 216, 255, 217]);

// Stand in only for the external executable: every owned child really fetches
// the URL the production runner supplied, through the application's Range route.
const PROCESS = `
  const [kind, input, output] = process.argv.slice(1);
  const response = await fetch(input, {
    headers: { range: "bytes=2-5" }, signal: AbortSignal.timeout(2000),
  });
  if (response.status !== 206 || response.headers.get("content-range") !== "bytes 2-5/10"
      || await response.text() !== "2345") throw new Error("media Range read failed");
  if (kind === "audio") {
    console.log(JSON.stringify({ streams: [{ codec_name: "aac", channels: 2, tags: { language: "eng" } }] }));
  } else if (kind === "sheet") {
    await Bun.write(output, new Uint8Array([255, 216, 255, 217]));
  } else {
    await Bun.write(output.replace("index.m3u8", "first.ts"), "segment");
    await Bun.write(output, "#EXTM3U\\n#EXTINF:2,\\nfirst.ts\\n");
  }
`;

test.each(["127.0.0.2", "0.0.0.0", "::1", "::"])(
  "startup on %s:0 supplies the live Range endpoint to audio, conversion, and thumbnails",
  async (hostname) => {
    const config = { ...configIn(root), hostname, cacheMaxBytes: 1_000_000 };
    const db = library("Held movie");
    try {
      db.run("UPDATE sets SET duration = 120");
      db.run("UPDATE parts SET chat_id = 1");
      await writeFile(config.libraryDb!, db.serialize());
    } finally { db.close(); }
    await new ChunkCache(config.cacheDir, config.cacheMaxBytes)
      .put("01SET", 0, 0, new TextEncoder().encode(MEDIA));

    const nativeSpawn = Bun.spawn.bind(Bun);
    const children: ReturnType<typeof Bun.spawn>[] = [];
    const inputs: Array<{ kind: string; url: string }> = [];
    const sheetExited = deferred<void>();
    const spawn = spyOn(Bun, "spawn").mockImplementation(((command: string[]) => {
      const kind = command[0] === "ffprobe" ? "audio"
        : command.at(-1)!.endsWith(".jpg") ? "sheet" : "transcode";
      expect(["ffprobe", "ffmpeg"]).toContain(command[0]!);
      const input = kind === "audio" ? command.at(-1)! : command[command.indexOf("-i") + 1]!;
      inputs.push({ kind, url: input });
      const child = nativeSpawn([process.execPath, "-e", PROCESS, kind, input, command.at(-1)!], {
        stdout: "pipe", stderr: "pipe",
      });
      children.push(child);
      if (kind === "sheet") void child.exited.then(() => sheetExited.resolve());
      return child;
    }) as typeof Bun.spawn);
    const order: string[] = [];
    let player: Awaited<ReturnType<typeof startPlayer>> | undefined;
    try {
      player = await startPlayer(config, {
        open: async () => telegramBoundary(order), findIndex: async () => "nothing-pinned",
        detectEncoder: async () => ({ kind: "software", name: "libx264" }),
        listen: () => () => {},
      });
      expect(inputs).toEqual([]);
      const baseUrl = player.server.baseUrl;
      const setUrl = `${baseUrl}/api/sets/01SET`;
      // The very first requests do not wait for background catalog readiness.
      const [audio, transcode, firstSheet] = await Promise.all([
        fetch(`${setUrl}/audio`), fetch(`${setUrl}/transcode`), fetch(`${setUrl}/thumbs.jpg`),
      ]);
      await sheetExited.promise;
      expect(inputs.toSorted((a, b) => a.kind.localeCompare(b.kind))).toEqual([
        { kind: "audio", url: `${setUrl}/stream` },
        { kind: "sheet", url: `${setUrl}/cached-stream` },
        { kind: "transcode", url: `${setUrl}/stream` },
      ]);
      expect(await Promise.all(children.map((child) => child.exited))).toEqual([0, 0, 0]);
      expect(audio.status).toBe(200);
      expect(await audio.json()).toEqual({ tracks: [{
        index: 0, lang: "eng", codec: "aac", channels: 2, title: null, isDefault: false,
      }] });
      expect(transcode.status).toBe(200);
      const converted = await transcode.json();
      if (!converted || typeof converted !== "object" || !("playlist" in converted)
        || typeof converted.playlist !== "string") throw new Error("missing transcode playlist");
      const playlist = await fetch(`${baseUrl}${converted.playlist}`);
      expect(playlist.status).toBe(200);
      expect(await playlist.text()).toContain("first.ts");
      expect(firstSheet.status).toBe(404);
      await firstSheet.arrayBuffer();
      // Process exit precedes the atomic rename; request again until publication.
      const deadline = Date.now() + 2000;
      let sheet: Response;
      for (;;) {
        sheet = await fetch(`${setUrl}/thumbs.jpg`);
        if (sheet.status !== 404 || Date.now() >= deadline) break;
        await sheet.arrayBuffer();
        await Bun.sleep(5);
      }
      expect(sheet.status).toBe(200);
      expect(new Uint8Array(await sheet.arrayBuffer())).toEqual(JPEG);
      expect(inputs).toHaveLength(3);
      await player.ready;
      await player.stop();
      expect(order).toEqual(["disconnect"]);
      await expect(fetch(`${baseUrl}/api/sets`)).rejects.toThrow();
    } finally {
      for (const child of children) child.kill();
      await Promise.all(children.map((child) => child.exited));
      await player?.stop();
      spawn.mockRestore();
    }
  }, 10_000,
);
