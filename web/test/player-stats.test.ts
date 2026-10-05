/**
 * The stats overlay: the rows the browser can fill, under Android's names,
 * with a row nothing knows left out rather than guessed at.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mountPlayerStats, statsRows } from "../public/lib/playback/player-stats.js";
import { catalogSet } from "./support/catalog-set";
import { browserEnvironment } from "./support/player-environment";

const fields = (over: Record<string, unknown> = {}) => ({
  readyState: 4, ahead: 52, starved: false, awaitingStart: false, fillRate: null,
  health: "ok", dropped: 0, frames: 1200, paused: false, held: false, ...over,
});
const film = catalogSet({ quality: "1080p", hdr: "HDR10", vcodec: "hevc", acodec: "eac3", total: 15_247_000_000, duration: 9_840 });
const noSize = { width: 0, height: 0 };
const named = (rows: { name: string; value: string }[]) => Object.fromEntries(rows.map((row) => [row.name, row.value]));

describe("the rows", () => {
  test("video, audio, buffer and cache, in Android's order and words, with no dropped row while none are", () => {
    const rows = statsRows({ fields: fields(), set: film, audio: null, size: { width: 1920, height: 800 } });
    expect(rows.map((row) => row.name)).toEqual(["video", "audio", "buffer", "cache"]);
    expect(named(rows)).toEqual({
      video: "1920×800 hevc HDR10 12 Mbps",
      audio: "eac3",
      buffer: "0:52 ahead",
      cache: "ready · 0:52 ahead",
    });
  });

  test("the probed track says codec, channels and language; the file's label stands in for an undecoded picture", () => {
    const audio = { index: 1, lang: "deu", codec: "ac3", channels: 6, title: null, isDefault: true };
    const rows = named(statsRows({ fields: fields(), set: film, audio, size: noSize }));
    expect(rows.audio).toBe("ac3 5.1 German");
    expect(rows.video).toBe("1080p hevc HDR10 12 Mbps");
  });

  test("a title held in full says cached, and dropped frames appear once there are some", () => {
    const rows = named(statsRows({ fields: fields({ held: true, dropped: 7 }), set: film, audio: null, size: noSize }));
    expect(rows.buffer).toBe("cached");
    expect(rows.dropped).toBe("7 frames");
    // The cache row no longer repeats the count the dropped row now owns.
    expect(rows.cache).not.toContain("dropped");
  });

  test("a row with nothing known is absent, never blank", () => {
    const bare = catalogSet({ quality: null, hdr: null, vcodec: null, acodec: null, total: 0, duration: null });
    const rows = statsRows({ fields: fields(), set: bare, audio: null, size: noSize });
    expect(rows.map((row) => row.name)).toEqual(["buffer", "cache"]);
    expect(statsRows({ fields: fields(), set: null, audio: null, size: noSize }).map((row) => row.name)).toEqual(["buffer", "cache"]);
  });
});

describe("the overlay", () => {
  let env: ReturnType<typeof browserEnvironment>;
  beforeEach(() => {
    env = browserEnvironment();
  });
  afterEach(() => env.restore());

  test("ⓘ toggles it, and nothing is drawn into it while it is shut", () => {
    const panel = env.node("stats-panel");
    panel.hidden = true;
    const stats = mountPlayerStats({ video: env.video });
    stats.draw(fields(), film, null);
    expect(panel.children).toHaveLength(0);
    env.node("stats-toggle").fire("click");
    expect(panel.hidden).toBe(false);
    expect(env.node("stats-toggle").getAttribute("aria-pressed")).toBe("true");
    expect(panel.children.map((node) => node.textContent)).toEqual([
      "video", "1080p hevc HDR10 12 Mbps", "audio", "eac3", "buffer", "0:52 ahead", "cache", "ready · 0:52 ahead",
    ]);
    env.node("stats-toggle").fire("click");
    expect(panel.hidden).toBe(true);
    expect(env.node("stats-toggle").getAttribute("aria-pressed")).toBe("false");
  });

  test("it sits below the top bar however tall that has grown, and follows when the bar changes", () => {
    const bar = env.node("top-bar");
    bar.clientHeight = 150;
    const panel = env.node("stats-panel");
    panel.hidden = true;
    const stats = mountPlayerStats({ video: env.video });
    env.node("stats-toggle").fire("click");
    expect(panel.style.top).toBe("150px");
    bar.clientHeight = 64;
    stats.draw(fields(), film, null);
    expect(panel.style.top).toBe("64px");
  });
});
