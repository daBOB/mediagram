/** The cast ops of `/api/together`, at the router, with a fake TV that answers by editing the offer. */

import { describe, expect, test } from "bun:test";

import { parseSession, writeSession } from "../public/lib/together/session.js";
import { accepted } from "../public/lib/together/cast.js";
import { harness, settle } from "./together-fixture";

const offer = { op: "offer", screen: "tab-a", profile: "kid", set: "film-1", playing: true, pos: 12.5, rate: 1 };
const TV = { screen: "tv-1", label: "TV", remote: false };

describe("/api/together cast ops", () => {
  test("offer answers on once the TV accepts", async () => {
    const h = harness();
    const asked = h.call("POST", offer);
    await settle();
    const id = Math.max(...h.channel.messages.keys());
    const text = writeSession(accepted(parseSession(h.channel.messages.get(id)!)!, TV, Date.now())!);
    h.channel.messages.set(id, text);
    h.hub.heard(id, text);
    const { status, json } = await asked;
    expect(status).toBe(200);
    expect(json).toMatchObject({ answer: "on", note: "", live: { id }, serverNow: expect.any(Number) });
    expect(json.live.session.cast.tv).toBe("tv-1");
  });

  test("offer without Telegram is 503 with the reason", async () => {
    const h = harness();
    h.channel.up = false;
    const { status, json } = await h.call("POST", offer);
    expect(status).toBe(503);
    expect(json).toEqual({ reason: "Telegram isn't reachable" });
  });

  test.each([
    ["no profile", { ...offer, profile: undefined }],
    ["an empty profile", { ...offer, profile: "" }],
    ["a profile over 128 characters", { ...offer, profile: "k".repeat(129) }],
    ["an unknown set", { ...offer, set: "nope" }],
    ["a replace with no id", { ...offer, op: "replace" }],
  ])("offer with %s is 400", async (_, body) => {
    const h = harness();
    expect((await h.call("POST", body)).status).toBe(400);
    expect(h.channel.sent).toHaveLength(0);
  });

  test("join as a remote adds a remote member", async () => {
    const h = harness();
    const { json } = await h.call("POST", { op: "start", screen: "tab-a", set: "film-1", playing: true, pos: 1, rate: 1 });
    const joined = await h.call("POST", { op: "join", screen: "tab-b", id: json.id, remote: true });
    expect(joined.json.session.members.find((m: { screen: string }) => m.screen === "tab-b").remote).toBe(true);
  });

  test("tracks with neither field is 400, and with an unknown session 404", async () => {
    const h = harness();
    expect((await h.call("POST", { op: "tracks", screen: "tab-a", id: 901 })).status).toBe(400);
    expect((await h.call("POST", { op: "tracks", screen: "tab-a", id: 901, audio: "x".repeat(65) })).status).toBe(400);
    expect((await h.call("POST", { op: "tracks", screen: "tab-a", id: 901, audio: "a1" })).status).toBe(404);
  });

  test("cancel is 204 and the message is gone", async () => {
    const h = harness();
    const { json } = await h.call("POST", { op: "start", screen: "tab-a", set: "film-1", playing: true, pos: 1, rate: 1 });
    expect((await h.call("POST", { op: "cancel", screen: "tab-a", id: json.id })).status).toBe(204);
    await settle();
    expect(h.channel.removed).toEqual([json.id]);
    expect(h.channel.messages.has(json.id)).toBe(false);
  });
});
