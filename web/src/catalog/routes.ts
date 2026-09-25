/** Catalog presentation and read endpoints, rebuilt together for each catalog. */
import type { Database } from "bun:sqlite";
import { subtitle, subtitleLanguages, summary } from "./assets";
import type { AudioTrackReader } from "./audio-tracks";
import type { HeldSets } from "../cache/held";
import { listPlayable, listSearchable, playableSet, type PlayableSet } from "../catalog";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { PosterStore, backdropKeyFor, posterKeyFor, seasonPosterKeyFor } from "../package/posters";
import { bodiless, withBody } from "../response";
import { SearchIndex } from "../search/index";
import { providerFactsByShow, showMeta } from "./shows";
import type { SheetStore } from "../thumbs/sheets";
import { artworkResponse } from "./artwork-routes";

const SUMMARY_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/summary$/;
const AUDIO_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/audio$/;
const HELD_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/held$/;
const SHOW_PATH = /^\/api\/shows\/(tmdb-(?:movie|tv)-\d{1,12})$/;
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
  const index = new SearchIndex(listSearchable(db));
  const provider = providerFactsByShow(db);

  // Catalog and search results share the same browser-facing projection.
  // Storage/provider identifiers never become media locations in a response.
  function forBrowser({ tmdb, ...set }: PlayableSet) {
    const key = posterKeyFor(set.kind, tmdb);
    const seasonKey = set.kind === "ep" ? seasonPosterKeyFor(key, set.season) : null;
    const backdropKey = backdropKeyFor(key);
    const facts = key === null ? undefined : provider.get(key);
    return {
      ...set,
      offline: options.held?.has(set.setId) ?? false,
      showKey: key,
      poster: posters.has(key) ? key : null,
      backdrop: posters.has(backdropKey) ? backdropKey : null,
      genres: facts?.genres ?? [],
      fsk: facts?.fsk ?? null,
      // What the home page's editorial picks read: one catalog request
      // carries them all, rather than one description request per title.
      tagline: facts?.tagline ?? null,
      rating: facts?.rating ?? null,
      popularity: facts?.popularity ?? null,
      seasonPoster: posters.has(seasonKey) ? seasonKey : null,
      hasSummary: summary(db, set.setId) !== null,
      subtitles: subtitleLanguages(db, set.setId),
    };
  }

  return async function catalogRoute(request: PlayerRequest): Promise<PlayerResponse | null> {
    const headOnly = request.method === "HEAD";
    const json = (value: unknown) => withBody(JSON.stringify(value), "application/json", { headOnly });
    if (request.path === "/api/search") {
      const hits = index.search(request.query ?? "")
        .map(({ summary: _summary, matched, excerpt, ...set }) => ({
          ...forBrowser(set), matched, excerpt,
        }));
      return json({ query: request.query ?? "", hits });
    }
    if (request.path === "/api/sets") return json(listPlayable(db).map(forBrowser));

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
