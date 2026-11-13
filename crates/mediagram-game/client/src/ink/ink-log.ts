import type { DrawOp } from "../protocol/DrawOp";

export type Stroke = { color: number; width: number; points: number[] };
export type InkChange = "reset" | "append";

/** Folds ops into the strokes they leave on the paper. A `more` with no open stroke (after an undo or clear) is dropped. */
export function strokesOf(ops: DrawOp[]): Stroke[] {
  const strokes: Stroke[] = [];
  let open = false;
  for (const op of ops) {
    if (op.t === "line") {
      strokes.push({ color: op.color, width: op.width, points: [...op.points] });
      open = true;
    } else if (op.t === "more") {
      if (open) strokes[strokes.length - 1].points.push(...op.points);
    } else {
      if (op.t === "undo") strokes.pop();
      else strokes.length = 0;
      open = false;
    }
  }
  return strokes;
}

/** The drawing of one turn, as the box relays it: replaced from index 0, otherwise appended in order. */
export class InkLog {
  turn: number | null = null;
  ops: DrawOp[] = [];
  private subs = new Set<(change: InkChange, from: number) => void>();

  apply(msg: { turn: number; from: number; ops: DrawOp[] }): InkChange | "ignored" {
    let change: InkChange;
    if (msg.from === 0) {
      this.turn = msg.turn;
      this.ops = [...msg.ops];
      change = "reset";
    } else if (msg.turn === this.turn && msg.from === this.ops.length) {
      this.ops.push(...msg.ops);
      change = "append";
    } else {
      return "ignored";
    }
    for (const fn of this.subs) fn(change, msg.from);
    return change;
  }

  /** "reset": repaint from scratch; "append": ops[from..] are new. */
  subscribe(fn: (change: InkChange, from: number) => void): () => void {
    this.subs.add(fn);
    return () => void this.subs.delete(fn);
  }
}
