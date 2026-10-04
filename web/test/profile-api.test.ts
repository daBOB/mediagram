/** The client for the profile routes: each call's request, its outcome, and what each grown-up may manage. */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import * as api from "../public/lib/profile-api.js";
import * as state from "../public/lib/watch-state.js";
import { ANDRE, LEA, MAJA, TIM, writesOf } from "./support/household-fixture";
import { browserEnvironment } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let household: object[];
beforeEach(async () => {
  env = browserEnvironment();
  household = [ANDRE, MAJA, TIM, LEA];
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, profiles: household });
    if (url.endsWith("/state")) return Response.json({});
    return new Response(null, { status: 204 });
  });
  await state.loadProfiles();
});
afterEach(async () => { await state.useProfile(null); env.restore(); });

const sent = () => writesOf(env.requests);

const CALLS = [
  { name: "unlock", call: () => api.unlock("andre", "1234"), expected: ["POST", "/api/profiles/andre/unlock", { pin: "1234" }] },
  { name: "claimAdmin", call: () => api.claimAdmin("maja", "4321"), expected: ["POST", "/api/profiles/maja/claim-admin", { pin: "4321" }] },
  { name: "setPin", call: () => api.setPin("andre", "1234", "maja", "5555"), expected: ["PUT", "/api/profiles/maja/pin", { actorId: "andre", pin: "1234", newPin: "5555" }] },
  { name: "create a kid", call: () => api.create("maja", "4321", { name: "Ben", kids: true, kidsAge: 6 }), expected: ["POST", "/api/profiles", { actorId: "maja", pin: "4321", name: "Ben", kids: true, kidsAge: 6 }] },
  { name: "create a grown-up", call: () => api.create("andre", "1234", { name: "Oma", kids: false, newPin: "2468" }), expected: ["POST", "/api/profiles", { actorId: "andre", pin: "1234", name: "Oma", kids: false, newPin: "2468" }] },
  { name: "createFirst, with no actor", call: () => api.createFirst("andre", "1234"), expected: ["POST", "/api/profiles", { name: "andre", newPin: "1234" }] },
  { name: "remove", call: () => api.remove("maja", "4321", "tim"), expected: ["DELETE", "/api/profiles/tim", { actorId: "maja", pin: "4321" }] },
  { name: "setKidsAge", call: () => api.setKidsAge("maja", "4321", "tim", 12), expected: ["PUT", "/api/profiles/tim/kids-age", { actorId: "maja", pin: "4321", age: 12 }] },
  { name: "prove, with a PIN", call: () => api.prove(ANDRE, "1234"), expected: ["POST", "/api/profiles/andre/unlock", { pin: "1234" }] },
  { name: "prove, with none yet", call: () => api.prove({ ...MAJA, hasPin: false }, "4321"), expected: ["PUT", "/api/profiles/maja/pin", { actorId: "maja", pin: "", newPin: "4321" }] },
];

describe("each call is the server's route, the PIN in the body", () => {
  for (const { name, call, expected } of CALLS) {
    test(name, async () => {
      expect(await call()).toEqual({ ok: true });
      expect(sent()).toEqual([expected]);
    });
  }

  test("every call declares JSON, a removal included, so the server's write check lets it through", async () => {
    await api.remove("maja", "4321", "tim");
    const removal = env.requests.find((request) => request.options?.method === "DELETE")!;
    expect(removal.options!.headers).toEqual({ "content-type": "application/json" });
  });
});

describe("a refusal says why", () => {
  test("the server's reason, and a wait's seconds", async () => {
    env.respondWith(async () => Response.json({ reason: "wait", retryAfter: 42 }, { status: 429, headers: { "retry-after": "42" } }));
    expect(await api.unlock("andre", "0000")).toEqual({ ok: false, reason: "wait", retryAfter: 42 });
    env.respondWith(async () => Response.json({ reason: "wrong-pin" }, { status: 403 }));
    expect(await api.unlock("andre", "0000")).toEqual({ ok: false, reason: "wrong-pin" });
    env.respondWith(async () => Response.json({ reason: "name-taken" }, { status: 409 }));
    expect(await api.create("maja", "4321", { name: "andre", kids: true, kidsAge: 6 })).toEqual({ ok: false, reason: "name-taken" });
  });

  const unnamed = [
    ["a network failure", async () => { throw new Error("offline"); }],
    ["a bodiless refusal", async () => new Response(null, { status: 403 })],
    ["a reason nobody named", async () => Response.json({ reason: "nope" }, { status: 400 })],
  ] as const;
  for (const [name, answer] of unnamed) {
    test(`${name} has no reason, and changes nothing here`, async () => {
      env.respondWith(answer);
      expect((await api.remove("andre", "1234", "maja")).reason).toBeNull();
      expect(state.profiles()).toHaveLength(4);
    });
  }
});

describe("after a change", () => {
  test("the profile list is read again", async () => {
    household = [ANDRE, MAJA, LEA];
    await api.remove("maja", "4321", "tim");
    expect(state.profiles().map((entry) => entry.id)).toEqual(["andre", "maja", "lea"]);
  });

  test("a device on a profile that went with its parent lets go of it", async () => {
    await state.useProfile("tim");
    household = [ANDRE, LEA];
    await api.remove("andre", "1234", "maja");
    expect(state.profileId()).toBeNull();
  });

  test("unlocking changes nothing, so nothing is read again", async () => {
    await api.unlock("andre", "1234");
    expect(env.requests.filter((request) => request.url === "/api/profiles" && !request.options?.method)).toHaveLength(1);
  });
});

describe("what each grown-up may manage", () => {
  test("the admin: every other grown-up, and the kids that are its own — a kid with no parent included", () => {
    const view = api.manageable([ANDRE, MAJA, TIM, LEA], "andre");
    expect(view.grownUps.map((entry: { id: string }) => entry.id)).toEqual(["maja"]);
    expect(view.kids.map((entry: { id: string }) => entry.id)).toEqual(["lea"]);
  });

  test("a parent: its own kids, no grown-ups", () => {
    const view = api.manageable([ANDRE, MAJA, TIM, LEA], "maja");
    expect(view.grownUps).toEqual([]);
    expect(view.kids.map((entry: { id: string }) => entry.id)).toEqual(["tim"]);
  });

  test("a kid, or nobody, manages nothing", () => {
    expect(api.manageable([ANDRE, MAJA, TIM, LEA], "tim").actor).toBeNull();
    expect(api.manageable([ANDRE, MAJA, TIM, LEA], "gone").actor).toBeNull();
  });

  test("a kid whose parent is not here belongs to the admin", () => {
    const ida = { ...TIM, id: "ida", parentId: "removed-elsewhere" };
    expect(api.ownerOf([ANDRE, MAJA, ida], ida)).toBe("andre");
  });
});

/** Whether the panel offers `action` on `targetId` — which the server's rule must agree with. */
function offered(profiles: object[], actorId: string, action: string, targetId: string | null) {
  const { actor, grownUps, kids } = api.manageable(profiles, actorId);
  const has = (list: Array<{ id: string }>) => list.some((entry) => entry.id === targetId);
  if (!actor) return false;
  if (action === "create-grown-up") return actor.admin === true;
  if (action === "create-kid") return true;
  if (action === "remove") return has(grownUps) || has(kids);
  if (action === "set-pin") return targetId === actorId || has(grownUps);
  if (action === "set-kids-age") return has(kids);
  throw new Error(`Unknown action ${action}`);
}

const RULES = JSON.parse(readFileSync(join(import.meta.dir, "fixtures", "watch-state", "profile-rules.json"), "utf8")) as
  Array<{ name: string; profiles: object[]; actorId: string; action: string; targetId: string | null; expect: boolean }>;

describe("the panel offers what the server's rule allows", () => {
  for (const rule of RULES) {
    test(rule.name, () => expect(offered(rule.profiles, rule.actorId, rule.action, rule.targetId ?? null)).toBe(rule.expect));
  }
});
