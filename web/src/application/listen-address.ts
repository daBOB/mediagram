/**
 * What to print when the player starts listening.
 *
 * Binding to every interface is what makes the player reachable from a phone
 * or a TV, and it is also the moment an unauthenticated library becomes
 * visible to everyone on the network. Both facts belong on screen: the
 * addresses that actually work, and the warning that they are open.
 */

import { networkInterfaces, type NetworkInterfaceInfo } from "node:os";

type Interfaces = NodeJS.Dict<NetworkInterfaceInfo[]>;

/** Addresses that mean "this machine only". */
const LOOPBACK = new Set(["127.0.0.1", "::1", "localhost"]);

/** Addresses that mean "every interface". */
const EVERYWHERE = new Set(["0.0.0.0", "::", ""]);

/** True when something other than this machine can reach the player. */
export function isExposed(hostname: string): boolean {
  return !LOOPBACK.has(hostname);
}

/**
 * The URLs the player can actually be opened at.
 *
 * Binding to every interface means the bind address itself — `0.0.0.0` — is
 * not something anyone can type, so the real addresses are listed instead.
 */
export function reachableUrls(
  hostname: string,
  port: number,
  interfaces: Interfaces = networkInterfaces(),
): string[] {
  if (!EVERYWHERE.has(hostname)) {
    return [`http://${hostname}:${port}`];
  }

  const external: string[] = [];
  for (const entries of Object.values(interfaces)) {
    for (const entry of entries ?? []) {
      if (entry.family === "IPv4" && !entry.internal) {
        external.push(`http://${entry.address}:${port}`);
      }
    }
  }
  // Loopback last: the useful answer is the one a phone can reach, but this
  // machine must still be told how to open it locally.
  return [...external, `http://127.0.0.1:${port}`];
}
