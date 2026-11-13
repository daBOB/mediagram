// Which sound Kopfnuss makes when, worked out from the scene alone. Each cue
// has a key so it plays once however many frames show it; the state keeps the
// keys already played and when the TV first saw the current stage.

import type { KopfnussScene } from "../../protocol/KopfnussScene";

export type SoundName = "round" | "pickDone" | "chosen" | "zap" | "boing" | "go" | "tick" | "reveal" | "step" | "summit";

export interface Cue {
  key: string;
  sound: SoundName;
  /** Host time, ms; none: the moment the cue first appears. */
  at?: number;
  /** A change to sound only while the TV watches it, not when it first sees the stage. */
  onlyChanges?: boolean;
}

/** Each landed trick appears on the TV this long after the one before (TvKopfnuss's animation delay). */
export const HIT_STEP_MS = 450;
/** The first trick lands this long after the category is chosen. */
const FIRST_HIT_MS = 100;
/** Cues are handed out this far ahead, so Web Audio can start them on time. */
const LOOKAHEAD_MS = 150;
/** A cue more than this late is skipped: a TV joining late stays quiet. */
const LATE_MS = 120;

export interface SoundState {
  stage: string;
  /** Host time the TV first saw the current stage. */
  since: number;
  played: Set<string>;
}

export const newSoundState = (): SoundState => ({ stage: "", since: 0, played: new Set() });

function stageKey(scene: KopfnussScene): string {
  const st = scene.stage;
  if (st.stage === "question") return `q ${scene.round} ${st.quiz.round} ${st.quiz.stage.stage}`;
  return `${st.stage} ${scene.round}`;
}

function cues(scene: KopfnussScene, since: number): Cue[] {
  const st = scene.stage;
  const r = scene.round;
  switch (st.stage) {
    case "pick":
      return [
        { key: `round ${r}`, sound: "round", at: since },
        ...st.done.map((p): Cue => ({ key: `done ${r} ${p}`, sound: "pickDone", onlyChanges: true })),
      ];
    case "resolve":
      return [
        { key: `chosen ${r}`, sound: "chosen", at: since },
        ...st.hits.map((h, i): Cue => ({ key: `hit ${r} ${i}`, sound: h.bounced ? "boing" : "zap", at: since + FIRST_HIT_MS + i * HIT_STEP_MS })),
      ];
    case "question": {
      const q = st.quiz.stage;
      const id = `${r} ${st.quiz.round}`;
      if (q.stage === "reveal") return [{ key: `reveal ${id}`, sound: "reveal", at: since }];
      if (q.stage !== "intro" && q.stage !== "open") return [];
      return [
        { key: `go ${id}`, sound: "go", at: q.opens_at },
        ...[3, 2, 1].map((s): Cue => ({ key: `tick ${id} ${s}`, sound: "tick", at: q.closes_at - s * 1000 })),
      ];
    }
    case "climb":
      return st.climbers.map((c): Cue => ({ key: `step ${c.player} ${c.step}`, sound: "step", onlyChanges: true }));
    case "fact":
      return [{ key: "summit", sound: "summit", at: since }];
    case "final":
      return [];
  }
}

/**
 * The cues to start now (with their host times), each once; `state` remembers them.
 * `lookaheadMs`: how early a cue is handed out, at least the speaker's delay.
 */
export function soundsDue(state: SoundState, scene: KopfnussScene, now: number, lookaheadMs = LOOKAHEAD_MS): (Cue & { at: number })[] {
  return cuesDue(state, stageKey(scene), (since) => cues(scene, since), now, lookaheadMs);
}

/**
 * The same for any game: `stage` names what the TV shows, `cuesOf` lists its
 * cues given the host time the TV first saw it.
 */
export function cuesDue(state: SoundState, stage: string, cuesOf: (since: number) => Cue[], now: number, lookaheadMs = LOOKAHEAD_MS): (Cue & { at: number })[] {
  const firstSight = stage !== state.stage;
  if (firstSight) {
    state.stage = stage;
    state.since = now;
  }
  const out: (Cue & { at: number })[] = [];
  for (const cue of cuesOf(state.since)) {
    if (state.played.has(cue.key)) continue;
    if (firstSight && cue.onlyChanges) {
      state.played.add(cue.key);
      continue;
    }
    const at = cue.at ?? now;
    if (at - now > lookaheadMs) continue;
    state.played.add(cue.key);
    if (now - at > LATE_MS) continue;
    out.push({ ...cue, at });
  }
  return out;
}
