/**
 * What each open player is doing, reported in rather than measured here.
 *
 * The System page and the player it describes are usually different
 * devices — a phone looking at what the television is playing — so this
 * cannot be read off a live object the way the cache or the transcoder are.
 * Each open player POSTs its own reading periodically; this keeps the most
 * recent one per viewer for a short while and lets it expire on its own.
 */

import type { PlayerRequest } from "../http/contracts";

/** Longer than the 5s the player reports at, so one missed beat is not an outage. */
const TTL_MS = 15_000;

/** However many devices a household plausibly has open at once, with headroom. */
const MAX_VIEWERS = 16;

const MODES = ["direct", "copy", "hevc-copy", "transcode"] as const;
export type PlaybackMode = (typeof MODES)[number];

const HEALTH_STATES = ["ok", "behind", "starving"] as const;
export type PlaybackHealth = (typeof HEALTH_STATES)[number];

/** What one open player says about itself. */
export interface PlaybackReport {
  /** `crypto.randomUUID()` per page load; never sent back out in a listing. */
  viewer: string;
  setId: string;
  title: string;
  mode: PlaybackMode;
  videoCodec: string | null;
  audioCodec: string | null;
  bitrateBits: number | null;
  /** Seconds of buffer ahead of the playhead. */
  ahead: number | null;
  health: PlaybackHealth;
  fillRate: number | null;
  dropped: number | null;
  frames: number | null;
  paused: boolean;
  held: boolean;
}

/** A stored report, as the snapshot shows it: no viewer id, but who and how stale. */
export type PlaybackRow = Omit<PlaybackReport, "viewer"> & { from: string; ageSeconds: number };

function clampedString(value: unknown, maxLength: number): string | null {
  if (typeof value !== "string" || value.length === 0) return null;
  return value.slice(0, maxLength);
}

function finiteOrNull(value: unknown): number | null {
  const n = Number(value);
  return Number.isFinite(n) && n >= 0 ? n : null;
}

/**
 * `null` when the body cannot possibly be a report — an unknown `mode` or
 * `health`, or no `viewer`/`setId` to key it by — rather than a partial
 * reading. Every other field degrades to a clamped or `null` value instead
 * of failing the whole report: a codec name a browser declined to give is
 * not a reason to lose the rest of what a viewer sent.
 */
export function validateReport(body: unknown): PlaybackReport | null {
  if (typeof body !== "object" || body === null) return null;
  const fields = body as Record<string, unknown>;

  const viewer = clampedString(fields.viewer, 64);
  const setId = clampedString(fields.setId, 64);
  if (viewer === null || setId === null) return null;

  const mode = fields.mode;
  if (typeof mode !== "string" || !MODES.includes(mode as PlaybackMode)) return null;
  const health = fields.health;
  if (typeof health !== "string" || !HEALTH_STATES.includes(health as PlaybackHealth)) return null;

  return {
    viewer,
    setId,
    title: clampedString(fields.title, 200) ?? "",
    mode: mode as PlaybackMode,
    videoCodec: clampedString(fields.videoCodec, 16),
    audioCodec: clampedString(fields.audioCodec, 16),
    bitrateBits: finiteOrNull(fields.bitrateBits),
    ahead: finiteOrNull(fields.ahead),
    health: health as PlaybackHealth,
    fillRate: finiteOrNull(fields.fillRate),
    dropped: finiteOrNull(fields.dropped),
    frames: finiteOrNull(fields.frames),
    paused: fields.paused === true,
    held: fields.held === true,
  };
}

export class PlaybackReports {
  private readonly byViewer = new Map<string, { report: PlaybackReport; from: string; at: number }>();

  constructor(private readonly now: () => number = Date.now) {}

  /** Replaces this viewer's reading. The stalest is dropped rather than refusing a new one over the cap. */
  put(report: PlaybackReport, from: PlayerRequest["client"]): void {
    this.prune();
    if (!this.byViewer.has(report.viewer) && this.byViewer.size >= MAX_VIEWERS) {
      const stalest = [...this.byViewer.entries()].sort((a, b) => a[1].at - b[1].at)[0];
      if (stalest) this.byViewer.delete(stalest[0]);
    }
    this.byViewer.set(report.viewer, { report, from: from ?? "unknown", at: this.now() });
  }

  /** Every reading still inside its TTL, without viewer ids, sorted for a stable listing. */
  list(): PlaybackRow[] {
    this.prune();
    const now = this.now();
    return [...this.byViewer.values()]
      .map(({ report, from, at }) => {
        const { viewer: _viewer, ...rest } = report;
        return { ...rest, from, ageSeconds: Math.max(0, Math.round((now - at) / 1000)) };
      })
      .sort((a, b) => a.setId.localeCompare(b.setId) || a.from.localeCompare(b.from));
  }

  private prune(): void {
    const deadline = this.now() - TTL_MS;
    for (const [viewer, entry] of this.byViewer) {
      if (entry.at <= deadline) this.byViewer.delete(viewer);
    }
  }
}
