/** The owner's PIN on the Households part once it stops being the owner's: changed elsewhere while it was held. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { HOUSEHOLD_WORDS as W } from "../public/lib/household-words.js";
import { refusalText } from "../public/lib/pin-prompt.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, byClass, textOf } from "./support/browser-application";
import { AT_HOME, FLAT, storage, writesOf } from "./support/household-fixture";
import { ATTIC, EVERYONE, fill, OWNER, part, press, prompting, promptTitle, rowOf } from "./support/households-part";
import { browserEnvironment } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let refusal: { status: number; body: object } | null;
beforeEach(async () => {
  env = browserEnvironment();
  refusal = null;
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "home" }) });
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, heard: true, profiles: EVERYONE, households: [ATTIC, FLAT, AT_HOME] });
    if (refusal && url.startsWith("/api/households")) return Response.json(refusal.body, { status: refusal.status });
    return new Response(null, { status: 204 });
  });
  await state.loadProfiles();
});
afterEach(() => env.restore());

test("a held PIN the server calls wrong is dropped, and the next change asks the owner's PIN again", async () => {
  const { root } = part(OWNER);
  await press(root, "Attic", W.delete);
  await answerPin(root, "1357");
  expect(writesOf(env.requests).at(-1)).toEqual(["DELETE", "/api/households/attic", { actorId: "andre", pin: "1357" }]);

  // The owner's PIN was changed on another device meanwhile.
  refusal = { status: 403, body: { reason: "wrong-pin" } };
  await fill(root, "Flat", W.rename, "The flat");
  expect(prompting(root)).toBe(false);
  expect(textOf(byClass(rowOf(root, "Flat"), "pin-message"))).toBe(refusalText({ reason: "wrong-pin" }));

  refusal = null;
  await press(root, "Attic", W.delete);
  expect(promptTitle(root)).toBe(W.ownersPin);
  await answerPin(root, "2580");
  expect(writesOf(env.requests).slice(-2)).toEqual([
    ["POST", "/api/profiles/andre/unlock", { pin: "2580" }],
    ["DELETE", "/api/households/attic", { actorId: "andre", pin: "2580" }],
  ]);
});
