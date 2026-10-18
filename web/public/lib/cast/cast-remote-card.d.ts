import type { Session } from "../together/session.js";

export function part(name: string): HTMLElement & HTMLInputElement & HTMLDialogElement;
export function drawFixed(): void;
export function drawTitle(s: Session): void;
export function drawCard(s: Session, now: number): void;
