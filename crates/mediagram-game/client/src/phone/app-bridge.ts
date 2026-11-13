// The Spieltisch controller apps (Android, iPhone/iPad) show this same page in
// a web view and add `window.mediagramApp`. Through it the page vibrates the
// device and keeps its memory with the app, because localStorage belongs to the
// box's address, and that moves whenever the router hands out a new one.
// In a plain browser there is no bridge and everything works as before.

import type { BoardAct } from "../protocol/BoardAct";
import type { NativeBoardShow } from "./board-native";

export type BuzzKind = "turn" | "accepted" | "wrong" | "won";

type NativeBridge = {
  vibrate(kind: string): void;
  getMemory(): string;
  setMemory(key: string, value: string | null): void;
  // The iPad app alone sets `board` and draws the drawing board natively; the page drives it.
  board?: boolean;
  showBoard?(json: string): void;
  hideBoard?(): void;
};

// Android Chrome over plain http still vibrates after a tap; iPhones ignore it.
const BROWSER_PATTERN: Record<BuzzKind, number | number[]> = {
  accepted: 30,
  turn: [60, 80, 60],
  wrong: 300,
  won: [80, 60, 80, 60, 250],
};

function bridge(): NativeBridge | null {
  return ((globalThis as Record<string, unknown>).mediagramApp as NativeBridge | undefined) ?? null;
}

export function inApp(): boolean {
  return bridge() !== null;
}

export function boardCapable(): boolean {
  return bridge()?.board === true;
}

export function showBoard(show: NativeBoardShow) {
  bridge()?.showBoard?.(JSON.stringify(show));
}

export function hideBoard() {
  bridge()?.hideBoard?.();
}

/** Installs `globalThis.mediagramBoard = { receive(json) }`, which the app calls with each act; returns the remover. */
export function listenToBoard(onAct: (act: BoardAct) => void): () => void {
  const g = globalThis as Record<string, unknown>;
  const listener = {
    receive(json: string) {
      let act: BoardAct | null;
      try {
        act = JSON.parse(json);
      } catch {
        return;
      }
      if (typeof act?.t === "string") onAct(act);
    },
  };
  g.mediagramBoard = listener;
  return () => {
    if (g.mediagramBoard === listener) delete g.mediagramBoard;
  };
}

export function buzz(kind: BuzzKind) {
  const app = bridge();
  if (app) {
    app.vibrate(kind);
    return;
  }
  try {
    navigator.vibrate?.(BROWSER_PATTERN[kind]);
  } catch {
    // a browser that refuses (no tap yet) just stays still
  }
}

// The bridge's memory, read once per bridge and kept current by `set`.
let readFrom: NativeBridge | null = null;
let appMemory: Record<string, string> = {};

function appStore(app: NativeBridge): Record<string, string> {
  if (readFrom !== app) {
    readFrom = app;
    try {
      appMemory = JSON.parse(app.getMemory()) ?? {};
    } catch {
      appMemory = {};
    }
  }
  return appMemory;
}

export const memory = {
  get(key: string): string | null {
    const app = bridge();
    if (app) return appStore(app)[key] ?? null;
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  },
  set(key: string, value: string | null) {
    const app = bridge();
    if (app) {
      const store = appStore(app);
      if (value === null) delete store[key];
      else store[key] = value;
      app.setMemory(key, value);
      return;
    }
    try {
      if (value === null) localStorage.removeItem(key);
      else localStorage.setItem(key, value);
    } catch {
      // private mode: the seat is just not remembered
    }
  },
};
