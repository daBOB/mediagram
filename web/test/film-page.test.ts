/**
 * Covers `film-page.js`'s `details()`: the facts a viewer reads when a film
 * will not play. `alang`/`slang` arrive as JSON array strings, the way the
 * index stores them — `languages()` used to require an actual array and
 * silently dropped both rows for every title.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { details } from "../public/lib/catalog/film-page.js";
import { browserEnvironment } from "./support/player-environment";
import { descendants, textOf } from "./support/browser-application";
import { catalogSet } from "./support/catalog-set";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(() => { env = browserEnvironment(); });
afterEach(() => { env.restore(); });

function rows(set: Parameters<typeof details>[0]) {
  const dl = details(set);
  const facts = descendants(dl).filter((node) => node.tagName === "DIV" && node.className === "fact");
  return Object.fromEntries(facts.map((fact) => [
    fact.children.find((c) => c.tagName === "DT")!.textContent,
    textOf(fact.children.find((c) => c.tagName === "DD")!),
  ]));
}

describe("audio and subtitle languages", () => {
  test("read from the file's own JSON array columns", () => {
    const set = catalogSet({ alang: JSON.stringify(["deu", "eng"]), slang: JSON.stringify(["deu"]) });
    const found = rows(set);
    expect(found["Audio languages"]).toBe("German, English");
    expect(found["Subtitles"]).toBe("German");
  });

  test("neither row is shown when the file carries nothing", () => {
    const set = catalogSet({ alang: null, slang: null });
    const found = rows(set);
    expect(found["Audio languages"]).toBeUndefined();
    expect(found["Subtitles"]).toBeUndefined();
  });

  test("a malformed column is no languages rather than a thrown error", () => {
    const set = catalogSet({ alang: "not json", slang: "{}" });
    const found = rows(set);
    expect(found["Audio languages"]).toBeUndefined();
    expect(found["Subtitles"]).toBeUndefined();
  });
});
