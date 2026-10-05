/**
 * `mountSubtitlePicker` against a fake DOM: the CC toggle and the menu behind
 * its ▾, forced tracks showing while regular ones are off, 'c' remembering per
 * show, the profile preference, and the style panel's wider reach.
 *
 * `subtitle-choice.js`'s own rule is proved against the shared fixture in
 * `subtitle-choice.test.ts`; this file only proves the DOM around it.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mountPlayerMenus } from "../public/lib/playback/player-menus.js";
import { mountSubtitlePicker } from "../public/lib/playback/subtitle-picker.js";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, chooseInMenu, markedInMenu, settle, TrackElement } from "./support/player-environment";

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

type Track = { track: number; lang: string; forced: boolean; sdh: boolean; label: string };

/** Attaches one `<track>` per entry, in order — as `player.js`'s `attachSubtitles` does. */
function attach(tracks: Track[]) {
  for (const existing of env.video.querySelectorAll("track")) existing.remove();
  for (const item of tracks) {
    const el = new TrackElement();
    el.srclang = item.lang;
    el.label = item.label;
    env.video.append(el);
  }
}

function mount() {
  env.node("cue-panel").hidden = true;
  return mountSubtitlePicker({
    video: env.video, cc: env.node("cc"), more: env.node("cc-menu"), menus: mountPlayerMenus(), stylePanel: env.node("cue-panel"),
    recall: (name) => state.preferenceOf(SCOPE, name),
    remember: (name, value) => state.setPreference(SCOPE, name, value),
  });
}

const showing = () => [...env.video.textTracks].filter((t) => t.mode === "showing").map((t) => t.language);
const rows = () => env.node("card-menu").children;
const pick = (value: string) => chooseInMenu(env.node, "cc-menu", value);

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
    expect(env.node("cc").disabled).toBe(false); // one regular track: CC can act
    expect(env.node("cc").getAttribute("aria-pressed")).toBe("false");
    expect(env.node("cc-menu").disabled).toBe(false);

    // CC switches the regular track on; the forced one yields to it rather
    // than doubling up, per the rule ("no regular showing" is its condition).
    env.node("cc").fire("click");
    expect([...env.video.textTracks].map((t) => t.mode)).toEqual(["disabled", "showing"]);
    expect(env.node("cc").getAttribute("aria-pressed")).toBe("true");
  });

  test("a forced-only title disables CC and offers only Style… behind the ▾", () => {
    const tracks = [{ track: 0, lang: "de", forced: true, sdh: false, label: "Forced (SRT)" }];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);
    picker.setAudio("de");

    expect(showing()).toEqual(["de"]);
    expect(env.node("cc").disabled).toBe(true);
    expect(env.node("cc-menu").disabled).toBe(false);
    env.node("cc-menu").fire("click");
    expect(rows().map((row) => row.textContent)).toEqual(["Style…"]);
  });

  test("never shows when the audio language is unknown", () => {
    const tracks = [{ track: 0, lang: "de", forced: true, sdh: false, label: "German (Forced)" }];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    expect(showing()).toEqual([]);
  });
});

describe("CC", () => {
  test("a title with no subtitles at all has CC and its ▾ disabled, and pressing CC does nothing", async () => {
    await useProfile();
    attach([]);
    const picker = mount();
    picker.offer([], null);
    expect(env.node("cc").disabled).toBe(true);
    expect(env.node("cc-menu").disabled).toBe(true);
    env.node("cc").fire("click");
    picker.toggle();
    expect(showing()).toEqual([]);
    expect(state.preferenceOf(SCOPE, "subtitle")).toBeNull();
  });

  test("on, with nothing remembered, picks what the player picks anywhere else: the audio language", async () => {
    await useProfile();
    const tracks = [
      { track: 0, lang: "en", forced: false, sdh: false, label: "English" },
      { track: 1, lang: "de", forced: false, sdh: false, label: "German" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);
    picker.setAudio("de");
    expect(showing()).toEqual([]);

    env.node("cc").fire("click");
    expect(showing()).toEqual(["de"]);
    expect(env.node("cc").getAttribute("aria-pressed")).toBe("true");
  });

  test("off and on again brings back the language that was showing, even one nobody picked this title", async () => {
    await useProfile();
    state.setPreference(SCOPE, "subtitle", "en");
    const tracks = [
      { track: 0, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 1, lang: "en", forced: false, sdh: false, label: "English" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);
    expect(showing()).toEqual(["en"]);

    env.node("cc").fire("click");
    expect(showing()).toEqual([]);
    expect(env.node("cc").getAttribute("aria-pressed")).toBe("false");
    env.node("cc").fire("click");
    expect(showing()).toEqual(["en"]);
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

  test("a pick from the menu is remembered as last, and 'c' restores exactly that track", async () => {
    await useProfile();
    const tracks = [
      { track: 0, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 1, lang: "en", forced: false, sdh: true, label: "English (SDH)" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    pick("en:sdh");
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
    expect(markedInMenu(env.node, "cc-menu")).toBe("en:sdh");
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

describe("the ▾ menu", () => {
  test("the languages by label, then Off, then Style…, with what is showing marked", () => {
    const tracks = [
      { track: 0, lang: "de", forced: true, sdh: false, label: "German (Forced)" },
      { track: 1, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 2, lang: "en", forced: false, sdh: true, label: "English (SDH)" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    env.node("cc-menu").fire("click");
    expect(rows().map((row) => [row.dataset.value, row.textContent, row.getAttribute("aria-pressed")])).toEqual([
      ["de", "German", "false"], ["en:sdh", "English (SDH)", "false"], ["off", "Off", "true"], ["style", "Style…", "false"],
    ]);
  });

  test("Style… opens the style panel in the menu's place, and the ▾ closes it again", () => {
    const tracks = [{ track: 0, lang: "de", forced: false, sdh: false, label: "German" }];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    pick("style");
    expect(env.node("cue-panel").hidden).toBe(false);
    expect(env.node("card-menu").hidden).toBe(true);
    expect(env.node("cc-menu").getAttribute("aria-expanded")).toBe("true");
    env.node("cc-menu").fire("click");
    expect(env.node("cue-panel").hidden).toBe(true);
  });
});
