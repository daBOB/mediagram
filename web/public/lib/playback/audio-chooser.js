/**
 * The audio track menu: what the file holds, named for a person.
 *
 * The list comes from the server's `/audio` route, which probes the file.
 * It deliberately does not come from the catalog's `alang`: that column holds
 * *distinct* language codes with untagged streams dropped, so its positions
 * are not the ordinals a track has to be selected by. See `audio-tracks.ts`.
 */

import { languageLabel } from "../language-label.js";

/**
 * A channel count as a viewer would say it.
 *
 * Worth saying because it is often the only thing separating two rows: a film
 * routinely carries the same language twice, once as 5.1 and once as a stereo
 * downmix, and "German / German" is not a menu.
 */
export function channelLabel(channels) {
  if (!Number.isFinite(channels) || channels <= 0) return "";
  if (channels === 1) return "mono";
  if (channels === 2) return "stereo";
  // 6 and 8 are the two that have names everyone knows; the rest are rare
  // enough that the bare count is clearer than a guess at a layout.
  if (channels === 6) return "5.1";
  if (channels === 8) return "7.1";
  return `${channels}ch`;
}

/**
 * One row of the menu.
 *
 * The track's own title wins over everything when it has one, because a file
 * that bothered to name a stream "Director's Commentary" has said something
 * the language code cannot.
 */
export function trackLabel(track) {
  const parts = [languageLabel(track.lang, `Track ${track.index + 1}`)];
  if (track.title) parts.push(track.title);
  const channels = channelLabel(track.channels);
  if (channels) parts.push(channels);
  if (track.codec) parts.push(track.codec);
  return parts.join(" · ");
}

/**
 * Which track to start on: the one the file marks as its default, else the
 * first. Not "the one with the most channels" — that is the guess ffmpeg
 * makes, and on a film it routinely lands on a commentary.
 */
export function defaultTrack(tracks) {
  return (tracks.find((track) => track.isDefault) ?? tracks[0])?.index ?? 0;
}

/**
 * The track in `lang`, or `null` when this file has none.
 *
 * A remembered choice is a **language**, never an ordinal. `0:a:1` is German
 * in one release of an episode and a director's commentary in the next, so a
 * stored number would silently hand a viewer the wrong thing the first time a
 * file was replaced. A language tag either matches or it does not.
 *
 * Returning `null` rather than falling back is the point: the caller then
 * uses the file's own default, which is the right answer for a file that no
 * longer carries the language somebody once chose.
 */
export function trackForLanguage(tracks, lang) {
  if (typeof lang !== "string" || lang.trim() === "") return null;
  const wanted = lang.trim().toLowerCase();
  const found = tracks.findIndex((track) => (track.lang ?? "").toLowerCase() === wanted);
  return found === -1 ? null : tracks[found].index;
}

/**
 * Asks the server what `setId` holds.
 *
 * An empty list is the normal answer for a title with one stream and for one
 * the probe could not read, and both mean the same thing to the page: no
 * choice to offer. A failure is never raised — a chooser that cannot be built
 * is not a reason to interrupt a film.
 */
export async function loadAudioTracks(setId) {
  try {
    const response = await fetch(`/api/sets/${encodeURIComponent(setId)}/audio`);
    if (!response.ok) return [];
    const { tracks } = await response.json();
    return Array.isArray(tracks) ? tracks : [];
  } catch {
    return [];
  }
}

/**
 * Fills `select` with `tracks` and selects `chosen`.
 *
 * Returns whether there is a choice worth showing: one track is not a menu,
 * it is a label for something nobody can change.
 */
export function fillChooser(select, tracks, chosen) {
  select.textContent = "";
  for (const track of tracks) {
    const option = document.createElement("option");
    option.value = String(track.index);
    // Carried on the option so the page can remember *what was chosen* rather
    // than where it happened to sit — a preference is a language, and the
    // ordinal is only how this file happens to number it today.
    if (track.lang) option.dataset.lang = track.lang;
    option.textContent = trackLabel(track);
    select.append(option);
  }
  select.value = String(chosen);
  return tracks.length > 1;
}
