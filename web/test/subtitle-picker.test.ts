/**
 * `mountSubtitlePicker` against a fake DOM: the picker rows, forced tracks
 * showing while regular ones are off, 'c' remembering per show, the profile
 * preference, and the style trigger's wider visibility.
 *
 * `subtitle-choice.js`'s own rule is proved against the shared fixture in
 * `subtitle-choice.test.ts`; this file only proves the DOM around it.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mountSubtitlePicker } from "../public/lib/playback/subtitle-picker.js";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, settle, TrackElement } from "./support/player-environment";

const SCOPE = "show:Series";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(async () => {
  env = browserEnvironment();
  await state.useProfile(null);
});
afterEach(async () => {
  await settle();
  env.restore();
});

/** A profile is required for `setPreference` to persist anything at all. */
async function useProfile() {
  env.respondWith(async () => Response.json({}));
  await state.useProfile("viewer");
}

/** Attaches one `<track>` per entry, in order — as `player.js`'s `attachSubtitles` does. */
function attach(tracks: { track: number; lang: string; forced: boolean; sdh: boolean; label: string }[]) {
  for (const existing of env.video.querySelectorAll("track")) existing.remove();
  for (const item of tracks) {
    const el = new TrackElement();
    el.srclang = item.lang;
    el.label = item.label;
    env.video.append(el);
  }
}

function mount() {
  return mountSubtitlePicker({
    video: env.video, subs: env.node("subs"), picker: env.node("sub-track"), styleTrigger: env.node("cue-settings"),
    recall: (name) => state.preferenceOf(SCOPE, name),
    remember: (name, value) => state.setPreference(SCOPE, name, value),
  });
}

const showing = () => [...env.video.textTracks].filter((t) => t.mode === "showing").map((t) => t.language);

describe("forced tracks", () => {
  test("shows automatically for the audio language while off, and yields once a regular track is switched on", async () => {
    await useProfile();
    const tracks = [
      { track: 0, lang: "de", forced: true, sdh: false, label: "German (Forced)" },
      { track: 1, lang: "de", forced: false, sdh: false, label: "German" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);
    picker.setAudio("de");

    expect([...env.video.textTracks].map((t) => t.mode)).toEqual(["showing", "disabled"]); // forced, then regular
    expect(env.node("subs").hidden).toBe(false); // one regular track: CC is offered
    expect(env.node("cue-settings").hidden).toBe(false);

    // 'c' switches the regular track on; the forced one yields to it rather
    // than doubling up, per the rule ("no regular showing" is its condition).
    picker.toggle();
    expect([...env.video.textTracks].map((t) => t.mode)).toEqual(["disabled", "showing"]);
  });

  test("a forced-only title offers no picker and no CC, but the style trigger stays", () => {
    const tracks = [{ track: 0, lang: "de", forced: true, sdh: false, label: "Forced (SRT)" }];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);
    picker.setAudio("de");

    expect(showing()).toEqual(["de"]);
    expect(env.node("subs").hidden).toBe(true);
    expect(env.node("cue-settings").hidden).toBe(false);
    expect(env.node("sub-track").children).toHaveLength(0);
  });

  test("never shows when the audio language is unknown", () => {
    const tracks = [{ track: 0, lang: "de", forced: true, sdh: false, label: "German (Forced)" }];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    expect(showing()).toEqual([]);
  });
});

describe("'c' remembers per show", () => {
  test("turning subtitles on and off is remembered under the show's own scope", async () => {
    await useProfile();
    const tracks = [
      { track: 0, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 1, lang: "en", forced: false, sdh: true, label: "English (SDH)" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    expect(showing()).toEqual([]);
    picker.toggle();
    expect(showing()).toEqual(["de"]); // first regular track, nothing else set
    expect(state.preferenceOf(SCOPE, "subtitle")).toBe("de");
    picker.toggle();
    expect(showing()).toEqual([]);
    expect(state.preferenceOf(SCOPE, "subtitle")).toBe("off");
  });

  test("a manual pick is remembered as last, and 'c' restores exactly that track", async () => {
    await useProfile();
    const tracks = [
      { track: 0, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 1, lang: "en", forced: false, sdh: true, label: "English (SDH)" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    env.node("sub-track").value = "en:sdh";
    env.node("sub-track").fire("change");
    expect(showing()).toEqual(["en"]);
    picker.toggle();
    expect(showing()).toEqual([]);
    picker.toggle();
    expect(showing()).toEqual(["en"]);
  });
});

describe("profile preference", () => {
  test("resolves to the SDH track when only an SDH track exists for that language", async () => {
    await useProfile();
    state.setPreference("profile", "subtitle", "en");
    const tracks = [
      { track: 0, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 1, lang: "en", forced: false, sdh: true, label: "English (SDH)" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    expect(showing()).toEqual(["en"]);
    expect(env.node("sub-track").value).toBe("en:sdh");
  });

  test("skipped for the fallback when it is itself Off", async () => {
    await useProfile();
    state.setPreference("profile", "subtitle", "off");
    const tracks = [{ track: 0, lang: "de", forced: false, sdh: false, label: "German" }];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    expect(showing()).toEqual([]);
  });
});

describe("picker rows", () => {
  test("Off first, then the regular tracks by label", () => {
    const tracks = [
      { track: 0, lang: "de", forced: true, sdh: false, label: "German (Forced)" },
      { track: 1, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 2, lang: "en", forced: false, sdh: true, label: "English (SDH)" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    expect(env.node("sub-track").children.map((c) => [c.value, c.textContent])).toEqual([
      ["off", "Off"], ["de", "German"], ["en:sdh", "English (SDH)"],
    ]);
  });
});
