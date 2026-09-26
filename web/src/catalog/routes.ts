/** Catalog presentation and read endpoints, rebuilt together for each catalog. */
import type { Database } from "bun:sqlite";
import { subtitle, subtitleLanguages, summary } from "./assets";
import type { AudioTrackReader } from "./audio-tracks";
import type { HeldSets } from "../cache/held";
import { listPlayable, listSearchable, playableSet, type PlayableSet } from "../catalog";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { negotiatedResponse } from "../http/compression";
import { PosterStore, backdropKeyFor, posterKeyFor, seasonPosterKeyFor } from "../package/posters";
import { bodiless, withBody } from "../response";
import { SearchIndex } from "../search/index";
import { providerFactsByShow, showMeta } from "./shows";
import { creditsFor, franchises, peopleSearch, personFor } from "./credits";
import type { SheetStore } from "../thumbs/sheets";
import { artworkKeys, artworkResponse } from "./artwork-routes";

const SUMMARY_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/summary$/;
const AUDIO_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/audio$/;
const HELD_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/held$/;
const SHOW_PATH = /^\/api\/shows\/(tmdb-(?:movie|tv)-\d{1,12})$/;
const CREDITS_PATH = /^\/api\/shows\/(tmdb-(?:movie|tv)-\d{1,12})\/credits$/;
const PERSON_PATH = /^\/api\/people\/(\d{1,12})$/;
const SUBTITLE_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/subtitles\/([A-Za-z]{2,8})\.vtt$/;

export interface CatalogRouterOptions {
  db: Database;
  held?: HeldSets;
  posters?: PosterStore;
  thumbs?: SheetStore;
  audio?: AudioTrackReader;
}

/** Called after the dispatcher has accepted GET or HEAD. */
export function createCatalogRouter(options: CatalogRouterOptions) {
  const { db, audio } = options;
  const posters = options.posters ?? new PosterStore(null);
  // A custom image (the `artwork` table) counts as "has art" beside a
  // packaged file: a course or an untagged title with only a manual upload
  // must still show it, not fall back to its initials for want of a file.
  const artwork = artworkKeys(db);
  const index = new SearchIndex(listSearchable(db));
  const provider = providerFactsByShow(db);
  const has = (key: string | null) => key !== null && (posters.has(key) || artwork.has(key));
  const people = peopleSearch(db, has);

  // Catalog and search results share the same browser-facing projection.
  // Storage/provider identifiers never become media locations in a response.
  function forBrowser({ tmdb, ...set }: PlayableSet) {
    // A course, a documentary and an untagged film have no provider id; their
    // art, if any, is keyed from their own name (the collection's, when they
    // belong to one) rather than from an id nothing ever gave them.
    const key = posterKeyFor(set.kind, tmdb, set.show ?? set.title);
    const seasonKey = set.kind === "ep" ? seasonPosterKeyFor(key, set.season) : null;
    const backdropKey = backdropKeyFor(key);
    const facts = key === null ? undefined : provider.get(key);
    return {
      ...set,
      offline: options.held?.has(set.setId) ?? false,
      showKey: key,
      poster: has(key) ? key : null,
      backdrop: has(backdropKey) ? backdropKey : null,
      genres: facts?.genres ?? [],
      fsk: facts?.fsk ?? null,
      // What the home page's editorial picks read: one catalog request
      // carries them all, rather than one description request per title.
      tagline: facts?.tagline ?? null,
      rating: facts?.rating ?? null,
      popularity: facts?.popularity ?? null,
      collectionId: facts?.collectionId ?? null,
      collectionName: facts?.collectionName ?? null,
      seriesType: facts?.seriesType ?? null,
      showStatus: facts?.status ?? null,
      seasonPoster: has(seasonKey) ? seasonKey : null,
      hasSummary: summary(db, set.setId) !== null,
      subtitles: subtitleLanguages(db, set.setId),
    };
  }

  return async function catalogRoute(request: PlayerRequest): Promise<PlayerResponse | null> {
    const headOnly = request.method === "HEAD";
    const json = (value: unknown) => negotiatedResponse(request, JSON.stringify(value), "application/json", { headOnly });
    if (request.path === "/api/search") {
      const hits = index.search(request.query ?? "")
        .map(({ summary: _summary, matched, excerpt, ...set }) => ({
          ...forBrowser(set), matched, excerpt,
        }));
      return json({ query: request.query ?? "", hits, people: people(request.query ?? "") });
    }
    if (request.path === "/api/sets") return json(listPlayable(db).map(forBrowser));

    if (request.path === "/api/franchises") return json(franchises(db));
    const credits = CREDITS_PATH.exec(request.path);
    if (credits) return json(creditsFor(db, credits[1]!, has));
    const person = PERSON_PATH.exec(request.path);
    if (person) {
      const found = personFor(db, Number(person[1]), has);
      return found === null ? bodiless(404) : json(found);
    }
    const show = SHOW_PATH.exec(request.path);
    if (show) {
      const meta = showMeta(db, show[1]!);
      return meta === null ? bodiless(404) : json(meta);
    }
    const artwork = await artworkResponse(db, posters, options.thumbs, request);
    if (artwork) return artwork;

    const synopsis = SUMMARY_PATH.exec(request.path);
    if (synopsis) {
      const body = summary(db, synopsis[1]!);
      return body === null ? bodiless(404) : withBody(body, "text/plain; charset=utf-8", { headOnly });
    }
    const held = HELD_PATH.exec(request.path);
    if (held) {
      const setId = held[1]!;
      if (playableSet(db, setId) === null) return bodiless(404);
      return json({ held: (await options.held?.check(setId)) ?? false });
    }
    const wantsAudio = AUDIO_PATH.exec(request.path);
    if (wantsAudio) {
      if (playableSet(db, wantsAudio[1]!) === null) return bodiless(404);
      return json({ tracks: audio ? await audio.read(wantsAudio[1]!) : [] });
    }
    const subtitles = SUBTITLE_PATH.exec(request.path);
    if (subtitles) {
      const body = subtitle(db, subtitles[1]!, subtitles[2]!);
      return body === null ? bodiless(404) : withBody(body, "text/vtt; charset=utf-8", { headOnly });
    }
    return null;
  };
}
