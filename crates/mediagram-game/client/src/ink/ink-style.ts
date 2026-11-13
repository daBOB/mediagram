// Paper look shared with the iOS board; both sides pin the same values.
export const PAPER_ASPECT = 16 / 9;
export const INK_MAX = 10000;
/** Index 5 is the eraser: ink in the paper colour. */
export const INK_COLORS = ["#1b1b1b", "#d8342b", "#2c6bed", "#2f9e44", "#f2a20e", "#fbf8f1"];
export const ERASER_INDEX = 5;
/** Fractions of the paper's width. */
export const INK_WIDTHS = [0.005, 0.01, 0.017];
export const ERASER_SCALE = 3;
export const PAPER_COLOR = INK_COLORS[ERASER_INDEX];

export function lineWidthPx(color: number, width: number, paperWidthPx: number): number {
  const w = INK_WIDTHS[Math.min(Math.max(width, 0), INK_WIDTHS.length - 1)] ?? INK_WIDTHS[1];
  return w * paperWidthPx * (color === ERASER_INDEX ? ERASER_SCALE : 1);
}
