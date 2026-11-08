// Where things are at a moment, from the host's scene and clock alone, so a TV that joins mid-turn shows the game as it is: a pawn
// hop by hop along its walk, pawns sharing a field, the dice in flight, the coins of a guest's payment.

import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import { COINS, PAWN } from "./grand-allee-look";

/** Fields a walk hops: one that would end on an occupied field goes on to the next free one, so this can be more than the die shows. */
export const hops = (from: number, to: number, fields: number) => (to - from + fields) % fields;

/** When (host time) a walk that earns the lap money reaches or passes the Bank; null for any other stage. The host pays at the start. */
export function bankAt(scene: GrandAlleeScene): number | null {
  const t = scene.turn, fields = scene.board.fields.length;
  if (t.stage !== "walking" || !t.bank) return null;
  return t.started_at + Math.min(hops(t.from, t.to, fields), (fields - t.from) % fields || fields) * t.hop_ms;
}

/** Where a seat's pawn is: its field, or mid-hop while its player walks (one field per hop_ms); `lift` is the hop's height. */
export function pawnPlace(scene: GrandAlleeScene, seat: number, now: number): { u: number; lift: number } {
  const s = scene.seats[seat], t = scene.turn;
  if (t.stage !== "walking" || t.player !== s.player) return { u: s.field, lift: 0 };
  const n = hops(t.from, t.to, scene.board.fields.length), p = Math.min(Math.max((now - t.started_at) / t.hop_ms, 0), n), whole = Math.floor(p);
  return { u: t.from + p, lift: whole >= n ? 0 : Math.sin((p - whole) * Math.PI) * PAWN.hop };
}

/**
 * Each pawn's place among those standing on its field (everyone starts on the Bank; later the engine never puts two on one):
 * `k` of `n`. The walker and the broke stand alone.
 */
export function sharing(scene: GrandAlleeScene): { k: number; n: number }[] {
  const walker = scene.turn.stage === "walking" ? scene.turn.player : -1;
  const on = scene.seats.map((s) => (s.out || s.player === walker ? -1 : s.field));
  return on.map((f, i) => {
    if (f < 0) return { k: 0, n: 1 };
    const same = on.flatMap((g, j) => (g === f ? [j] : []));
    return { k: same.indexOf(i), n: same.length };
  });
}

/** How far a throw is from `from` (0) to `to` (1), clamped. */
export const flight = (from: number, to: number, now: number) => Math.min(1, Math.max(0, (now - from) / Math.max(1, to - from)));

/** How many coins a guest's payment sends. */
export const coinCount = (amount: number) => Math.min(COINS.max, Math.max(3, Math.round(amount / COINS.per)));

/**
 * Coin `i` of `n`: its way (0..1) from the payer's pawn to the owner's hotel, or null before it leaves and after it arrives.
 * They leave one after another once the nights die lands; the last arrives at paid_at, when the cash moves.
 */
export function coinProgress(i: number, n: number, landsAt: number, paidAt: number, now: number): number | null {
  const span = Math.max(1, paidAt - landsAt), fly = Math.min(COINS.flyMs, span), gap = n > 1 ? (span - fly) / (n - 1) : 0;
  const p = (now - (landsAt + i * gap)) / fly;
  return p < 0 || p > 1 ? null : p;
}
