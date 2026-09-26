/**
 * Opaque handles standing in for values the browser must never see.
 *
 * A library's access hash is bound to the account; handing it to the browser
 * would mean trusting a caller not to replay it against a different session,
 * and there is no reason to accept that risk when a random id costs nothing.
 * Rebuilt on every listing, so a handle is only ever valid for as long as the
 * list it came from would still be current — a channel that vanished between
 * listing and choosing simply is not found.
 */

import { randomBytes } from "node:crypto";

export class HandleMap<T> {
  private map = new Map<string, T>();

  /** Replaces the whole map with a fresh listing, discarding every old handle. */
  reset(values: T[]): { handle: string; value: T }[] {
    this.map = new Map();
    return values.map((value) => {
      const handle = randomBytes(16).toString("base64url");
      this.map.set(handle, value);
      return { handle, value };
    });
  }

  get(handle: string): T | undefined {
    return this.map.get(handle);
  }
}
