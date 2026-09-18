/**
 * Reading a title's audio streams off the file.
 *
 * The rule under test is the one the index cannot express: a stream's
 * position in the probed list is its `-map 0:a:N` ordinal, including the
 * streams a language-code list would have merged or dropped.
 */

import { describe, expect, test } from "bun:test";
import { AudioTrackReader, parseAudioTracks } from "../src/audio-tracks";

/** ffprobe's shape, trimmed to the fields that are read. */
const probeJson = (streams: unknown[]) => JSON.stringify({ streams });

const stream = (over: Record<string, unknown> = {}) => ({
  codec_name: "aac",
  channels: 2,
  tags: { language: "eng" },
  disposition: { default: 0 },
  ...over,
});

describe("ordinals", () => {
  test("a stream's position is the ordinal ffmpeg will be given", () => {
    const tracks = parseAudioTracks(
      probeJson([
        stream({ tags: {} }),
        stream({ tags: { language: "eng" } }),
        stream({ tags: { language: "eng", title: "Commentary" } }),
        stream({ tags: { language: "deu" }, codec_name: "ac3", channels: 6 }),
      ]),
    );

    expect(tracks.map((t) => t.index)).toEqual([0, 1, 2, 3]);
    // The case the index gets wrong: `alang` would be ["en","de"], putting
    // German at position 1 — which is the English track.
    expect(tracks[3]).toMatchObject({ index: 3, lang: "deu", codec: "ac3", channels: 6 });
    expect(tracks[2]!.title).toBe("Commentary");
  });

  test("an untagged stream keeps its place rather than being dropped", () => {
    const tracks = parseAudioTracks(probeJson([stream({ tags: {} }), stream()]));

    expect(tracks).toHaveLength(2);
    expect(tracks[0]!.lang).toBeNull();
    expect(tracks[1]!.index).toBe(1);
  });

  test("`und` is treated as no language, because that is what it means", () => {
    expect(parseAudioTracks(probeJson([stream({ tags: { language: "und" } })]))[0]!.lang).toBeNull();
  });

  test("the file's own default is carried through", () => {
    const tracks = parseAudioTracks(
      probeJson([stream(), stream({ disposition: { default: 1 } })]),
    );
    expect(tracks.map((t) => t.isDefault)).toEqual([false, true]);
  });
});

describe("refusing to guess", () => {
  test("junk is no tracks, not a throw", () => {
    expect(parseAudioTracks("not json")).toEqual([]);
    expect(parseAudioTracks("{}")).toEqual([]);
    expect(parseAudioTracks(JSON.stringify({ streams: "no" }))).toEqual([]);
  });

  test("a stream missing everything still yields a usable track", () => {
    expect(parseAudioTracks(probeJson([{}]))[0]).toEqual({
      index: 0,
      lang: null,
      codec: null,
      channels: null,
      title: null,
      isDefault: false,
    });
  });
});

describe("the reader", () => {
  test("probes once per set and answers from the cache after", async () => {
    let calls = 0;
    const reader = new AudioTrackReader("http://127.0.0.1:8770", async () => {
      calls += 1;
      return probeJson([stream()]);
    });

    expect(await reader.read("01SET")).toHaveLength(1);
    expect(await reader.read("01SET")).toHaveLength(1);
    expect(calls).toBe(1);
  });

  test("asks for the set's own stream route", async () => {
    let asked = "";
    const reader = new AudioTrackReader("http://127.0.0.1:8770", async (url) => {
      asked = url;
      return probeJson([]);
    });

    await reader.read("01SET/../etc");
    expect(asked).toBe("http://127.0.0.1:8770/api/sets/01SET%2F..%2Fetc/stream");
  });

  test("a probe that throws leaves a title with no chooser, not an error", async () => {
    const reader = new AudioTrackReader("http://127.0.0.1:8770", async () => {
      throw new Error("ffprobe is not installed");
    });

    expect(await reader.read("01SET")).toEqual([]);
  });

  test("a failure is cached too, so a slow open happens once", async () => {
    let calls = 0;
    const reader = new AudioTrackReader("http://127.0.0.1:8770", async () => {
      calls += 1;
      return null;
    });

    await reader.read("01SET");
    await reader.read("01SET");
    expect(calls).toBe(1);
  });
});
