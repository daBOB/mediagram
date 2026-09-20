/** Covers `player-keys`: which keystrokes the player takes, and which it leaves. */

import { describe, expect, test } from "bun:test";
import { keyAction, wantsKeys } from "../public/lib/player-keys.js";

describe("the keys the player takes", () => {
  test("space and k both play and pause", () => {
    expect(keyAction({ key: " " })).toEqual({ do: "playPause" });
    expect(keyAction({ key: "k" })).toEqual({ do: "playPause" });
  });

  test("the arrows skip the same ten the buttons do", () => {
    expect(keyAction({ key: "ArrowLeft" })).toEqual({ do: "skip", by: -10 });
    expect(keyAction({ key: "ArrowRight" })).toEqual({ do: "skip", by: 10 });
  });

  test("up and down are the volume", () => {
    expect(keyAction({ key: "ArrowUp" })).toEqual({ do: "volume", by: 0.1 });
    expect(keyAction({ key: "ArrowDown" })).toEqual({ do: "volume", by: -0.1 });
  });

  test("m, f and c", () => {
    expect(keyAction({ key: "m" })).toEqual({ do: "mute" });
    expect(keyAction({ key: "f" })).toEqual({ do: "fullscreen" });
    expect(keyAction({ key: "c" })).toEqual({ do: "subtitles" });
  });

  test("a digit is that tenth of the film", () => {
    expect(keyAction({ key: "0" })).toEqual({ do: "seekFraction", by: 0 });
    expect(keyAction({ key: "5" })).toEqual({ do: "seekFraction", by: 0.5 });
    expect(keyAction({ key: "9" })).toEqual({ do: "seekFraction", by: 0.9 });
  });
});

describe("the handling keys", () => {
  test("p and z", () => {
    expect(keyAction({ key: "p" })).toEqual({ do: "pictureInPicture" });
    expect(keyAction({ key: "z" })).toEqual({ do: "framing" });
  });

  test("comma and full stop step a frame either way", () => {
    // Nominal: the index knows no frame rate, so this is a 24th of a second.
    expect(keyAction({ key: "," })).toEqual({ do: "step", by: -1 / 24 });
    expect(keyAction({ key: "." })).toEqual({ do: "step", by: 1 / 24 });
  });

  test("brackets step the speed the picker already offers", () => {
    expect(keyAction({ key: "[" })).toEqual({ do: "speed", by: -1 });
    expect(keyAction({ key: "]" })).toEqual({ do: "speed", by: 1 });
  });

  test("a and b mark out a loop", () => {
    expect(keyAction({ key: "a" })).toEqual({ do: "loop", end: "from" });
    expect(keyAction({ key: "b" })).toEqual({ do: "loop", end: "to" });
  });

  test("and every one of them is guarded like the rest", () => {
    // Eight more keys is eight more chances to steal a keystroke. The guard is
    // one place, so this is a table rather than eight arguments.
    for (const key of ["p", "z", ",", ".", "[", "]", "a", "b"]) {
      expect(keyAction({ key, inControl: true })).toBeNull();
      expect(keyAction({ key, ctrlKey: true })).toBeNull();
      // A focused button keeps only space and Enter; everything else works.
      expect(keyAction({ key, onButton: true })).not.toBeNull();
    }
  });
});

describe("the keys it leaves alone", () => {
  test("Escape, which the dialog closes on", () => {
    expect(keyAction({ key: "Escape" })).toBeNull();
  });

  test("anything it has no answer for", () => {
    for (const key of ["q", "Tab", "F5", "PageDown", "", "ArrowLeftRight"]) {
      expect(keyAction({ key })).toBeNull();
    }
  });

  test("a key that is not a key", () => {
    expect(keyAction({})).toBeNull();
    expect(keyAction({ key: undefined as never })).toBeNull();
    expect(keyAction({ key: 5 as never })).toBeNull();
  });

  test("anything held with a modifier belongs to the browser", () => {
    // ctrl+F is find and meta+← is back; neither asks to skip ten seconds.
    expect(keyAction({ key: "f", ctrlKey: true })).toBeNull();
    expect(keyAction({ key: "ArrowLeft", metaKey: true })).toBeNull();
    expect(keyAction({ key: " ", altKey: true })).toBeNull();
  });
});

describe("a field that wants its own keys", () => {
  test("takes every one of them", () => {
    for (const key of [" ", "ArrowLeft", "c", "5", "m"]) {
      expect(keyAction({ key, inControl: true })).toBeNull();
    }
  });
});

describe("a focused button", () => {
  test("keeps space and Enter, which press it", () => {
    // Otherwise a viewer who tabbed to Watchlist and pressed space would add
    // the title to the watchlist and pause the film.
    expect(keyAction({ key: " ", onButton: true })).toBeNull();
    expect(keyAction({ key: "Enter", onButton: true })).toBeNull();
  });

  test("but gives back everything else", () => {
    expect(keyAction({ key: "ArrowRight", onButton: true })).toEqual({ do: "skip", by: 10 });
    expect(keyAction({ key: "m", onButton: true })).toEqual({ do: "mute" });
  });
});

describe("what counts as a field", () => {
  const el = (tagName: string, extra = {}) => ({ tagName, ...extra });

  test("the ones that take typing or a choice", () => {
    for (const tag of ["INPUT", "SELECT", "TEXTAREA", "OPTION"]) {
      expect(wantsKeys(el(tag))).toBe(true);
    }
    expect(wantsKeys(el("DIV", { isContentEditable: true }))).toBe(true);
  });

  test("and nothing else, including nothing at all", () => {
    expect(wantsKeys(el("BUTTON"))).toBe(false);
    expect(wantsKeys(el("VIDEO"))).toBe(false);
    expect(wantsKeys(null as never)).toBe(false);
    expect(wantsKeys({} as never)).toBe(false);
  });
});
