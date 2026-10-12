import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";

const read = (path: string) => readFileSync(new URL(`../public/${path}`, import.meta.url), "utf8");
const source = read("lib/film-leader.js");

/** Runs the boot script against the few nodes it touches, as index.html lays them out. */
function boot() {
  const video = new EventTarget();
  const placed: string[] = [];
  const stage = { hidden: true, prepend: () => placed.push("stage") };
  const status = { textContent: "" };
  const library = { prepend: () => placed.push("library") };
  const drawing = { content: { firstElementChild: { cloneNode: () => ({}) } } };
  const nodes: Record<string, unknown> = { "film-leader": drawing, "stage-leader": stage, "stage-status": status, video };
  const document = {
    getElementById: (id: string) => nodes[id] ?? null,
    querySelector: (selector: string) => (selector === ".loading-state" ? library : null),
  };
  new Function("document", source)(document);
  const fire = (type: string) => video.dispatchEvent(new Event(type));
  /** The drawing's visibility and the words a screen reader is given, together. */
  const shown = () => [!stage.hidden, status.textContent];
  return { placed, fire, shown };
}

const UP = [true, "Loading…"];
const DOWN = [false, ""];

test("one drawing fills both waits: the library loading and the stage", () => {
  expect(boot().placed.sort()).toEqual(["library", "stage"]);
});

test("the stage shows the leader and says so from a source starting to load until its first frame", () => {
  const { fire, shown } = boot();
  expect(shown()).toEqual(DOWN);
  fire("loadstart");
  expect(shown()).toEqual(UP);
  fire("loadeddata");
  expect(shown()).toEqual(DOWN);
  // A stall once a picture is up is not an opening: no buffering spinner.
  fire("waiting");
  fire("stalled");
  expect(shown()).toEqual(DOWN);
});

test("the next title opens with the leader again once the last one's source is dropped", () => {
  const { fire, shown } = boot();
  fire("loadstart");
  fire("loadeddata");
  expect(shown()).toEqual(DOWN);
  fire("emptied");
  expect(shown()).toEqual(DOWN);
  fire("loadstart");
  expect(shown()).toEqual(UP);
});

test("a source dropped or failed before its first frame takes the leader down", () => {
  for (const ending of ["emptied", "error"]) {
    const { fire, shown } = boot();
    fire("loadstart");
    fire(ending);
    expect(shown(), ending).toEqual(DOWN);
  }
});

test("the leader's geometry and timing stay as the Android leader copies them", () => {
  const html = read("index.html");
  const css = read("styles/film-leader.css");
  expect(html).toContain('<svg class="film-leader" viewBox="0 0 96 96" width="96" height="96"');
  expect(html).toContain('<circle class="leader-sweep" cx="48" cy="48" r="19" stroke-width="38" transform="rotate(-90 48 48)">');
  expect(html).toContain('<circle class="leader-ring" cx="48" cy="48" r="46" stroke-width="1.5">');
  expect(html).toContain('<circle class="leader-ring" cx="48" cy="48" r="38" stroke-width="1">');
  // 2π × 19: empty at rest, a full turn as the dash reaches it.
  expect(css).toContain("stroke-dasharray: 119.381 119.381;");
  expect(css).toContain("stroke-dashoffset: 119.381;");
  expect(css).toContain("animation: leader-sweep 1s linear infinite;");
  expect(css).toContain("animation: leader-count 3s step-end infinite;");
  // 3 starts the cycle (no delay); 2 and 1 are offset into it.
  expect([...css.matchAll(/animation-delay: ([^;]+);/g)].map((match) => match[1])).toEqual(["-2s", "-1s"]);
  expect(css).toContain(".leader-count:nth-of-type(2) { animation-delay: -2s; }");
  expect(css).toContain(".leader-count:nth-of-type(3) { animation-delay: -1s; }");
});

test("still, the leader shows the 3 with the 2 and the 1 at opacity 0", () => {
  const html = read("index.html");
  const css = read("styles/film-leader.css");
  expect([...html.matchAll(/<text class="leader-count" x="48" y="64">(\d)<\/text>/g)].map((match) => match[1]))
    .toEqual(["3", "2", "1"]);
  // Every numeral after the first is hidden unless an animation shows it.
  expect(css).toContain(".leader-count + .leader-count { opacity: 0; }");
  const base = css.match(/\.leader-count \{([^}]*)\}/)![1]!;
  expect(base).not.toContain("opacity");
});
