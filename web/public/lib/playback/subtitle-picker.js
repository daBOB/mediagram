/**
 * The subtitle half of the transport bar: the picker, the CC/style
 * visibility, and what 'c' does. Moved out of `transport.js`, which held
 * this before the catalog carried forced and SDH tracks — `subtitle-choice.js`
 * is the rule now, this is only the DOM around it.
 *
 * `video.textTracks[i]` is always the API's `tracks[i]`: `player.js` attaches
 * one `<track>` per catalog entry, in the catalog's own order, and never
 * reorders them.
 */

import { chooseSubtitles, toggleOn, trackKey, visibility } from "./subtitle-choice.js";
import { preferenceOf } from "../watch-state.js";

/** @typedef {import("./subtitle-choice.js").SubtitleTrack} SubtitleTrack */

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
 * @param {{video: HTMLVideoElement, subs: HTMLElement, picker: HTMLSelectElement,
 *   styleTrigger: HTMLElement, recall: (name: string) => string|null,
 *   remember: (name: string, value: string) => void}} deps
 */
export function mountSubtitlePicker({ video, subs, picker, styleTrigger, recall, remember }) {
  /** @type {SubtitleTrack[]} */
  let tracks = [];
  let alang = [];
  let audioTag = null;
  /** The last regular track chosen this session, cleared on every title open. */
  let last = null;

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

    const vis = visibility(tracks);
    subs.hidden = !vis.ccVisible;
    styleTrigger.hidden = !vis.styleVisible;
    picker.replaceChildren();
    for (const key of vis.pickerRows) {
      const option = document.createElement("option");
      option.value = key;
      option.textContent = key === "off" ? "Off" : labelFor(key);
      picker.append(option);
    }
    if (vis.pickerRows.length > 0) picker.value = chosen.regular ?? "off";
    return chosen;
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

  picker.addEventListener("change", () => {
    const key = picker.value;
    if (key !== "off") last = key;
    remember("subtitle", key);
    recompute();
  });

  /**
   * `c`: off and back to whatever they were. Forced follows the audio
   * language on its own line and is never what this switches.
   */
  function toggle() {
    if (subs.hidden) return;
    const chosen = chooseSubtitles({ tracks, remembered: recall("subtitle"), preferred: preferred(), audioTag, alang });
    const next = chosen.regular !== null ? "off" : toggleOn(tracks, { last, preferred: preferred(), audio: chosen.audio });
    if (next === null) return;
    if (next !== "off") last = next;
    remember("subtitle", next);
    recompute();
  }

  return { offer, setAudio, toggle };
}
