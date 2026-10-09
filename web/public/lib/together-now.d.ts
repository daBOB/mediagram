/**
 * Watch together as this tab knows it, shared by Home's notice, the title
 * pages and the player (`docs/mlib-spec.md` §7b).
 */

import type { CatalogSet } from "./library.js";
import type { Session } from "./together/session.js";

export interface Live { id: number; session: Session }
export interface TogetherView { available: boolean; reason: string | null; live: Live | null; offset: number }
/** What a `together` event said, ended and stale sessions read as `null`. */
export interface Heard { id: number; session: Session | null }

/** This tab's screen id in a session's `members`. */
export const screen: string;
export function togetherNow(): TogetherView;
/** This tab's clock, corrected to the server's. */
export function serverNow(): number;
/** Whether this tab is in a session, as its player last said through `setInTogether`. */
export function inTogether(): boolean;
export function setInTogether(value: boolean): void;
/** `listener` runs each time this tab is out of a session. */
export function onLeaveTogether(listener: () => void): void;
/** `heard` is `null` after a read of `GET /api/together`. Returns the unsubscribe. */
export function onTogether(listener: (view: TogetherView, heard: Heard | null) => void): () => void;
export function refreshTogether(): Promise<void>;
export function heardTogether(payload: { id: number; session: Session | null; serverNow: number }): void;
/** Takes session `id` out of this tab's view, telling listeners as a `null` event would. */
export function forgetTogether(id: number): void;
export function together(body: Record<string, unknown>): Promise<{ ok: boolean; status: number; body: any }>;
export function setTogetherOpener(open: (set: CatalogSet) => void, find: (setId: string) => CatalogSet | null): void;
/** The set `setId` names in this profile's catalog, or `null` when it is not in it. */
export function setForTogether(setId: string): CatalogSet | null;
export function openForTogether(setId: string, join?: number | null): void;
export function takeJoin(): number | null;
