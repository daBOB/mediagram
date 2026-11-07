// Where a pawn is on the road at a moment, from the scene and the server clock alone.

import type { BauherrScene } from "../../protocol/BauherrScene";

const HOP_LIFT = 0.55;

/** Where a pawn is: its field, or mid-hop while its owner walks (one field per `hop_ms`); `lift` is the hop's height. */
export function pawnPlace(scene: BauherrScene, seat: number, now: number): { u: number; lift: number } {
  const s = scene.seats[seat], t = scene.turn;
  if (t.stage !== "walking" || t.player !== s.player) return { u: s.field, lift: 0 };
  const progress = Math.min(Math.max((now - t.started_at) / t.hop_ms, 0), t.value);
  const whole = Math.floor(progress), f = progress - whole;
  return { u: t.from + progress, lift: whole >= t.value ? 0 : Math.sin(f * Math.PI) * HOP_LIFT };
}
