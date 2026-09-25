/**
 * What a show adds up to, from the episodes the catalog already carries.
 *
 * Nothing here is fetched or stored: runtime, size, picture and languages are
 * all facts about the files themselves, so a show describes itself as soon as
 * one episode of it exists. That also means every field can be missing, and a
 * field missing everywhere is left out rather than shown empty — a blank row
 * reads as "we looked and there is none", which would be a lie.
 *
 * Where episodes disagree the range is shown. A show half in 720p and half in
 * 1080p is worth knowing about, and picking either one to display would hide
 * exactly the thing worth seeing.
 */

import { countOf, humanDuration, humanSize } from "../format.js";
import { languageLabel } from "../language-label.js";
import { flattenCollection } from "../library.js";

/**
 * @typedef {import("../../../src/catalog/shows.ts").ShowMeta} ShowMeta
 * @typedef {Partial<Pick<ShowMeta, "totalEpisodes"|"totalSeasons"|"firstAir"|"lastAir">>} SeriesMetadata
 */

/** The parts that have something to say, as one line, or `null` if none do. */
function joined(...parts) {
  return parts.filter(Boolean).join(" \u00b7 ") || null;
}

/** The distinct values of `pick` across `sets`, in first-seen order. */
function distinct(sets, pick) {
  // A Set keeps first-insertion order, which is the order promised above.
  const seen = new Set();
  for (const set of sets) {
    const value = pick(set);
    for (const item of Array.isArray(value) ? value : [value]) {
      if (item !== null && item !== undefined && item !== "") seen.add(item);
    }
  }
  return [...seen];
}

/**
 * Languages from one of the index's JSON list columns; a malformed one is no
 * languages rather than a thrown error.
 *
 * Both audio and subtitles are read from the file's own tracks. The `assets`
 * table knows about extracted subtitle sidecars, which is a different
 * question — what the player can serve as a separate track — and answering
 * the header with it would say a show has no subtitles when its files do.
 */
function languagesIn(value) {
  if (Array.isArray(value)) return value;
  try {
    const parsed = JSON.parse(value ?? "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/** Quality labels smallest first, so a range reads the way a viewer expects. */
const QUALITY_ORDER = ["SD", "480p", "720p", "1080p", "1440p", "2160p"];

function sortedQualities(values) {
  return [...values].sort((a, b) => {
    const ai = QUALITY_ORDER.indexOf(a);
    const bi = QUALITY_ORDER.indexOf(b);
    // An unknown label sorts after the known ones rather than to the front.
    return (ai === -1 ? QUALITY_ORDER.length : ai) - (bi === -1 ? QUALITY_ORDER.length : bi);
  });
}

/**
 * The facts a show's header states.
 *
 * Returned as data rather than markup so it can be tested without a DOM, the
 * way the rest of this directory's grouping already is.
 * @param {Pick<import("../library.js").Collection, "divisions">} collection
 */
export function summarize(collection) {
  const sets = flattenCollection(collection);
  const seasons = collection.divisions?.length ?? 0;

  const years = distinct(sets, (s) => s.year).sort((a, b) => a - b);
  const qualities = sortedQualities(distinct(sets, (s) => s.quality));
  // SDR is the absence of anything worth saying, so it is only worth saying
  // when some episodes have more than it.
  const hdr = distinct(sets, (s) => s.hdr).filter((v) => v !== "SDR");

  const runtime = sets.reduce((n, s) => n + (Number(s.duration) || 0), 0);
  const size = sets.reduce((n, s) => n + (Number(s.total) || 0), 0);

  return {
    episodes: sets.length,
    seasons,
    runtime: runtime > 0 ? runtime : null,
    size: size > 0 ? size : null,
    years,
    qualities,
    hdr,
    video: distinct(sets, (s) => s.vcodec),
    audio: distinct(sets, (s) => s.acodec),
    audioLanguages: distinct(sets, (s) => languagesIn(s.alang)).map((code) => languageLabel(code, code)),
    subtitleLanguages: distinct(sets, (s) => languagesIn(s.slang)).map((code) => languageLabel(code, code)),
  };
}

/** `1080p`, or `720p–1080p` when a show is not all one thing. */
export function rangeOf(values) {
  if (values.length === 0) return null;
  if (values.length === 1) return values[0];
  return `${values[0]}–${values[values.length - 1]}`;
}

/** `1080p · HDR10 · h264 · aac`, or `null` when nothing was recorded. */
export function pictureLine(facts) {
  return joined(rangeOf(facts.qualities), ...facts.hdr, ...facts.video, ...facts.audio);
}

/**
 * How much of a show is held: `8 episodes · one season · 5h 58m · 38.7 GB`.
 *
 * When the provider has said how much exists and this library has less, the
 * counts say so — `8 of 16 episodes`. Holding all of it says nothing about
 * totals, because "16 of 16" is a fact about arithmetic, not about the show.
 * @param {SeriesMetadata|null} [meta]
 */
export function scaleLine(facts, meta = null) {
  return joined(
    held(facts.episodes, meta?.totalEpisodes ?? null, "episode"),
    facts.seasons > 0 ? held(facts.seasons, meta?.totalSeasons ?? null, "season") : null,
    facts.runtime === null ? null : humanDuration(facts.runtime),
    facts.size === null ? null : humanSize(facts.size),
  );
}

/**
 * `one season`, or `1 of 2 seasons` when some of it is missing.
 *
 * Numerals once a comparison is involved: `one of two seasons` reads as prose
 * where the point is the arithmetic.
 */
function held(have, total, noun) {
  if (typeof total !== "number" || total <= have) return countOf(have, noun);
  return `${have} of ${total} ${noun}s`;
}

/**
 * The labelled rows worth printing, in order.
 *
 * A language nobody recorded yields no row at all: an empty `Audio` line
 * would say the library looked and found none, which is not what happened.
 */
export function detailRows(facts) {
  return [
    { label: "Audio", value: facts.audioLanguages.join(", ") },
    { label: "Subtitles", value: facts.subtitleLanguages.join(", ") },
  ].filter((row) => row.value !== "");
}

/**
 * The years a show ran, or failing that the years this library's copies carry.
 *
 * The provider's dates describe the show; the episodes' years describe what is
 * held. A library with one season of a show that ran seven should say when the
 * show ran, so the dates win where they exist.
 * @param {SeriesMetadata|null} [meta]
 */
export function yearLine(facts, meta = null) {
  const first = year(meta?.firstAir);
  if (first !== null) {
    const last = year(meta?.lastAir);
    // A show still running has no last date, and an en dash to nowhere would
    // claim it ended.
    return last !== null && last !== first ? `${first}\u2013${last}` : String(first);
  }
  return facts.years.length > 0 ? rangeOf(facts.years.map(String)) : null;
}

/** The year out of a provider date, which is `YYYY-MM-DD` or absent. */
function year(date) {
  const parsed = Number(String(date ?? "").slice(0, 4));
  return Number.isInteger(parsed) && parsed > 1800 ? parsed : null;
}

/**
 * Genre, rating, network and status on one line, from the provider.
 *
 * A rating of zero is what an unrated title scores and is not a rating, so
 * the uploader records nothing for it and nothing is printed.
 * @param {Partial<Pick<ShowMeta, "genres"|"rating"|"network"|"status">>|null} meta
 */
export function provenance(meta) {
  if (!meta) return null;
  return joined(
    meta.genres,
    typeof meta.rating === "number" ? `\u2605 ${meta.rating.toFixed(1)}` : null,
    meta.network,
    meta.status,
  );
}
