/**
 * The subtitle half of the control card: the CC toggle, the menu behind its
 * ▾ (the languages, Off, then Style…), and what 'c' does. `subtitle-choice.js`
 * is the rule; this is only the DOM around it.
 *
 * `video.textTracks[i]` is always the API's `tracks[i]`: `player.js` attaches
 * one `<track>` per catalog entry, in the catalog's own order, and never
 * reorders them.
 */

import { chooseSubtitles, toggleOn, trackKey, visibility } from "./subtitle-choice.js";
import { preferenceOf } from "../watch-state.js";

/** @typedef {import("./subtitle-choice.js").SubtitleTrack} SubtitleTrack */

/** The menu row that opens the style panel. No track key can be this word. */
const STYLE = "style";

/** `set.alang`, a JSON array string the way the index stores it, or already an array. */
function parsedAlang(value) {
  if (Array.isArray(value)) return value;
  try {
    const parsed = JSON.parse(value ?? "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/**
 * @param {{video: HTMLVideoElement, cc: HTMLButtonElement, more: HTMLButtonElement,
 *   menus: ReturnType<typeof import("./player-menus.js").mountPlayerMenus>,
 *   stylePanel: HTMLElement, recall: (name: string) => string|null,
 *   remember: (name: string, value: string) => void}} deps
 */
export function mountSubtitlePicker({ video, cc, more, menus, stylePanel, recall, remember }) {
  /** @type {SubtitleTrack[]} */
  let tracks = [];
  let alang = [];
  let audioTag = null;
  /** The last regular track chosen or shown this title, cleared on every title open. */
  let last = null;
  /** The regular track showing now, or `null` for none. */
  let showing = null;

  function preferred() {
    return preferenceOf("profile", "subtitle");
  }

  function applyModes(regularKey, forcedKey) {
    for (const [index, track] of [...video.textTracks].entries()) {
      const meta = tracks[index];
      if (!meta) continue;
      const key = trackKey(meta);
      track.mode = (meta.forced ? key === forcedKey : key === regularKey) ? "showing" : "disabled";
    }
  }

  function labelFor(key) {
    return tracks.find((track) => !track.forced && trackKey(track) === key)?.label ?? key;
  }

  function recompute() {
    const chosen = chooseSubtitles({ tracks, remembered: recall("subtitle"), preferred: preferred(), audioTag, alang });
    applyModes(chosen.regular, chosen.forced);
    showing = chosen.regular;

    const vis = visibility(tracks);
    // Disabled rather than hidden: a CC that is missing reads as a player
    // without subtitles, one that is greyed out as a title without them.
    cc.disabled = !vis.ccVisible;
    cc.setAttribute("aria-pressed", String(showing !== null));
    // A forced-only title has nothing to pick, but its style still matters.
    more.disabled = !vis.styleVisible;
    return chosen;
  }

  /** The menu: each language, Off, then the way to the style panel. */
  function items() {
    const regular = visibility(tracks).pickerRows.filter((key) => key !== "off");
    const rows = regular.map((key) => ({ value: key, label: labelFor(key), current: key === showing }));
    if (regular.length > 0) rows.push({ value: "off", label: "Off", current: showing === null });
    rows.push({ value: STYLE, label: "Style…" });
    return rows;
  }

  function choose(key) {
    if (key !== "off") last = key;
    remember("subtitle", key);
    recompute();
  }

  /**
   * Rebuilt per title: `<track>` elements attached, tracks in catalog order.
   * `alangJson` is `set.alang` as the index stores it — a JSON array string,
   * the audio fallback before the real probe below narrows it.
   */
  function offer(newTracks, alangJson) {
    tracks = newTracks ?? [];
    alang = parsedAlang(alangJson);
    audioTag = null;
    last = null;
    recompute();
  }

  /** The probed audio language arrived, or the viewer switched it. */
  function setAudio(tag) {
    audioTag = tag;
    recompute();
  }

  /**
   * CC and `c`: off, and back on to the language that was showing — or, when
   * none has shown this title, to the one `toggleOn` picks, which is what the
   * player picks anywhere else. Forced follows the audio language on its own
   * line and is never what this switches.
   */
  function toggle() {
    if (cc.disabled) return;
    const chosen = chooseSubtitles({ tracks, remembered: recall("subtitle"), preferred: preferred(), audioTag, alang });
    const next = chosen.regular !== null ? "off" : toggleOn(tracks, { last, preferred: preferred(), audio: chosen.audio });
    if (next === null) return;
    last = next === "off" ? chosen.regular : next;
    remember("subtitle", next);
    recompute();
  }

  cc.addEventListener("click", toggle);
  menus.list(more, {
    items,
    pick: (value) => (value === STYLE ? menus.panel(more, stylePanel) : choose(value)),
  });

  return { offer, setAudio, toggle };
}
