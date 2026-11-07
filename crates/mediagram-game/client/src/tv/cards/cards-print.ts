// The card faces and the back, printed once at load into one atlas canvas, and
// a second atlas of count numbers for the seats. Own design: cream stock, French
// suits, German indices (B, D, K, A), court cards as a lettered medallion.

import type { Card } from "../../protocol/Card";
import { RANKS, RANK_LABEL, SUITS, SUIT_SYMBOL, isRed } from "../../ui/card-face";

export const HUD_FONT = '"Bricolage Grotesque", "Atkinson Hyperlegible", system-ui, sans-serif';
const SYMBOL_FONT = '"Atkinson Hyperlegible", "DejaVu Sans", system-ui, sans-serif';

export const COLS = 8;
export const ROWS = 7;
const CW = 160, CH = 224;
export const BACK_CELL = 52;
const RED = "#c4213f", BLACK = "#1b1b22", CREAM = "#fbf6ea";

/** Atlas cell of a card, or the back. Cells run left to right, top to bottom. */
export const cellOf = (c: Card | null) => (c ? SUITS.indexOf(c.suit) * 13 + RANKS.indexOf(c.rank) : BACK_CELL);

// pip columns (left, centre, right) and rows (0 top .. 1 bottom) per rank, ten at most
const L = 0, C = 0.5, R = 1;
const PIPS: Record<number, [number, number][]> = {
  2: [[C, 0], [C, 1]],
  3: [[C, 0], [C, 0.5], [C, 1]],
  4: [[L, 0], [R, 0], [L, 1], [R, 1]],
  5: [[L, 0], [R, 0], [C, 0.5], [L, 1], [R, 1]],
  6: [[L, 0], [R, 0], [L, 0.5], [R, 0.5], [L, 1], [R, 1]],
  7: [[L, 0], [R, 0], [C, 0.25], [L, 0.5], [R, 0.5], [L, 1], [R, 1]],
  8: [[L, 0], [R, 0], [C, 0.25], [L, 0.5], [R, 0.5], [C, 0.75], [L, 1], [R, 1]],
  9: [[L, 0], [R, 0], [L, 1 / 3], [R, 1 / 3], [C, 0.5], [L, 2 / 3], [R, 2 / 3], [L, 1], [R, 1]],
  10: [[L, 0], [R, 0], [C, 1 / 6], [L, 1 / 3], [R, 1 / 3], [L, 2 / 3], [R, 2 / 3], [C, 5 / 6], [L, 1], [R, 1]],
};

function face(g: CanvasRenderingContext2D, card: Card) {
  const ink = isRed(card.suit) ? RED : BLACK, sym = SUIT_SYMBOL[card.suit], label = RANK_LABEL[card.rank];
  g.fillStyle = CREAM;
  g.fillRect(0, 0, CW, CH);
  g.strokeStyle = "rgba(60,40,20,0.55)";
  g.lineWidth = 3;
  g.strokeRect(1.5, 1.5, CW - 3, CH - 3);
  g.fillStyle = ink;
  g.textAlign = "center";
  g.textBaseline = "middle";
  // corner index, repeated upside down in the opposite corner
  for (const flip of [false, true]) {
    g.save();
    if (flip) {
      g.translate(CW, CH);
      g.rotate(Math.PI);
    }
    g.font = `800 ${label.length > 1 ? 30 : 36}px ${HUD_FONT}`;
    g.fillText(label, 21, 28);
    g.font = `700 30px ${SYMBOL_FONT}`;
    g.fillText(sym, 21, 62);
    g.restore();
  }
  const n = Number(label);
  const x0 = 50, x1 = CW - 50, y0 = 40, y1 = CH - 40;
  if (n >= 2) {
    g.font = `700 ${n <= 3 ? 46 : 40}px ${SYMBOL_FONT}`;
    for (const [col, row] of PIPS[n]) {
      g.save();
      g.translate(x0 + (x1 - x0) * col, y0 + (y1 - y0) * row);
      if (row > 0.5) g.rotate(Math.PI);
      g.fillText(sym, 0, 0);
      g.restore();
    }
  } else if (label === "A") {
    g.font = `700 100px ${SYMBOL_FONT}`;
    g.fillText(sym, CW / 2, CH / 2 + 4);
  } else {
    // court card: a framed medallion with the letter between two suit marks
    g.strokeStyle = ink;
    g.lineWidth = 4;
    g.fillStyle = isRed(card.suit) ? "rgba(196,33,63,0.10)" : "rgba(27,27,34,0.08)";
    g.beginPath();
    g.roundRect(42, 38, CW - 84, CH - 76, 16);
    g.fill();
    g.stroke();
    g.fillStyle = ink;
    g.font = `800 82px ${HUD_FONT}`;
    g.fillText(label, CW / 2, CH / 2 + 2);
    g.font = `700 34px ${SYMBOL_FONT}`;
    g.fillText(sym, CW / 2, 66);
    g.save();
    g.translate(CW / 2, CH - 66);
    g.rotate(Math.PI);
    g.fillText(sym, 0, 0);
    g.restore();
  }
}

function back(g: CanvasRenderingContext2D) {
  g.fillStyle = CREAM;
  g.fillRect(0, 0, CW, CH);
  g.fillStyle = "#1f3366";
  g.fillRect(9, 9, CW - 18, CH - 18);
  g.save();
  g.beginPath();
  g.rect(15, 15, CW - 30, CH - 30);
  g.clip();
  g.strokeStyle = "rgba(240,224,190,0.5)";
  g.lineWidth = 2;
  for (let k = -CH; k < CW + CH; k += 22) {
    g.beginPath();
    g.moveTo(k, 0);
    g.lineTo(k + CH, CH);
    g.moveTo(k + CH, 0);
    g.lineTo(k, CH);
    g.stroke();
  }
  g.restore();
  g.strokeStyle = "#f0e0be";
  g.lineWidth = 3;
  g.strokeRect(15, 15, CW - 30, CH - 30);
  g.beginPath();
  g.ellipse(CW / 2, CH / 2, 30, 42, 0, 0, 7);
  g.fillStyle = "#1f3366";
  g.fill();
  g.stroke();
  g.fillStyle = "#f0e0be";
  g.font = `700 34px ${SYMBOL_FONT}`;
  g.textAlign = "center";
  g.textBaseline = "middle";
  g.fillText("♠", CW / 2, CH / 2 + 2);
}

/** All 52 faces and the back in one canvas, laid out by `cellOf`. */
export function printCards(): HTMLCanvasElement {
  const c = document.createElement("canvas");
  c.width = COLS * CW;
  c.height = ROWS * CH;
  const g = c.getContext("2d")!;
  const paint = (cell: number, draw: (g: CanvasRenderingContext2D) => void) => {
    g.save();
    g.translate((cell % COLS) * CW, Math.floor(cell / COLS) * CH);
    g.beginPath();
    g.rect(0, 0, CW, CH);
    g.clip();
    draw(g);
    g.restore();
  };
  SUITS.forEach((suit, s) => RANKS.forEach((rank, r) => paint(s * 13 + r, (ctx) => face(ctx, { suit, rank }))));
  paint(BACK_CELL, back);
  return c;
}

const NUMBER_CELL = 64;
/** The numbers 0..55 in white on dark discs, for the card counts beside the seats. */
export function printNumbers(): HTMLCanvasElement {
  const c = document.createElement("canvas");
  c.width = COLS * NUMBER_CELL;
  c.height = ROWS * NUMBER_CELL;
  const g = c.getContext("2d")!;
  g.textAlign = "center";
  g.textBaseline = "middle";
  for (let n = 0; n < COLS * ROWS; n++) {
    const x = (n % COLS) * NUMBER_CELL + NUMBER_CELL / 2, y = Math.floor(n / COLS) * NUMBER_CELL + NUMBER_CELL / 2;
    g.beginPath();
    g.arc(x, y, NUMBER_CELL / 2 - 3, 0, 7);
    g.fillStyle = "rgba(28,18,11,0.9)";
    g.fill();
    g.lineWidth = 3;
    g.strokeStyle = "#f0e0be";
    g.stroke();
    g.fillStyle = "#fff4e2";
    g.font = `800 ${n > 9 ? 32 : 36}px ${HUD_FONT}`;
    g.fillText(String(n), x, y + 2);
  }
  return c;
}
