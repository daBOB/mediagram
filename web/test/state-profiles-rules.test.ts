/** Covers what `profile-rules.json` cannot: the rule never throws, whatever it is asked. */

import { describe, expect, test } from "bun:test";
import { allowed, ownerOf, type Action } from "../src/state/profiles-rules";

describe("the rule", () => {
  test("refuses an action it does not know, rather than throwing", () => {
    const profiles = [{ id: "admin", kids: false, admin: true, parentId: null }];
    expect(allowed(profiles, "admin", "rename" as Action, "admin")).toBe(false);
  });

  test("an empty household allows nothing and owns nothing", () => {
    expect(allowed([], "anyone", "create-kid", "")).toBe(false);
    expect(ownerOf([], { id: "kid", kids: true, admin: false, parentId: null })).toBeNull();
  });
});
