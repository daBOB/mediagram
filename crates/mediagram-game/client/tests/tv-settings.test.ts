// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { DEFAULTS, effectsGain, getSettings, musicVolume, sanitize, subscribeSettings, updateSettings } from "../src/tv/settings/tv-settings";

test("defaults keep today's mix", () => {
  expect(musicVolume(DEFAULTS)).toBeCloseTo(0.35);
  expect(effectsGain(DEFAULTS)).toBe(1);
});

test("muted silences both, keeping the levels", () => {
  const s = { ...DEFAULTS, muted: true };
  expect(musicVolume(s)).toBe(0);
  expect(effectsGain(s)).toBe(0);
  expect(s.music).toBe(4);
});

test("music levels scale and stay within 0..1", () => {
  expect(musicVolume({ ...DEFAULTS, music: 10 })).toBeCloseTo(0.875);
  expect(musicVolume({ ...DEFAULTS, music: 0 })).toBe(0);
});

test("sanitize repairs bad input", () => {
  expect(sanitize(null)).toEqual(DEFAULTS);
  expect(sanitize({ music: 99, effects: -3, figures: "x", muted: "yes" })).toEqual({ ...DEFAULTS, music: 10, effects: 0 });
  expect(sanitize({ music: NaN, figures: "avatars" })).toEqual({ ...DEFAULTS, figures: "avatars" });
});

test("update notifies subscribers until they unsubscribe, and works without storage", () => {
  const seen: number[] = [];
  const off = subscribeSettings((s) => seen.push(s.music));
  updateSettings({ music: 7 });
  expect(getSettings().music).toBe(7);
  off();
  updateSettings({ music: 2 });
  expect(seen).toEqual([7]);
});
