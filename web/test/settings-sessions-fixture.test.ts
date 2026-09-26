/**
 * Runs the shared fixture `crates/mediagram-core` also reads against this
 * app's port of the same shaping. A case that only passes after a change
 * here does not belong in the fixture — see `shared_authorizations_fixture.rs`.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { shape, type RawAuthorization, type SessionSummary } from "../src/settings/sessions";

const fixture = JSON.parse(
  readFileSync(join(import.meta.dir, "fixtures", "authorizations", "cases.json"), "utf8"),
) as { apiId: number; authorizations: RawAuthorization[]; expect: SessionSummary[] };

describe("authorizations fixture", () => {
  test("shape filters to this app's sessions plus the current one, newest active first", () => {
    expect(shape(fixture.authorizations, fixture.apiId)).toEqual(fixture.expect);
  });
});
