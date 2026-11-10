// The rules cards before the first roll, drawn like the rest of the HUD as canvas labels in the WebGL canvas. The card is drawn
// and uploaded once per card; the step bar that fills over the card's time is a small quad cropped every frame, so nothing is
// redrawn while it runs.

import * as THREE from "three";
import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import type { GrandAlleeTurn } from "../../protocol/GrandAlleeTurn";
import { HUD_FONT } from "../race/race-hud";
import { INK, drop, label, paint, pill, place, type Label } from "./grand-allee-hud-label";
import { genitive, runs, wrap, type Run } from "./grand-allee-intro-text";

type Intro = Extract<GrandAlleeTurn, { stage: "intro" }>;

const GOLD = "#ffc861", TRACK = "rgba(255,244,226,0.22)";
/** The host's three cards. Sizes at 1920 wide; `y` is a middle (or a top, for the bars) below the card's top edge. */
const CARDS = 3, CARD = { w: 1180, h: 662, top: 168, pad: 64, r: 40 };
const KICKER = { size: 26, y: 62 }, TITLE = { size: 76, y: 121 }, LIST = { top: 185, size: 40, line: 48.8, gap: 18, indent: 40 };
const STEP = { w: 92, h: 12, gap: 12, y: 599 }, SKIP = { size: 28 };

function drawCard(g: CanvasRenderingContext2D, w: number, h: number, u: number, t: Intro) {
  const x = CARD.pad * u, tx = x + LIST.indent * u, max = w - tx - CARD.pad * u;
  // the canvas keeps the last card's alpha and alignment
  [g.globalAlpha, g.textAlign] = [1, "left"];
  pill(g, 0, 0, w, h, "", 0, CARD.r * u);
  g.textBaseline = "middle";
  g.fillStyle = INK;
  g.globalAlpha = 0.72;
  g.font = `700 ${KICKER.size * u}px ${HUD_FONT}`;
  g.letterSpacing = `${0.06 * KICKER.size * u}px`;
  g.fillText(`SO GEHT GRAND ALLEE · ${t.card} / ${CARDS}`, x, KICKER.y * u);
  g.letterSpacing = "0px";
  g.globalAlpha = 1;
  g.font = `800 ${TITLE.size * u}px ${HUD_FONT}`;
  g.fillText(t.title, x, TITLE.y * u);
  const font = (r: Run) => `${r.gold ? 800 : 600} ${LIST.size * u}px ${HUD_FONT}`;
  const width = (r: Run) => ((g.font = font(r)), g.measureText(r.text).width);
  let y = (LIST.top + LIST.line / 2) * u;
  for (const line of t.lines) {
    g.beginPath();
    g.arc(x + 11 * u, y, 7 * u, 0, 7);
    g.fillStyle = GOLD;
    g.fill();
    for (const row of wrap(runs(line), max, width)) {
      let rx = tx;
      for (const r of row) {
        const rw = width(r);
        g.fillStyle = r.gold ? GOLD : INK;
        g.fillText(r.text, rx, y);
        rx += rw;
      }
      y += LIST.line * u;
    }
    y += LIST.gap * u;
  }
  // the step bars: the cards shown full, the current one's track (its fill is a quad of its own), the next ones empty
  for (let i = 1; i <= CARDS; i++) {
    g.beginPath();
    g.roundRect((CARD.pad + (i - 1) * (STEP.w + STEP.gap)) * u, STEP.y * u, STEP.w * u, STEP.h * u, (STEP.h / 2) * u);
    g.fillStyle = i < t.card ? INK : TRACK;
    g.fill();
  }
  if (!t.leader) return;
  g.globalAlpha = 0.8;
  g.textAlign = "right";
  g.fillStyle = INK;
  g.font = `700 ${SKIP.size * u}px ${HUD_FONT}`;
  g.fillText(`Überspringen: auf ${genitive(t.leader)} Handy`, w - CARD.pad * u, (STEP.y + STEP.h / 2) * u);
}

export class IntroCard {
  readonly labels: Label[];
  private card: Label;
  private fill: Label;

  constructor(scene: THREE.Scene) {
    this.card = label(scene, CARD.w, CARD.h);
    this.fill = label(scene, STEP.w, STEP.h);
    this.fill.mesh.renderOrder = 1;
    this.labels = [this.card, this.fill];
  }

  /** Shows the card during the intro on a `W` x `H` drawing buffer at scale `u`; returns how many labels were redrawn. */
  update(scene: GrandAlleeScene, now: number, W: number, H: number, u: number): number {
    const t = scene.turn;
    this.card.mesh.visible = this.fill.mesh.visible = t.stage === "intro";
    if (t.stage !== "intro") {
      // the HUD's largest texture: freed once the intro is over (it was never uploaded if the TV came later)
      if (this.card.key !== "") {
        this.card.key = "";
        this.card.tex.dispose();
      }
      return 0;
    }
    let drawn = Number(paint(this.card, `${t.card}|${t.leader}`, (g, w, h) => drawCard(g, w, h, u, t)));
    drawn += Number(paint(this.fill, "bar", (g, w, h) => {
      g.beginPath();
      g.roundRect(0, 0, w, h, h / 2);
      g.fillStyle = INK;
      g.fill();
    }));
    const x = Math.round((W - this.card.canvas.width) / 2), top = H - Math.round(CARD.top * u);
    place(this.card, x, top);
    place(this.fill, x + Math.round((CARD.pad + (t.card - 1) * (STEP.w + STEP.gap)) * u), top - Math.round(STEP.y * u));
    const full = this.fill.canvas.width, p = Math.min(1, Math.max(0, (now - t.opened_at) / (t.closes_at - t.opened_at)));
    // cropped, not squeezed: the bar's rounded start stays round while it fills
    this.fill.mesh.scale.x = Math.max(1, full * p);
    this.fill.tex.repeat.x = this.fill.mesh.scale.x / full;
    return drawn;
  }

  dispose() {
    this.labels.forEach(drop);
  }
}
