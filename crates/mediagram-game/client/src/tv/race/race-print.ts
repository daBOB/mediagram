// The printed board face: cherry veneer, fields, lines, corner panels and the
// centre rosette in one 2048² canvas, with the recess and rim shading painted
// in. Gradients only: blur filters and per-dot noise are slow on the box CPU.

import { Color } from "three";
import { BOARD, CELL, GOALS, PATH, YARDS } from "./race-geometry";

const SIZE = 2048;
const INK = "#3a2213";
const FIELD = "#f5ead3";

const shade = (hex: string, k: number) => "#" + new Color(hex).offsetHSL(0, 0, k).getHexString();

/** `colors[c]`: the colour of the player in corner c, or a neutral wood tone for an empty corner. */
export function printBoard(wood: HTMLImageElement, colors: string[]): HTMLCanvasElement {
  const c = document.createElement("canvas");
  c.width = c.height = SIZE;
  const g = c.getContext("2d")!;
  const s = SIZE / BOARD;
  const P = (gv: number) => (gv * CELL + BOARD / 2) * s;

  for (let i = 0; i < 2; i++) for (let j = 0; j < 2; j++) g.drawImage(wood, i * 1024, j * 1024, 1024, 1024);
  g.fillStyle = "rgba(255,190,120,0.10)";
  g.fillRect(0, 0, SIZE, SIZE);
  g.strokeStyle = INK;
  g.lineWidth = 0.05 * s;
  g.strokeRect(0.32 * s, 0.32 * s, SIZE - 0.64 * s, SIZE - 0.64 * s);
  g.lineWidth = 0.018 * s;
  g.strokeRect(0.46 * s, 0.46 * s, SIZE - 0.92 * s, SIZE - 0.92 * s);

  YARDS.forEach((yard, corner) => {
    const cx = (yard[0][0] + yard[3][0]) / 2, cz = (yard[0][1] + yard[3][1]) / 2, half = 1.45 * CELL * s;
    g.beginPath();
    g.roundRect(P(cx) - half, P(cz) - half, half * 2, half * 2, 0.35 * CELL * s);
    g.fillStyle = colors[corner] + "38";
    g.fill();
    g.lineWidth = 0.04 * s;
    g.strokeStyle = colors[corner];
    g.stroke();
  });

  // printed lines between the fields, and into each goal lane
  g.lineCap = "round";
  g.lineJoin = "round";
  g.strokeStyle = INK;
  g.lineWidth = 0.07 * s;
  g.beginPath();
  PATH.forEach(([x, z], i) => (i ? g.lineTo(P(x), P(z)) : g.moveTo(P(x), P(z))));
  g.closePath();
  g.stroke();
  GOALS.forEach((lane, corner) => {
    const [ex, ez] = PATH[(corner * 10 + 39) % 40];
    g.beginPath();
    g.moveTo(P(ex), P(ez));
    g.lineTo(P(lane[3][0]), P(lane[3][1]));
    g.stroke();
  });

  // centre rosette: four points in the corner colours
  const c0 = P(0), R = 0.62 * CELL * s;
  GOALS.forEach((lane, corner) => {
    const a = Math.atan2(lane[3][1], lane[3][0]);
    g.beginPath();
    g.moveTo(c0, c0);
    g.lineTo(c0 + Math.cos(a - 0.45) * R * 0.55, c0 + Math.sin(a - 0.45) * R * 0.55);
    g.lineTo(c0 + Math.cos(a) * R, c0 + Math.sin(a) * R);
    g.lineTo(c0 + Math.cos(a + 0.45) * R * 0.55, c0 + Math.sin(a + 0.45) * R * 0.55);
    g.closePath();
    g.fillStyle = colors[corner];
    g.fill();
    g.lineWidth = 0.025 * s;
    g.strokeStyle = INK;
    g.stroke();
  });
  g.beginPath();
  g.arc(c0, c0, 0.16 * CELL * s, 0, 7);
  g.fillStyle = "#f4e9d2";
  g.fill();
  g.stroke();

  const field = (gx: number, gz: number, fill: string, r = 0.43) => {
    const x = P(gx), y = P(gz), rr = r * CELL * s;
    g.beginPath();
    g.arc(x, y, rr, 0, 7);
    g.fillStyle = fill;
    g.fill();
    // shallow recess: the rim on the sun side shades it
    const gr = g.createRadialGradient(x - rr * 0.22, y - rr * 0.28, rr * 0.5, x, y, rr);
    gr.addColorStop(0, "rgba(40,20,5,0)");
    gr.addColorStop(1, "rgba(40,20,5,0.34)");
    g.fillStyle = gr;
    g.fill();
    g.lineWidth = 0.045 * s;
    g.strokeStyle = INK;
    g.stroke();
    return rr;
  };
  PATH.forEach(([gx, gz], i) => {
    const corner = i % 10 === 0 ? i / 10 : -1;
    const rr = field(gx, gz, corner >= 0 ? colors[corner] : FIELD);
    if (corner < 0) return;
    // start field: an arrow along the direction of travel
    const [nx, nz] = PATH[(i + 1) % 40];
    g.save();
    g.translate(P(gx), P(gz));
    g.rotate(Math.atan2(nz - gz, nx - gx));
    g.fillStyle = "#fff6e6";
    g.beginPath();
    g.moveTo(rr * 0.5, 0);
    g.lineTo(-rr * 0.25, -rr * 0.42);
    g.lineTo(-rr * 0.05, 0);
    g.lineTo(-rr * 0.25, rr * 0.42);
    g.closePath();
    g.fill();
    g.restore();
  });
  GOALS.forEach((lane, corner) => lane.forEach(([x, z]) => field(x, z, shade(colors[corner], 0.12))));
  YARDS.forEach((yard, corner) => yard.forEach(([x, z]) => field(x, z, shade(colors[corner], 0.05), 0.4)));

  // soft occlusion where the board meets the raised frame, stronger on the side the frame shades
  const pool = g.createRadialGradient(SIZE / 2, SIZE / 2, SIZE * 0.3, SIZE / 2, SIZE / 2, SIZE * 0.75);
  pool.addColorStop(0, "rgba(30,14,4,0)");
  pool.addColorStop(1, "rgba(30,14,4,0.22)");
  g.fillStyle = pool;
  g.fillRect(0, 0, SIZE, SIZE);
  const edge = (x0: number, y0: number, x1: number, y1: number, a: number) => {
    const gr = g.createLinearGradient(x0, y0, x1, y1);
    gr.addColorStop(0, `rgba(30,14,4,${a})`);
    gr.addColorStop(1, "rgba(30,14,4,0)");
    g.fillStyle = gr;
  };
  const e = 0.42 * s;
  edge(0, 0, e, 0, 0.3); g.fillRect(0, 0, e, SIZE);
  edge(0, 0, 0, e, 0.3); g.fillRect(0, 0, SIZE, e);
  edge(SIZE, 0, SIZE - e * 1.4, 0, 0.5); g.fillRect(SIZE - e * 1.4, 0, e * 1.4, SIZE);
  edge(0, SIZE, 0, SIZE - e * 1.4, 0.5); g.fillRect(0, SIZE - e * 1.4, SIZE, e * 1.4);
  return c;
}
