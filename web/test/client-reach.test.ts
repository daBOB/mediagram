/**
 * Telling a viewer on the sofa from a viewer on the internet.
 *
 * The distinction decides whether a title may be played as it is. A 13.9
 * Mbit/s film is nothing on a LAN and does not fit a household uplink, so
 * offering it whole to a remote viewer is offering a stall.
 *
 * Behind a proxy every request arrives from loopback, so the forwarded
 * address has to be read — but only where a proxy is actually in front, or a
 * header anyone can set decides what the server believes.
 */

import { describe, expect, test } from "bun:test";

import { clientAddress, isLocalAddress } from "../src/client-reach";

describe("recognising a local address", () => {
  test("loopback is local", () => {
    expect(isLocalAddress("127.0.0.1")).toBe(true);
    expect(isLocalAddress("::1")).toBe(true);
    expect(isLocalAddress("::ffff:127.0.0.1")).toBe(true);
  });

  test("the private ranges are local", () => {
    for (const ip of ["192.168.0.118", "10.4.4.4", "172.16.0.1", "172.31.255.254"]) {
      expect(isLocalAddress(ip)).toBe(true);
    }
  });

  test("addresses that only look private are not", () => {
    // 172.32 is outside the /12, and 10.x written as a hostname is not an
    // address at all.
    for (const ip of ["172.32.0.1", "8.8.8.8", "203.0.113.9", "not-an-address"]) {
      expect(isLocalAddress(ip)).toBe(false);
    }
  });

  test("link-local and unique-local IPv6 count as local", () => {
    expect(isLocalAddress("fe80::1")).toBe(true);
    expect(isLocalAddress("fd00::1")).toBe(true);
    expect(isLocalAddress("2001:db8::1")).toBe(false);
  });

  test("carrier-grade NAT is not a home network", () => {
    // 100.64/10 is the ISP's, not the household's: a viewer there is remote.
    expect(isLocalAddress("100.64.0.1")).toBe(false);
  });
});

describe("the address to judge a request by", () => {
  test("without a trusted proxy, the socket's address is the answer", () => {
    const address = clientAddress("203.0.113.9", "192.168.0.5", false);

    expect(address).toBe("203.0.113.9");
  });

  test("a forwarded header is ignored unless a proxy is trusted", () => {
    // Otherwise anyone can claim to be on the LAN and ask for direct play.
    expect(clientAddress("203.0.113.9", "127.0.0.1", false)).toBe("203.0.113.9");
  });

  /**
   * The last entry, not the first. A proxy that replaces the header writes one
   * entry, so both readings agree. A proxy that *appends* — Cloudflare does —
   * leaves whatever the caller sent in front of the address it observed, and
   * reading the first entry would believe the caller.
   */
  test("with a trusted proxy, the address the proxy itself observed wins", () => {
    expect(clientAddress("127.0.0.1", "203.0.113.9", true)).toBe("203.0.113.9");
    expect(clientAddress("127.0.0.1", "10.0.0.1, 203.0.113.9", true)).toBe("203.0.113.9");
  });

  test("a caller cannot claim the local network by sending a chain", () => {
    // What a remote viewer would send to be offered the original file.
    const spoofed = clientAddress("127.0.0.1", "192.168.0.10, 203.0.113.9", true);

    expect(isLocalAddress(spoofed)).toBe(false);
  });

  test("a trusted proxy that forwards nothing falls back to the socket", () => {
    expect(clientAddress("127.0.0.1", null, true)).toBe("127.0.0.1");
    expect(clientAddress("127.0.0.1", "   ", true)).toBe("127.0.0.1");
  });

  test("a request with no address at all is treated as remote", () => {
    expect(isLocalAddress(clientAddress(null, null, false))).toBe(false);
  });
});
