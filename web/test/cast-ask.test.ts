import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment, type Node } from "./support/player-environment";
import { descendants } from "./support/browser-application";
import { askTv } from "../public/lib/cast/cast-ask.js";
import { CAST_ANSWER_MS } from "../public/lib/together/cast.js";
import { CAST_WORDS, busyQuestion, refusedLine } from "../public/lib/together/cast-words.js";
import { UNREACHABLE, screen, type Live } from "../public/lib/together-now.js";

let env: ReturnType<typeof browserEnvironment>;
let sent: Array<Record<string, unknown>>;
const own = { set: "film-1", playing: true, pos: 12, rate: 1 };
const live = { id: 7, session: { session: "s-1", set: "film-1" } } as unknown as Live;

const dialog = () => env.document.getElementById("cast-ask") as unknown as Node;
const status = () => (env.document.getElementById("cast-ask-status") as unknown as Node).textContent;
const buttons = () => descendants(env.document.getElementById("cast-ask-actions") as unknown as Node).filter((n) => n.tagName === "BUTTON");
const press = (label: string) => buttons().find((b) => b.textContent === label)!.fire("click");
const tick = async () => { for (let i = 0; i < 20; i++) await Promise.resolve(); };

/** Answers each post in turn with the next reply, remembering what was sent. */
function answers(...replies: Array<{ status?: number; body?: unknown }>) {
  env.respondWith(async (_url, options) => {
    sent.push(JSON.parse(String(options?.body)));
    const next = replies.shift() ?? { status: 204 };
    return next.body === undefined ? new Response(null, { status: next.status ?? 204 }) : Response.json(next.body, { status: next.status ?? 200 });
  });
}

beforeEach(() => { env = browserEnvironment(); sent = []; });
afterEach(() => env.restore());

test("on resolves the live session and closes the dialog", async () => {
  answers({ body: { answer: "on", live, note: "", serverNow: 0 } });
  const asked = askTv(own, "mia");
  expect(dialog().open).toBe(true);
  expect(status()).toBe(CAST_WORDS.asking);
  expect(await asked).toEqual(live);
  expect(dialog().open).toBe(false);
  expect(sent).toEqual([{ op: "offer", screen, profile: "mia", ...own }]);
});

test("busy asks to replace, and Replace sends replace and resolves on", async () => {
  answers({ body: { answer: "busy", live, note: "Dune (2021)", serverNow: 0 } }, { body: { answer: "on", live, note: "", serverNow: 0 } });
  const asked = askTv(own, "mia");
  await tick();
  expect(status()).toBe(busyQuestion("Dune (2021)"));
  press(CAST_WORDS.replace);
  expect(await asked).toEqual(live);
  expect(sent[1]).toEqual({ op: "replace", screen, profile: "mia", ...own, id: 7 });
  expect(dialog().open).toBe(false);
});

test("busy then Cancel sends cancel and resolves null", async () => {
  answers({ body: { answer: "busy", live, note: "Dune (2021)", serverNow: 0 } }, {});
  const asked = askTv(own, "mia");
  await tick();
  press(CAST_WORDS.cancel);
  expect(await asked).toBeNull();
  expect(sent[1]).toEqual({ op: "cancel", id: 7 });
  expect(dialog().open).toBe(false);
});

test("refused shows the reason and resolves null", async () => {
  answers({ body: { answer: "refused", live, note: "it's rated above Mia's age limit", serverNow: 0 } });
  const asked = askTv(own, "mia");
  await tick();
  expect(status()).toBe(refusedLine("it's rated above Mia's age limit"));
  press("OK");
  expect(await asked).toBeNull();
  expect(dialog().open).toBe(false);
});

test("a second ask while one is open sends nothing and shares the first's answer", async () => {
  answers({ body: { answer: "on", live, note: "", serverNow: 0 } });
  const first = askTv(own, "mia");
  const second = askTv(own, "mia");
  expect(second).toBe(first);
  expect(await first).toEqual(live);
  expect(sent).toHaveLength(1);
  answers({ body: { answer: "on", live, note: "", serverNow: 0 } });
  expect(await askTv(own, "mia")).toEqual(live);
});

test("a request that never answers ends in no answer after the server's wait and a margin", async () => {
  env.respondWith(() => new Promise<Response>(() => {}));
  const asked = askTv(own, "mia");
  env.advance(CAST_ANSWER_MS + 2_999);
  await tick();
  expect(status()).toBe(CAST_WORDS.asking);
  env.advance(1);
  await tick();
  expect(status()).toBe(CAST_WORDS.noAnswer);
  press("OK");
  expect(await asked).toBeNull();
  expect(dialog().open).toBe(false);
});

test("an answer without its session says Telegram isn't reachable, and closes", async () => {
  answers({ body: { answer: "busy", live: null, note: "Dune (2021)", serverNow: 0 } });
  const asked = askTv(own, "mia");
  await tick();
  expect(status()).toBe(UNREACHABLE);
  press("OK");
  expect(await asked).toBeNull();
  expect(dialog().open).toBe(false);
});

test("no answer says Mediagram isn't open", async () => {
  answers({ body: { answer: "none", live: null, note: "", serverNow: 0 } });
  const asked = askTv(own, "mia");
  await tick();
  expect(status()).toBe(CAST_WORDS.noAnswer);
  press("OK");
  expect(await asked).toBeNull();
});

test("unreachable says Telegram isn't reachable", async () => {
  answers({ status: 503, body: { reason: "Telegram isn't reachable" } });
  const asked = askTv(own, "mia");
  await tick();
  expect(status()).toBe("Telegram isn't reachable");
  press("OK");
  expect(await asked).toBeNull();
});

test("the first button takes focus once there are buttons, and none is focused while only the TV is being asked", async () => {
  answers({ body: { answer: "busy", live, note: "Dune (2021)", serverNow: 0 } }, {});
  const asked = askTv(own, "mia");
  expect(env.document.activeElement).toBeNull();
  await tick();
  expect(env.document.activeElement).toBe(buttons()[0] as never);
  press(CAST_WORDS.cancel);
  await asked;
});
