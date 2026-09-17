/** Covers `listen-address`: which binds count as exposed, and which URLs are shown. */

import { describe, expect, test } from "bun:test";
import { isExposed, reachableUrls } from "../src/listen-address";

const interfaces = {
  lo: [{ address: "127.0.0.1", family: "IPv4", internal: true }],
  eth0: [{ address: "192.168.1.42", family: "IPv4", internal: false }],
  docker0: [{ address: "172.17.0.1", family: "IPv4", internal: false }],
} as never;

describe("whether the player is exposed", () => {
  test("loopback is not exposed", () => {
    expect(isExposed("127.0.0.1")).toBe(false);
    expect(isExposed("::1")).toBe(false);
    expect(isExposed("localhost")).toBe(false);
  });

  test("binding to every interface is exposed", () => {
    expect(isExposed("0.0.0.0")).toBe(true);
    expect(isExposed("::")).toBe(true);
  });

  test("binding to one real address is still exposed", () => {
    expect(isExposed("192.168.1.42")).toBe(true);
  });
});

describe("the addresses to show", () => {
  test("a loopback bind is reachable only from this machine", () => {
    expect(reachableUrls("127.0.0.1", 8770, interfaces)).toEqual([
      "http://127.0.0.1:8770",
    ]);
  });

  /** The point of the exercise: an address to type into a phone. */
  test("binding to every interface lists the ones on the network", () => {
    const urls = reachableUrls("0.0.0.0", 8770, interfaces);

    expect(urls).toContain("http://192.168.1.42:8770");
    expect(urls).toContain("http://127.0.0.1:8770");
  });

  test("a specific bind shows exactly that address", () => {
    expect(reachableUrls("192.168.1.42", 8770, interfaces)).toEqual([
      "http://192.168.1.42:8770",
    ]);
  });

  test("the port is carried through", () => {
    expect(reachableUrls("127.0.0.1", 9999, interfaces)[0]).toEndWith(":9999");
  });

  /** A machine with no network still has to start and say something useful. */
  test("no external interfaces still yields loopback", () => {
    const only = { lo: [{ address: "127.0.0.1", family: "IPv4", internal: true }] } as never;

    expect(reachableUrls("0.0.0.0", 8770, only)).toEqual(["http://127.0.0.1:8770"]);
  });
});
