/**
 * The ☰ sidebar against a fake DOM: shown only for a run, opened on the
 * season playing, the show page's rows with the playing one marked and the
 * watched ones greyed, a pick handed on and the sidebar shut, and Esc.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mountEpisodeSidebar } from "../public/lib/playback/episode-sidebar.js";
import { collectionOf } from "../public/lib/playback/plays-next.js";
import { groupDepartments } from "../public/lib/departments.js";
import * as state from "../public/lib/watch-state.js";
import { catalogSet as set } from "./support/catalog-set";
import { browserEnvironment, settle, type Node } from "./support/player-environment";
import type { CatalogSet } from "../public/lib/library.js";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(async () => {
  env = browserEnvironment();
  await state.useProfile(null);
  env.node("episode-sidebar").hidden = true;
});
afterEach(async () => {
  await settle();
  env.restore();
});

const ep = (id: string, season: number | null, episode: string | null, title: string | null = id) =>
  set({ setId: id, kind: "ep", show: "Star City", season, episode, title, duration: 1260 });
const s1 = [ep("s1e1", 1, "1"), ep("s1e2", 1, "2"), ep("s1e3", 1, "3")];
const s2 = [ep("s2e1", 2, "1")];
const showOf = (sets: CatalogSet[]) => collectionOf(groupDepartments(sets), sets[0]!);

const panel = () => env.node("episode-sidebar");
const textOf = (node: Node): string => node.textContent + node.children.map(textOf).join("");
const find = (root: Node, test: (node: Node) => boolean): Node | undefined =>
  root.children.find(test) ?? root.children.map((child) => find(child, test)).find(Boolean);
/** The header's four: ‹, the season's name, ›, ✕. */
const head = (at: number) => panel().children[0]!.children[at]!;
const titleText = () => head(1).textContent;
const prevSeason = () => head(0);
const nextSeason = () => head(2);
const shut = () => head(3);
const rows = () => panel().children[1]!.children[0]!.children;

function mount() {
  const picked: string[] = [];
  let closes = 0;
  const sidebar = mountEpisodeSidebar({ onPick: (chosen) => picked.push(chosen.setId), onClose: () => { closes++; } });
  return { sidebar, picked, closes: () => closes };
}

describe("when it is offered", () => {
  test("☰ shows for a run and hides for a title without one", () => {
    const { sidebar } = mount();
    sidebar.open(s1[0]!, showOf([...s1, ...s2]));
    expect(env.node("episodes").hidden).toBe(false);
    sidebar.open(set({ kind: "movie" }), null);
    expect(env.node("episodes").hidden).toBe(true);
  });
});

describe("the seasons", () => {
  test("opens on the season playing, and steps between seasons, each end disabled", () => {
    const { sidebar } = mount();
    sidebar.open(s2[0]!, showOf([...s1, ...s2]));
    env.node("episodes").fire("click");
    expect(panel().hidden).toBe(false);
    expect(env.node("episodes").getAttribute("aria-expanded")).toBe("true");
    expect(titleText()).toBe("Season 2");
    expect(nextSeason().disabled).toBe(true);
    prevSeason().fire("click");
    expect(titleText()).toBe("Season 1");
    expect(prevSeason().disabled).toBe(true);
    expect(rows()).toHaveLength(3);
  });

  test("one season is a title without arrows", () => {
    const { sidebar } = mount();
    sidebar.open(s1[0]!, showOf(s1));
    env.node("episodes").fire("click");
    expect(titleText()).toBe("Season 1");
    expect(prevSeason().hidden).toBe(true);
    expect(nextSeason().hidden).toBe(true);
  });
});

describe("the rows", () => {
  test("watched greyed with a tick, partly watched with a progress line, the playing one says so and cannot be picked", () => {
    state.setWatched("s1e1", true);
    state.setProgress("s1e3", 630, 1260);
    const { sidebar } = mount();
    sidebar.open(s1[1]!, showOf(s1));
    env.node("episodes").fire("click");
    const [watched, playing, partway] = rows();
    expect(watched!.classes.has("is-watched")).toBe(true);
    expect(find(watched!, (node) => node.className === "tick")?.textContent).toBe("✓");
    expect(find(watched!, (node) => node.className === "meta")?.textContent).toBe("21m");
    expect(playing!.scrolledIntoView).toBe(true);
    expect(playing!.disabled).toBe(true);
    expect(playing!.getAttribute("aria-current")).toBe("true");
    expect(find(playing!, (node) => node.className === "meta")?.textContent).toBe("Now playing");
    expect(partway!.classes.has("is-watched")).toBe(false);
    expect(find(partway!, (node) => node.className === "watched")).toBeDefined();
  });

  test("picking a row hands it on to be opened and shuts the sidebar", () => {
    const { sidebar, picked, closes } = mount();
    sidebar.open(s1[0]!, showOf(s1));
    env.node("episodes").fire("click");
    rows()[2]!.fire("click");
    expect(picked).toEqual(["s1e3"]);
    expect(panel().hidden).toBe(true);
    expect(env.node("episodes").getAttribute("aria-expanded")).toBe("false");
    expect(closes()).toBe(1);
  });

  test("a run with an untitled, numberless, seasonless episode still draws, that episode in a group of its own last", () => {
    const messy = [...s1, ep("odd", null, null, null)];
    const { sidebar } = mount();
    sidebar.open(set({ setId: "unknown-to-the-index", kind: "ep", show: "Star City" }), showOf(messy));
    env.node("episodes").fire("click");
    expect(titleText()).toBe("Season 1");
    nextSeason().fire("click");
    expect(titleText()).toBe("Episodes");
    expect(textOf(rows()[0]!)).toContain("odd");
  });
});

describe("closing", () => {
  const escape = () => {
    const event = Object.assign(new Event("keydown", { cancelable: true }), { key: "Escape" });
    env.node("player").dispatchEvent(event);
    return event;
  };

  test("✕, ☰ again, and Esc each shut it; Esc is taken from the dialog only while it is open", () => {
    const { sidebar } = mount();
    sidebar.open(s1[0]!, showOf(s1));
    env.node("episodes").fire("click");
    shut().fire("click");
    expect(panel().hidden).toBe(true);
    env.node("episodes").fire("click");
    env.node("episodes").fire("click");
    expect(panel().hidden).toBe(true);
    env.node("episodes").fire("click");
    expect(escape().defaultPrevented).toBe(true);
    expect(panel().hidden).toBe(true);
    expect(escape().defaultPrevented).toBe(false);
  });

  test("the player is marked while it is open, so the card can stand clear of it", () => {
    const { sidebar } = mount();
    sidebar.open(s1[0]!, showOf(s1));
    expect(env.node("player").classes.has("sidebar-open")).toBe(false);
    env.node("episodes").fire("click");
    expect(env.node("player").classes.has("sidebar-open")).toBe(true);
    shut().fire("click");
    expect(env.node("player").classes.has("sidebar-open")).toBe(false);
  });

  test("an Esc something earlier already took leaves it open", () => {
    const { sidebar } = mount();
    sidebar.open(s1[0]!, showOf(s1));
    env.node("episodes").fire("click");
    const taken = Object.assign(new Event("keydown", { cancelable: true }), { key: "Escape" });
    taken.preventDefault();
    env.node("player").dispatchEvent(taken);
    expect(panel().hidden).toBe(false);
  });

  test("a new title shuts it", () => {
    const { sidebar } = mount();
    sidebar.open(s1[0]!, showOf(s1));
    env.node("episodes").fire("click");
    sidebar.open(s1[1]!, showOf(s1));
    expect(sidebar.isOpen()).toBe(false);
  });
});
