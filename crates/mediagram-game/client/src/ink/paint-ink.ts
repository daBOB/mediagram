import type { DrawOp } from "../protocol/DrawOp";
import type { Stroke } from "./ink-log";
import { INK_COLORS, INK_MAX, PAPER_COLOR, lineWidthPx } from "./ink-style";

function setPen(ctx: CanvasRenderingContext2D, color: number, width: number, w: number) {
  ctx.strokeStyle = INK_COLORS[color] ?? INK_COLORS[0];
  ctx.lineWidth = lineWidthPx(color, width, w);
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
}

const px = (v: number, size: number) => (v / INK_MAX) * size;

export function paintAll(ctx: CanvasRenderingContext2D, strokes: Stroke[], w: number, h: number): void {
  ctx.fillStyle = PAPER_COLOR;
  ctx.fillRect(0, 0, w, h);
  for (const s of strokes) {
    const p = s.points;
    if (p.length < 2) continue;
    setPen(ctx, s.color, s.width, w);
    ctx.beginPath();
    ctx.moveTo(px(p[0], w), px(p[1], h));
    if (p.length === 2) ctx.lineTo(px(p[0], w), px(p[1], h)); // a dot, via the round cap
    // Curves run through the midpoints, so the points themselves are control points.
    for (let i = 2; i + 3 < p.length; i += 2) {
      const mx = (px(p[i], w) + px(p[i + 2], w)) / 2;
      const my = (px(p[i + 1], h) + px(p[i + 3], h)) / 2;
      ctx.quadraticCurveTo(px(p[i], w), px(p[i + 1], h), mx, my);
    }
    if (p.length >= 4) ctx.lineTo(px(p[p.length - 2], w), px(p[p.length - 1], h));
    ctx.stroke();
  }
}

/** Paints ops[from..]; returns false when they hold an undo or clear and the caller must paintAll. */
export function paintTail(ctx: CanvasRenderingContext2D, ops: DrawOp[], from: number, w: number, h: number): boolean {
  if (ops.slice(from).some((o) => o.t === "undo" || o.t === "clear")) return false;
  let last: [number, number] | null = null; // where the open stroke ended
  let pen: { color: number; width: number } | null = null;
  ops.forEach((op, i) => {
    if (op.t === "line") {
      pen = { color: op.color, width: op.width };
      last = null;
    } else if (op.t !== "more") {
      pen = null; // undo or clear closes the stroke
      return;
    } else if (!pen) {
      return;
    }
    const pts = op.t === "line" || op.t === "more" ? op.points : [];
    if (i >= from && pen && pts.length >= 2) {
      setPen(ctx, pen.color, pen.width, w);
      ctx.beginPath();
      const start: [number, number] = last ?? [pts[0], pts[1]];
      ctx.moveTo(px(start[0], w), px(start[1], h));
      if (!last && pts.length === 2) ctx.lineTo(px(pts[0], w), px(pts[1], h));
      for (let k = last ? 0 : 2; k + 1 < pts.length; k += 2) ctx.lineTo(px(pts[k], w), px(pts[k + 1], h));
      ctx.stroke();
    }
    if (pts.length >= 2) last = [pts[pts.length - 2], pts[pts.length - 1]];
  });
  return true;
}
