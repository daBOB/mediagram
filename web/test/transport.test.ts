/** Covers `transport`: what the bar's controls say, before any of them move. */

import { describe, expect, test } from "bun:test";
import { isSilent, playLabel, speedLabel, subtitleOptions } from "../public/lib/transport.js";

describe("the speed on the menu", () => {
  test("drops the noise after the point", () => {
    expect(speedLabel(1)).toBe("1×");
    expect(speedLabel(1.5)).toBe("1.5×");
    expect(speedLabel(0.75)).toBe("0.75×");
  });

  test("a rate that is not one falls back to one", () => {
    // `playbackRate` is never absent on a real element, but a picker built
    // from a stale value should not print `NaN×` at a viewer.
    for (const rate of [undefined, null, 0, -1, Number.NaN, "fast"]) {
      expect(speedLabel(rate as never)).toBe("1×");
    }
  });
});

describe("what the play button offers", () => {
  test("the opposite of what is happening", () => {
    expect(playLabel(true)).toBe("Play");
    expect(playLabel(false)).toBe("Pause");
  });
});

describe("silence", () => {
  test("muted, or turned all the way down, is the same to a listener", () => {
    expect(isSilent({ volume: 1, muted: true })).toBe(true);
    expect(isSilent({ volume: 0, muted: false })).toBe(true);
  });

  test("and anything audible is not", () => {
    expect(isSilent({ volume: 1, muted: false })).toBe(false);
    expect(isSilent({ volume: 0.01, muted: false })).toBe(false);
  });
});

describe("the subtitle menu", () => {
  const track = (label: string, kind = "subtitles") => ({ label, kind });

  test("offers Off first, and then what there is", () => {
    expect(subtitleOptions([track("English"), track("Deutsch")])).toEqual([
      { value: "off", label: "Off" },
      { value: "0", label: "English" },
      { value: "1", label: "Deutsch" },
    ]);
  });

  test("counts only subtitles, so an index means the same to both sides", () => {
    // The element's list holds every kind of text track. Numbering the menu
    // from the unfiltered list would point "0" at a chapters track.
    expect(subtitleOptions([track("Chapters", "chapters"), track("English")])).toEqual([
      { value: "off", label: "Off" },
      { value: "0", label: "English" },
    ]);
  });

  test("a track with no label is still a track", () => {
    expect(subtitleOptions([track("")])[1]).toEqual({ value: "0", label: "Subtitles" });
  });

  test("nothing to offer is Off alone, which the bar reads as no menu", () => {
    for (const tracks of [undefined, null, []]) {
      expect(subtitleOptions(tracks as never)).toHaveLength(1);
    }
  });
});
