/** The household's Kids marks as the page holds them: each with the age it is for kids from. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(() => { env = browserEnvironment(); });
afterEach(() => env.restore());

test("marks are read with the age each is for kids from", async () => {
  env.respondWith(async () => Response.json({ kids: ["a", "b"], fromSix: ["b"] }));
  await state.loadKids();
  expect(state.kidsAge("a")).toBe(12);
  expect(state.kidsAge("b")).toBe(6);
  expect(state.kidsAge("c")).toBeNull();
  expect([...state.kidsMarks()]).toEqual([["a", 12], ["b", 6]]);
});

test("an answer from before ages reads every mark as from 12", async () => {
  env.respondWith(async () => Response.json({ kids: ["a"] }));
  await state.loadKids();
  expect([...state.kidsMarks()]).toEqual([["a", 12]]);
});

test("marking says the age; unmarking takes the mark off", () => {
  state.setKids("film", 6);
  expect(state.kidsAge("film")).toBe(6);
  expect(env.requests.at(-1)).toMatchObject({ url: "/api/kids/film", options: { method: "PUT", body: JSON.stringify({ age: 6 }) } });
  state.setKids("film", null);
  expect(state.kidsAge("film")).toBeNull();
  expect(env.requests.at(-1)?.options?.method).toBe("DELETE");
});
