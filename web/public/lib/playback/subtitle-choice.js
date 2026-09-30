/**
 * Which subtitle tracks show, and what the picker and 'c' key do about it.
 *
 * Pure and DOM-free on purpose: `plan.md`'s "Playback rule" is exercised here
 * against `web/test/fixtures/subtitles/choice-cases.json`, the same fixture
 * every surface (web, phone, TV) is tested against, so the rule is proved
 * once rather than once per player.
 *
 * A track is `{lang, forced, sdh, label}` — the shape the catalog's
 * `subtitles` field already carries. "Regular" means non-forced throughout:
 * a forced track is never offered in the picker and never remembered: it
 * follows the audio language on its own line.
 */

/** @typedef {{lang: string, forced: boolean, sdh: boolean, label: string}} SubtitleTrack */

/** `lang`, or `lang:sdh` for the SDH track of a language that has both. */
export function trackKey(track) {
  return track.sdh ? `${track.lang}:sdh` : track.lang;
}

/** Whether two language tags are the same non-null language. */
export function sameLanguage(a, b) {
  return a != null && b != null && a === b;
}

/** ISO 639-2/B codes this library's files carry that Intl does not canonicalise. */
const BIBLIOGRAPHIC = { ger: "de", deu: "de", eng: "en" };

/**
 * The audio language: the playing stream's own tag, normalised, or the
 * first of the set's own `alang` when the stream carries none.
 * @param {string|null} tag
 * @param {string[]} alang
 * @returns {string|null}
 */
export function audioLanguage(tag, alang) {
  if (tag) return BIBLIOGRAPHIC[tag.toLowerCase()] ?? tag.toLowerCase();
  return alang?.[0] ?? null;
}

function regularTracksOf(tracks) {
  return tracks.filter((track) => !track.forced);
}

/**
 * The regular track `key` resolves to, trying an exact key first, then the
 * same language plain, then the same language SDH. `null` for no match.
 */
function cascadeMatch(key, regularTracks) {
  if (key == null) return null;
  const exact = regularTracks.find((track) => trackKey(track) === key);
  if (exact) return exact;
  const lang = key.split(":")[0];
  const plain = regularTracks.find((track) => sameLanguage(track.lang, lang) && !track.sdh);
  if (plain) return plain;
  return regularTracks.find((track) => sameLanguage(track.lang, lang) && track.sdh) ?? null;
}

/**
 * The regular track showing passively — before any 'c' press this session —
 * or `null` for none. `plan.md`: `wanted = remembered ?? preferred ?? off`;
 * `regular = wanted off ? none : same key -> same lang plain -> same lang
 * SDH -> profile preference -> none`.
 */
function chooseRegular(tracks, remembered, preferred) {
  const regularTracks = regularTracksOf(tracks);
  const wanted = remembered ?? preferred ?? "off";
  if (wanted === "off") return null;
  const first = cascadeMatch(wanted, regularTracks);
  if (first) return first;
  if (remembered != null && preferred != null && preferred !== "off") {
    return cascadeMatch(preferred, regularTracks);
  }
  return null;
}

/**
 * The forced track's language, or `null`. Shows only in the audio's own
 * language, only while no regular track is showing — including while
 * subtitles are switched off, which is the one line 'c' does not touch.
 */
function chooseForced(tracks, audio, regularKey) {
  if (regularKey != null || audio == null) return null;
  return tracks.some((track) => track.forced && sameLanguage(track.lang, audio)) ? audio : null;
}

/**
 * What shows right now, from the catalog's tracks and this viewer's choices.
 * @param {{tracks: SubtitleTrack[], remembered: string|null, preferred: string|null,
 *   audioTag: string|null, alang: string[]}} args
 * @returns {{audio: string|null, regular: string|null, forced: string|null}}
 */
export function chooseSubtitles({ tracks, remembered, preferred, audioTag, alang }) {
  const audio = audioLanguage(audioTag, alang);
  const regular = chooseRegular(tracks, remembered, preferred);
  return { audio, regular: regular ? trackKey(regular) : null, forced: chooseForced(tracks, audio, regular ? trackKey(regular) : null) };
}

/**
 * What 'c' turns subtitles on to, when none are showing: the last regular
 * this session, else the profile preference, else the audio language, else
 * the first regular track. `null` when there is nothing to turn on.
 * @param {SubtitleTrack[]} tracks
 * @param {{last: string|null, preferred: string|null, audio: string|null}} args
 */
export function toggleOn(tracks, { last, preferred, audio }) {
  const regularTracks = regularTracksOf(tracks);
  if (regularTracks.length === 0) return null;
  if (last != null) {
    const found = cascadeMatch(last, regularTracks);
    if (found) return trackKey(found);
  }
  if (preferred != null && preferred !== "off") {
    const found = cascadeMatch(preferred, regularTracks);
    if (found) return trackKey(found);
  }
  if (audio != null) {
    const found = regularTracks.find((track) => sameLanguage(track.lang, audio));
    if (found) return trackKey(found);
  }
  return trackKey(regularTracks[0]);
}

/**
 * The picker's rows, and whether the CC toggle and the style trigger show.
 *
 * `pickerRows` is empty (no "Off" either) when there is nothing to pick
 * between — a forced-only title, or one with no tracks at all. `ccVisible`
 * follows the same count. `styleVisible` is wider: a forced track can show
 * on its own, so its size and backing are still worth offering.
 * @param {SubtitleTrack[]} tracks
 */
export function visibility(tracks) {
  const regularTracks = regularTracksOf(tracks);
  return {
    pickerRows: regularTracks.length === 0 ? [] : ["off", ...regularTracks.map(trackKey)],
    ccVisible: regularTracks.length > 0,
    styleVisible: tracks.length > 0,
  };
}
