// The TV's own settings (sound and board figures), kept in the TV page's
// localStorage so they survive the next game night. Pages and the race board
// subscribe; the panel writes.

export type Figures = "pawns" | "avatars";
export type TvSettings = { muted: boolean; music: number; effects: number; figures: Figures };

export const LEVELS = 10;
export const DEFAULTS: TvSettings = { muted: false, music: 4, effects: 8, figures: "pawns" };
const KEY = "mediagram.game.tv-settings";

/** Keeps whatever of `raw` is valid and fills the rest from the defaults. */
export function sanitize(raw: unknown): TvSettings {
  const r = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  const level = (v: unknown, d: number) => (typeof v === "number" && Number.isFinite(v) ? Math.min(LEVELS, Math.max(0, Math.round(v))) : d);
  return {
    muted: typeof r.muted === "boolean" ? r.muted : DEFAULTS.muted,
    music: level(r.music, DEFAULTS.music),
    effects: level(r.effects, DEFAULTS.effects),
    figures: r.figures === "avatars" || r.figures === "pawns" ? r.figures : DEFAULTS.figures,
  };
}

/** Today's mix at the defaults (music 4 -> 0.35); the top level is a little over twice that. */
export const musicVolume = (s: TvSettings) => (s.muted ? 0 : Math.min(1, (0.35 * s.music) / 4));
export const effectsGain = (s: TvSettings) => (s.muted ? 0 : s.effects / 8);

function load(): TvSettings {
  try {
    return sanitize(JSON.parse(localStorage.getItem(KEY) ?? "null"));
  } catch {
    return { ...DEFAULTS }; // no storage or broken JSON: the page still renders
  }
}

let current = load();
const listeners = new Set<(s: TvSettings) => void>();

export const getSettings = () => current;

export function updateSettings(patch: Partial<TvSettings>) {
  current = sanitize({ ...current, ...patch });
  try {
    localStorage.setItem(KEY, JSON.stringify(current));
  } catch {
    // storage full or off: the change still applies for this session
  }
  listeners.forEach((l) => l(current));
}

/** Calls `fn` on every change; returns the unsubscribe. */
export function subscribeSettings(fn: (s: TvSettings) => void): () => void {
  listeners.add(fn);
  return () => void listeners.delete(fn);
}
