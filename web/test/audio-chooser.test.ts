/**
 * Naming audio tracks in a menu.
 *
 * The rule worth testing is that two rows are never the same: a film
 * routinely carries one language twice, once as 5.1 and once as a stereo
 * downmix, and a menu reading "German / German" is not a menu.
 */

import { describe, expect, test } from "bun:test";
import { channelLabel, defaultTrack, trackForLanguage, trackLabel } from "../public/lib/audio-chooser.js";

const track = (over: Record<string, unknown> = {}) => ({
  index: 0,
  lang: "eng",
  codec: "aac",
  channels: 2,
  title: null,
  isDefault: false,
  ...over,
});

describe("channels", () => {
  test("the layouts everyone knows are named", () => {
    expect(channelLabel(1)).toBe("mono");
    expect(channelLabel(2)).toBe("stereo");
    expect(channelLabel(6)).toBe("5.1");
    expect(channelLabel(8)).toBe("7.1");
  });

  test("an unusual count is stated rather than guessed at", () => {
    expect(channelLabel(3)).toBe("3ch");
    expect(channelLabel(12)).toBe("12ch");
  });

  test("a count nobody supplied says nothing", () => {
    expect(channelLabel(0)).toBe("");
    expect(channelLabel(null as unknown as number)).toBe("");
    expect(channelLabel(Number.NaN)).toBe("");
  });
});

describe("rows", () => {
  test("a language, how many channels, and what it is", () => {
    expect(trackLabel(track({ lang: "deu", channels: 6, codec: "ac3" }))).toBe(
      "German · 5.1 · ac3",
    );
  });

  test("the same language twice is still two distinguishable rows", () => {
    const surround = trackLabel(track({ lang: "deu", channels: 6, codec: "ac3" }));
    const stereo = trackLabel(track({ index: 1, lang: "deu", channels: 2, codec: "aac" }));
    expect(surround).not.toBe(stereo);
  });

  test("a stream that named itself gets to keep the name", () => {
    expect(trackLabel(track({ lang: "eng", title: "Commentary" }))).toBe(
      "English · Commentary · stereo · aac",
    );
  });

  test("an untagged stream is called by its number, not by `und`", () => {
    expect(trackLabel(track({ index: 2, lang: null }))).toBe("Track 3 · stereo · aac");
    expect(trackLabel(track({ index: 0, lang: "und" }))).toBe("Track 1 · stereo · aac");
  });

  test("a stream that says nothing at all still gets a row", () => {
    expect(trackLabel(track({ lang: null, codec: null, channels: null }))).toBe("Track 1");
  });
});

describe("where to start", () => {
  test("the stream the file marks as its default", () => {
    expect(defaultTrack([track(), track({ index: 1, isDefault: true })])).toBe(1);
  });

  test("the first one when the file marks none", () => {
    expect(defaultTrack([track(), track({ index: 1 })])).toBe(0);
    expect(defaultTrack([])).toBe(0);
  });
});

describe("matching a remembered language", () => {
  const tracks = [
    { index: 0, lang: "de", channels: 6, codec: "ac3", isDefault: true },
    { index: 1, lang: "en", channels: 2, codec: "aac", isDefault: false },
  ];

  test("finds the track carrying it", () => {
    expect(trackForLanguage(tracks, "en")).toBe(1);
    expect(trackForLanguage(tracks, "de")).toBe(0);
  });

  test("ignoring case and stray whitespace", () => {
    expect(trackForLanguage(tracks, " EN ")).toBe(1);
  });

  test("a language this file does not carry is nothing, not the first track", () => {
    // The caller then falls back to the file's own default, which is the right
    // answer for a re-upload that dropped a language somebody once chose.
    expect(trackForLanguage(tracks, "fr")).toBeNull();
  });

  test("and nothing remembered is nothing", () => {
    for (const lang of [null, undefined, "", "  ", 5]) {
      expect(trackForLanguage(tracks, lang as never)).toBeNull();
    }
  });

  test("an ordinal is never what is matched", () => {
    // A stored `1` would be German in one release and a commentary in the next.
    expect(trackForLanguage(tracks, "1")).toBeNull();
  });
});

