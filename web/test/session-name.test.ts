import { describe, expect, test } from "bun:test";

import manifest from "../package.json";
import { deviceModel, sessionName } from "../src/telegram/session-name";

describe("session name", () => {
  test("names the surface and the device, as the Rust clients do", () => {
    expect(deviceModel("web", "homelab")).toBe("mediagram web · homelab");
  });

  test("a device with no name still says what it is", () => {
    expect(deviceModel("web", "  ")).toBe("mediagram web");
  });

  test("carries the project's version, not the client library's", () => {
    expect(sessionName().appVersion).toBe(manifest.version);
    expect(sessionName().deviceModel.startsWith("mediagram web")).toBe(true);
  });
});
