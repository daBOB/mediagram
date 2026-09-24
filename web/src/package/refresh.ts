/**
 * The reader algorithm: pointer to catalog, or the catalog already held.
 *
 * Every refusal ends the same way — the player keeps what it had. A stale
 * catalog is useful; a forged one is not, and there is no third option worth
 * having.
 *
 * The rule this file exists to enforce is the freshness one. `sha256` is not
 * authenticated, so a host that wants to suppress an update can set it to the
 * digest of the copy the reader is holding; a reader that skips on that match
 * never runs the cipher and never notices. Whether a package is already held
 * is therefore decided from the five fields the tag covers, recorded only
 * after a successful decrypt.
 *
 * Normative description: `docs/mlib-package-v1.md` §5.
 */

import { createHash } from "node:crypto";
import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { failureMessage } from "../failure-message";

import { openPackage } from "./open";
import {
  MAX_PACKAGE_BYTES,
  associatedData,
  parsePointer,
  pointerReadabilityRefusal,
  type Pointer,
} from "./pointer";
import { unpackTo } from "./unpack";
import { CURRENT, FUTURE_TOLERANCE_SECONDS, availableVersionName, cleanupCatalogDirectory, removeOtherVersions, swapCurrent } from "./catalog-versions";

export type { Pointer };

/** Written into a version directory so identity and catalog cannot disagree. */
const IDENTITY_FILE = "identity.json";
const MANIFEST_FILE = "manifest.json";

/** The five fields the cipher authenticates. What "already held" means. */
export interface Identity {
  format: number;
  created_at: number;
  key_id: string;
  schema: number;
  spec: number;
}

export interface RefreshOptions {
  /** Where `latest.json` lives. */
  baseUrl: string;
  key: Buffer;
  /** Directory the reader owns: versions and the `current` link live here. */
  root: string;
  supportedSchema: number[];
  now?: () => number;
  fetch?: (url: string) => Promise<Response>;
}

export interface RefreshResult {
  /** `updated` if a new catalog was unpacked, `unchanged` if the held one is
   *  already the one offered, `kept` if the refresh was refused. */
  status: "updated" | "unchanged" | "kept";
  /** The directory to open, or `null` when there is no catalog at all. */
  dir: string | null;
  /** Why, when the answer is `kept`. */
  reason?: string;
  /**
   * What the catalog being opened actually is, or `null` when there is none.
   *
   * Deliberately the identity of the catalog in **use**, not of the pointer
   * just read: after a `kept` those are different, and the one worth
   * reporting is the one the queries will run against.
   */
  identity: Identity | null;
}

function identityOf(pointer: Pointer): Identity {
  return {
    format: pointer.format,
    created_at: pointer.created_at,
    key_id: pointer.key_id,
    schema: pointer.schema,
    spec: pointer.spec,
  };
}

function sameIdentity(a: Identity, b: Identity): boolean {
  return (
    a.format === b.format &&
    a.created_at === b.created_at &&
    a.key_id === b.key_id &&
    a.schema === b.schema &&
    a.spec === b.spec
  );
}

/** The identity recorded when the held catalog was last decrypted. */
async function heldIdentity(root: string): Promise<Identity | null> {
  try {
    const text = await readFile(join(root, CURRENT, IDENTITY_FILE), "utf8");
    const value: unknown = JSON.parse(text);
    if (typeof value !== "object" || value === null || Array.isArray(value)) return null;
    const fields = value as Record<string, unknown>;
    for (const field of ["format", "created_at", "schema", "spec"]) {
      if (!Number.isInteger(fields[field])) return null;
    }
    if (typeof fields.key_id !== "string") return null;
    return {
      format: fields.format as number,
      created_at: fields.created_at as number,
      key_id: fields.key_id,
      schema: fields.schema as number,
      spec: fields.spec as number,
    };
  } catch {
    return null;
  }
}

async function heldDir(root: string): Promise<string | null> {
  const path = join(root, CURRENT);
  try {
    await readFile(join(path, IDENTITY_FILE));
    return path;
  } catch {
    return null;
  }
}

/** A refusal that keeps whatever catalog is already there. */
async function keep(root: string, reason: string): Promise<RefreshResult> {
  return {
    status: "kept",
    dir: await heldDir(root),
    reason,
    identity: await heldIdentity(root),
  };
}

/**
 * Fetches the pointer, and the package it names if it is worth having.
 *
 * Returns the catalog directory the player should open. Throws nothing: every
 * failure is a `kept`, because a player that cannot refresh should still
 * play what it has.
 */
export async function refreshCatalog(options: RefreshOptions): Promise<RefreshResult> {
  const { root, key, supportedSchema } = options;
  const get = options.fetch ?? globalThis.fetch;
  const now = options.now ?? (() => Math.floor(Date.now() / 1000));
  try {
    await mkdir(root, { recursive: true });
  } catch (error) {
    return keep(root, failureMessage(error));
  }

  const pointerUrl = `${options.baseUrl.replace(/\/+$/, "")}/latest.json`;
  let pointer: Pointer;
  try {
    const response = await get(pointerUrl);
    if (!response.ok) throw new Error(`the pointer answered ${response.status}`);
    pointer = parsePointer(await response.text());
  } catch (error) {
    return keep(root, `could not read the pointer: ${failureMessage(error)}`);
  }

  const refusal = pointerReadabilityRefusal(pointer, supportedSchema);
  if (refusal) return keep(root, refusal.reason);

  const expectedKeyId = createHash("sha256").update(key).digest("hex").slice(0, 8);
  if (pointer.key_id !== expectedKeyId) {
    // Before the download: a package sealed for another key cannot open, and
    // finding that out after fetching it wastes the fetch.
    return keep(root, "the package was sealed for a different key");
  }

  if (pointer.created_at > now() + FUTURE_TOLERANCE_SECONDS) {
    return keep(root, "the package claims to have been built in the future");
  }

  const held = await heldIdentity(root);
  if (held) {
    if (sameIdentity(held, identityOf(pointer))) {
      return { status: "unchanged", dir: await heldDir(root), identity: held };
    }
    if (pointer.created_at <= held.created_at) {
      return keep(root, "the package offered is older than the one already held");
    }
  }

  // Fetched from the base this player was configured with, joined to the
  // name in the pointer — deliberately not from the pointer's own `url`.
  // That field is not authenticated, so following it means fetching whatever
  // address the last person to write the pointer chose, and a player on a
  // home network making arbitrary requests is a probe with its credentials.
  // The exporter builds `url` as exactly this join, so nothing published
  // normally is fetched from anywhere different.
  const packageUrl = `${options.baseUrl.replace(/\/+$/, "")}/${pointer.file}`;

  let sealed: Uint8Array;
  try {
    const response = await get(packageUrl);
    if (!response.ok) throw new Error(`the package answered ${response.status}`);
    sealed = new Uint8Array(await response.arrayBuffer());
  } catch (error) {
    return keep(root, `could not fetch the package: ${failureMessage(error)}`);
  }

  // The pointer's own number decided whether to start; this decides whether
  // to go on. A ceiling the reader enforces only against a claim is no
  // ceiling at all.
  if (sealed.length > Math.min(pointer.bytes, MAX_PACKAGE_BYTES)) {
    return keep(root, `the package is larger than its pointer promised (${sealed.length} bytes)`);
  }

  const digest = createHash("sha256").update(sealed).digest("hex");
  if (digest !== pointer.sha256) {
    // Integrity only. The tag below is what proves the bytes are ours.
    return keep(root, "the package does not match the sha256 in its pointer");
  }

  let plaintext: Uint8Array;
  try {
    plaintext = openPackage(key, sealed, associatedData(pointer));
  } catch (error) {
    return keep(root, failureMessage(error));
  }

  const incoming = join(root, `incoming-${pointer.created_at}-${process.pid}`);
  let version: string;
  let staged = incoming;
  try {
    await rm(incoming, { recursive: true, force: true });
    await unpackTo(plaintext, incoming);
    await checkManifest(incoming, pointer);
    await writeFile(join(incoming, IDENTITY_FILE), JSON.stringify(identityOf(pointer)));
    version = await availableVersionName(root, pointer.created_at);
    await rename(incoming, join(root, version));
    staged = join(root, version);
    await swapCurrent(root, version);
  } catch (error) {
    await cleanupCatalogDirectory(staged);
    return keep(root, failureMessage(error));
  }

  await removeOtherVersions(root, version);

  return { status: "updated", dir: join(root, CURRENT), identity: identityOf(pointer) };
}

/** The manifest is inside the ciphertext, so it and the pointer must agree. */
async function checkManifest(dir: string, pointer: Pointer): Promise<void> {
  const text = await readFile(join(dir, MANIFEST_FILE), "utf8").catch(() => null);
  if (text === null) throw new Error("the package has no manifest");

  let manifest: { created_at?: unknown; schema?: unknown };
  try {
    manifest = JSON.parse(text) as typeof manifest;
  } catch {
    throw new Error("the package manifest is not JSON");
  }
  if (manifest.created_at !== pointer.created_at) {
    throw new Error("the package manifest disagrees with the pointer about when it was built");
  }
  if (manifest.schema !== pointer.schema) {
    throw new Error("the package manifest disagrees with the pointer about its schema");
  }
}
