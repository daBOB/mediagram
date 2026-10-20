/**
 * Sections a profile is not shown: which section a set sits in, the
 * catalogue without the hidden ones (the kids filter with it), and the page
 * that follows on the shipped document — its nav, a department, Home, search,
 * Continue and My List — filtered again at once after a change made here.
 */

import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, test } from "bun:test";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { staticResponse } from "../src/http/static-files";
import { SECTIONS } from "../public/lib/catalog/sections.js";
import { countAnime, groupDepartments } from "../public/lib/departments.js";
import { countDocumentaries } from "../public/lib/documentaries.js";
import { createLibrarySession } from "../public/lib/library-session.js";
import { departmentOf, hiddenSectionsOf, withoutHidden } from "../public/lib/section-access.js";
import { answerPin, buttonNamed, descendants, textOf } from "./support/browser-application";
import { catalogSet } from "./support/catalog-set";
import { htmlApplicationEnvironment } from "./support/html-application";
import { fakeLibraryPort } from "./support/library-session-port";
import { settle, type Node } from "./support/player-environment";

const film = catalogSet({ setId: "Heat", title: "Heat", kind: "movie", fsk: "12" });
const animeFilm = catalogSet({ setId: "Mononoke", title: "Mononoke", kind: "movie", anime: true });
const animeEpisode = catalogSet({ setId: "Frieren", title: "Frieren", kind: "ep", show: "Frieren", season: 1, episode: "1", anime: true });
const episode = catalogSet({ setId: "Dark", title: "Dark", kind: "ep", show: "Dark", season: 1, episode: "1" });
const documentary = catalogSet({ setId: "Planet", title: "Planet", kind: "docu" });
const lesson = catalogSet({ setId: "Ownership", title: "Ownership", kind: "tut", show: "Rust" });
const handout = catalogSet({ setId: "Slides", title: "Slides", kind: "doc", show: "Rust" });
const clip = catalogSet({ setId: "Clip", title: "Clip", kind: "clip" });

/** Where `groupDepartments` files a set on its own: the departments it counts it in. */
function shelvedIn(set: typeof film) {
  const library = groupDepartments([set]);
  const counts: Record<string, number> = {
    movies: library.movies.length, series: library.series.length, anime: countAnime(library.anime),
    documentaries: countDocumentaries(library.documentaries), tutorials: library.tutorials.length,
  };
  return Object.keys(counts).filter((key) => counts[key]! > 0);
}

test("each set is in the section its department shelf files it under", () => {
  const expected: [typeof film, string][] = [
    [film, "movies"], [animeFilm, "anime"], [animeEpisode, "anime"], [episode, "series"],
    [documentary, "documentaries"], [lesson, "tutorials"], [handout, "tutorials"], [clip, "movies"],
  ];
  for (const [set, section] of expected) {
    expect(departmentOf(set), set.setId).toBe(section);
    expect(shelvedIn(set), set.setId).toEqual([section]);
  }
});

test("a hidden section's sets are left out, and a key this build does not know leaves out nothing", () => {
  const all = [film, animeFilm, animeEpisode, episode, documentary, lesson, handout];
  expect(withoutHidden(all, ["anime", "tutorials"]).map((set: typeof film) => set.setId)).toEqual(["Heat", "Dark", "Planet"]);
  expect(withoutHidden(all, ["podcasts"])).toEqual(all);
  expect(withoutHidden(all, [])).toEqual(all);
});

test("the owner, and nobody, has nothing hidden; anyone else what their list names, in the nav's order", () => {
  expect(hiddenSectionsOf(null)).toEqual([]);
  expect(hiddenSectionsOf({ kids: false, admin: true, owner: true, hiddenSections: ["anime"] })).toEqual([]);
  // A household's admin sees what the owner left them.
  expect(hiddenSectionsOf({ kids: false, admin: true, hiddenSections: ["anime"] })).toEqual(["anime"]);
  expect(hiddenSectionsOf({ kids: false, admin: false, hiddenSections: ["tutorials", "podcasts", "anime", "movies"] }))
    .toEqual(["movies", "anime", "tutorials"]);
  // A profile from a server that sends no list hides nothing.
  expect(hiddenSectionsOf({ kids: true, kidsAge: 12 })).toEqual([]);
});

test("a kid is shown what both its age limit and its sections allow, where the catalogue is filtered", async () => {
  const family = catalogSet({ setId: "Family", kind: "movie", fsk: "6" });
  const grown = catalogSet({ setId: "Grown", kind: "movie", fsk: "16" });
  const kidsAnime = catalogSet({ ...animeEpisode, setId: "Kids anime", fsk: "6" });
  const fake = fakeLibraryPort(JSON.stringify([family, film, grown, kidsAnime]));
  let profile: object = { kids: true, kidsAge: 12, hiddenSections: ["anime"] };
  const session = createLibrarySession({
    port: fake.port, state: { profile: () => profile, kidsMarks: () => new Map() }, remoteState: () => {},
    together: { heard: () => {}, reopened: () => {}, inSession: () => false, onLeave: () => {} },
  });
  await session.start();
  session.reapply();
  const shown = () => session.current().visibleSets.map((set) => set.setId);
  expect(shown()).toEqual(["Family", "Heat"]);
  profile = { kids: false, admin: false, hiddenSections: ["anime"] };
  session.reapply();
  expect(shown()).toEqual(["Family", "Heat", "Grown"]);
  profile = { kids: false, admin: true, owner: true, hiddenSections: ["anime"] };
  session.reapply();
  expect(shown()).toEqual(["Family", "Heat", "Grown", "Kids anime"]);
});

describe("the page, for a profile with a section hidden", () => {
  let directory: string;
  let html: string;
  let serial = 0;
  let env: Awaited<ReturnType<typeof htmlApplicationEnvironment>>;
  /** The household the stub server lists: Maja, watching, and andre, who runs it. */
  let profiles: Record<string, unknown>[];
  const ANDRE = { id: "andre", name: "andre", kids: false, kidsAge: null, parentId: null, admin: true, hasPin: true, hiddenSections: [] };
  const MAJA = { id: "viewer", name: "Maja", kids: false, kidsAge: null, parentId: null, admin: false, hasPin: true, hiddenSections: ["movies"] };

  beforeAll(async () => {
    const served = staticResponse({ method: "GET", path: "/", range: null });
    if (!(served.body instanceof Uint8Array)) throw new Error("Missing served HTML document");
    html = new TextDecoder().decode(served.body);
    directory = await mkdtemp(join(tmpdir(), "mediagram-section-access-"));
    const entry = join(directory, "entry.js");
    await writeFile(entry, `import ${JSON.stringify(join(import.meta.dir, "../public/app.js"))};`);
    const result = await Bun.build({ entrypoints: [entry], outdir: directory, naming: "application.js", target: "browser", external: ["/lib/hls.mjs"] });
    if (!result.success) throw new AggregateError(result.logs, "Building the application on the shipped document");
  });
  afterAll(() => rm(directory, { recursive: true, force: true }));
  beforeEach(async () => {
    env = await htmlApplicationEnvironment(html);
    profiles = [ANDRE, MAJA];
    env.respondWith(async (url, options) => {
      if (url === "/api/sets") return Response.json([film, episode, animeEpisode]);
      if (url === "/api/player") return Response.json({ remote: false });
      if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, profiles });
      if (url.endsWith("/state")) return Response.json({ progress: [{ setId: "Heat", at: 90, duration: 600, updatedAt: 1 }], watchlist: ["Heat"] });
      if (url === "/api/kids") return Response.json({ kids: [] });
      if (url.startsWith("/api/search")) return Response.json({ query: "x", hits: [film, episode] });
      if (url === "/api/profiles/viewer/hidden-sections") {
        const { sections } = JSON.parse(String(options!.body));
        profiles = profiles.map((entry) => (entry.id === "viewer" ? { ...entry, hiddenSections: sections } : entry));
      }
      if (options?.method && options.method !== "HEAD") return new Response(null, { status: 204 });
      return new Response(null, { status: 404 });
    });
  });
  afterEach(async () => {
    env.document.getElementById("player")?.close();
    await env.visibility("hidden");
    await settle();
    env.restore();
  });

  const start = async () => { await import(`${join(directory, "application.js")}?case=${++serial}`); await settle(); };
  const page = () => textOf(env.node("main"));
  /** The departments bar's links, as the shipped document has them. */
  const links = () => (env.document as unknown as { querySelectorAll(selector: string): Node[] }).querySelectorAll(".departments a");
  const link = (section: string) => links().find((node) => node.dataset.section === section)!;
  const hiddenLinks = () => links().filter((node) => node.hidden).map((node) => node.dataset.section);

  test("its nav, the department, Home, a title's page, search, Continue and My List leave the section out", async () => {
    await start();
    // Anime keeps its own rule beside this one: there is anime, so its link is there.
    expect(hiddenLinks()).toEqual(["movies"]);
    expect([env.node("n-continue").textContent, env.node("n-watchlist").textContent]).toEqual(["0", "0"]);
    expect(env.node("rail-masthead").textContent).not.toContain("film");
    // Its pages say only that there is nothing, with no upload hint: the department and its paged shelf.
    await env.navigate("#/movies");
    expect(page()).toBe(SECTIONS.movies.empty);
    await env.navigate("#/movies/page/2");
    expect(page()).not.toContain("mediagram add");
    await env.navigate("#/film/Heat");
    expect(page()).toContain("not in the library");
    await env.navigate("#/home");
    expect(page()).toContain("Dark");
    expect(page()).not.toContain("Heat");
    await env.navigate("#/search/x");
    expect(page()).toContain("Dark");
    expect(page()).not.toContain("Heat");
  });

  test("a hidden Anime loses its link as Movies does", async () => {
    profiles = [ANDRE, { ...MAJA, hiddenSections: ["anime"] }];
    await start();
    expect(hiddenLinks()).toEqual(["anime"]);
    await env.navigate("#/anime");
    expect(page()).toBe(SECTIONS.anime.empty);
  });

  test("the owner is shown every section, whatever their list says", async () => {
    profiles = [{ ...ANDRE, owner: true, id: "viewer", hiddenSections: ["movies"] }];
    await start();
    expect(link("movies").hidden).toBe(false);
    expect(env.node("n-movies").textContent).toBe("1");
    expect(env.node("rail-masthead").textContent).toContain("film");
  });

  test("a section switched off in Settings for whoever is watching leaves the page at once, and Manage stays open", async () => {
    profiles = [ANDRE, { ...MAJA, hiddenSections: [] }];
    await start();
    expect(link("movies").hidden).toBe(false);
    expect(env.node("n-movies").textContent).toBe("1");
    await env.navigate("#/settings");
    buttonNamed(env.node("main"), "Profiles").fire("click");
    buttonNamed(env.node("main"), "andre").fire("click");
    await answerPin(env.node("main"), "1357");
    const group = descendants(env.node("main")).find((node) => node.getAttribute("aria-label") === "Sections for Maja")!;
    descendants(group).find((node) => node.getAttribute("role") === "switch" && node.textContent === SECTIONS.movies.label)!.fire("click");
    await settle();
    expect(link("movies").hidden).toBe(true);
    expect(env.node("n-movies").textContent).toBe("0");
    expect(page()).toContain("As andre");
  });
});
