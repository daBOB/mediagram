import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../public/lib/film-leader.js", import.meta.url), "utf8");

/** Runs the boot script against the few nodes it touches, as index.html lays them out. */
function boot() {
  const video = new EventTarget();
  const placed: string[] = [];
  const stage = { hidden: true, prepend: () => placed.push("stage") };
  const library = { prepend: () => placed.push("library") };
  const drawing = { content: { firstElementChild: { cloneNode: () => ({}) } } };
  const nodes: Record<string, unknown> = { "film-leader": drawing, "stage-leader": stage, video };
  const document = {
    getElementById: (id: string) => nodes[id] ?? null,
    querySelector: (selector: string) => (selector === ".loading-state" ? library : null),
  };
  new Function("document", source)(document);
  const fire = (type: string) => video.dispatchEvent(new Event(type));
  return { stage, placed, fire };
}

test("one drawing fills both waits: the library loading and the stage", () => {
  expect(boot().placed.sort()).toEqual(["library", "stage"]);
});

test("the stage shows the leader from a source starting to load until its first frame", () => {
  const { stage, fire } = boot();
  expect(stage.hidden).toBe(true);
  fire("loadstart");
  expect(stage.hidden).toBe(false);
  fire("loadeddata");
  expect(stage.hidden).toBe(true);
  // A stall once a picture is up is not an opening: no buffering spinner.
  fire("waiting");
  fire("stalled");
  expect(stage.hidden).toBe(true);
});

test("a source dropped or failed before its first frame takes the leader down", () => {
  for (const ending of ["emptied", "error"]) {
    const { stage, fire } = boot();
    fire("loadstart");
    fire(ending);
    expect(stage.hidden, ending).toBe(true);
  }
});
