/** The Report sheet: a reason before Send, one report per title, a failure that keeps the sheet, Esc that keeps the film. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { mediaError, mountPlayerReport } from "../public/lib/playback/player-report.js";
import { REPORT_REASONS, REPORT_WORDS } from "../public/lib/report/report-text.js";
import { browserEnvironment, deferred, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(() => {
  env = browserEnvironment();
});
afterEach(async () => {
  await settle();
  env.restore();
});

const film = { setId: "film" };
const choices = () => env.node("report-reasons").children.map((label) => label.children[0]!);

function mount() {
  const facts = { title: "Hot Shots! (1991)", position: 754, mode: "transcode", held: true };
  const report = mountPlayerReport({ dialog: env.node("player"), facts: () => facts });
  report.open(film);
  env.node("report").fire("click");
  return report;
}

test("the reasons are the shared words, and Send waits for one to be picked", () => {
  mount();
  expect(env.node("report").textContent).toBe(REPORT_WORDS.button);
  expect(env.node("report-panel").hidden).toBe(false);
  expect(env.node("report-reasons").children.map((label) => label.children[1]!.textContent)).toEqual(REPORT_REASONS.map((r) => r.words));
  expect(env.node("report-send").disabled).toBe(true);
  choices()[1]!.fire("change");
  expect(env.node("report-send").disabled).toBe(false);
});

test("a sent report closes the sheet and the button reads as done until the next title", async () => {
  const report = mount();
  report.failed("MEDIA_ERR_DECODE: " + "x".repeat(400));
  env.respondWith(async () => new Response(null, { status: 204 }));
  choices()[0]!.fire("change");
  (env.node("report-note") as unknown as { value: string }).value = "stops after the intro";
  env.node("report-send").fire("click");
  await settle();
  const body = JSON.parse(String(env.requests[0]!.options!.body));
  expect(env.requests[0]!.url).toBe("/api/report");
  expect(body).toMatchObject({ set: "film", reason: "wont-play", title: "Hot Shots! (1991)", note: "stops after the intro", position: 754, played: "converted", held: true });
  expect(body.error).toHaveLength(300);
  expect(env.node("report-panel").hidden).toBe(true);
  expect(env.node("report").textContent).toBe(REPORT_WORDS.sent);
  expect(env.node("report").disabled).toBe(true);
  report.open({ setId: "next" });
  expect(env.node("report").textContent).toBe(REPORT_WORDS.button);
  expect(env.node("report").disabled).toBe(false);
});

test("a send that fails says so and leaves the sheet open to try again", async () => {
  mount();
  env.respondWith(async () => new Response(null, { status: 503 }));
  choices()[2]!.fire("change");
  env.node("report-send").fire("click");
  await settle();
  expect(env.node("report-said").textContent).toBe(REPORT_WORDS.failed);
  expect(env.node("report-panel").hidden).toBe(false);
  expect(env.node("report-send").disabled).toBe(false);
});

test("a second press while a send is in flight sends nothing, and Report stays shut until it settles", async () => {
  mount();
  const answer = deferred<Response>();
  env.respondWith(() => answer.promise);
  choices()[0]!.fire("change");
  env.node("report-send").fire("click");
  await settle();
  choices()[1]!.fire("change");
  expect(env.node("report-send").disabled).toBe(true);
  expect(env.node("report").disabled).toBe(true);
  env.node("report-send").fire("click");
  await settle();
  expect(env.requests).toHaveLength(1);
  answer.resolve(new Response(null, { status: 204 }));
  await settle();
  expect(env.node("report").textContent).toBe(REPORT_WORDS.sent);
  expect(env.requests).toHaveLength(1);
});

test("an answer for an earlier open marks nothing, even when the same title opened again", async () => {
  const report = mount();
  const answer = deferred<Response>();
  env.respondWith(() => answer.promise);
  choices()[0]!.fire("change");
  env.node("report-send").fire("click");
  await settle();
  report.open(film);
  answer.resolve(new Response(null, { status: 204 }));
  await settle();
  expect(env.node("report").textContent).toBe(REPORT_WORDS.button);
  expect(env.node("report").disabled).toBe(false);
  env.node("report").fire("click");
  choices()[0]!.fire("change");
  expect(env.node("report-send").disabled).toBe(false);
});

test("Esc closes the sheet and takes the key, so the player stays open", () => {
  mount();
  const event = Object.assign(new Event("keydown", { cancelable: true }), { key: "Escape" });
  env.node("player").dispatchEvent(event);
  expect(event.defaultPrevented).toBe(true);
  expect(env.node("report-panel").hidden).toBe(true);
  expect(env.requests).toHaveLength(0);
});

test("a media error is named by its code, with the browser's message when there is one", () => {
  expect(mediaError({ code: 3, message: "PIPELINE_ERROR_DECODE" })).toBe("MEDIA_ERR_DECODE: PIPELINE_ERROR_DECODE");
  expect(mediaError({ code: 4, message: "" })).toBe("MEDIA_ERR_SRC_NOT_SUPPORTED");
});
