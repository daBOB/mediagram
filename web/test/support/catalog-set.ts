import type { CatalogSet } from "../../public/lib/library.js";

/** A complete browser catalog row; absent metadata is null, as in the HTTP projection. */
export function catalogSet(over: Partial<CatalogSet> = {}): CatalogSet {
  return {
    setId: `01SET${Math.random().toString(36).slice(2, 10).toUpperCase()}`,
    kind: "movie", title: "A Title", show: null, path: null, chap: null,
    season: null, episode: null, year: null,
    container: "mp4", vcodec: "h264", acodec: "aac",
    quality: null, hdr: null, alang: null, slang: null,
    duration: 60, total: 1000, partCount: 1, addedAt: 0,
    poster: null, backdrop: null, seasonPoster: null, showKey: null, genres: [], fsk: null,
    tagline: null, rating: null, popularity: null,
    offline: false, hasSummary: false, subtitles: [],
    ...over,
  };
}
