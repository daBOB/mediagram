import type { Live } from "../together-now.js";

/** Joins the cast `live` as a remote, shows the remote and follows the session. */
export function openRemote(live: Live): void;
/** Leaves the session, which goes on, and closes the remote. */
export function closeRemote(): void;
export function remoteOpen(): boolean;
/** `listener` runs whenever the remote opens or closes. Returns the unsubscribe. */
export function onRemote(listener: () => void): () => void;
