// The clocks on the HUD: a bar under the banner running down while a question waits for its answer, and during a debt auction a
// panel with what is up, its value and each bidder's answer so far (sealed: who has bid, never how much), then the result.
// The bars are plain quads scaled every frame, so the countdown is smooth without redrawing a texture.

import * as THREE from "three";
import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { taler } from "../../ui/taler";
import { HUD_FONT } from "../race/race-hud";
import { INK, avatar, label, paint, pill, place, drop, type Label } from "./grand-allee-hud-label";
import type { SeatLook } from "./grand-allee-pieces";

const BAR = { w: 620, h: 10 }, GOLD = "#f4c542", BID = "#86e07a", PASS = "#b9ad9c";
type Quad = THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>;

export class Clocks {
  readonly labels: Label[];
  private panel: Label;
  private secs: Label;
  private track: Quad;
  private bar: Quad;

  constructor(scene: THREE.Scene, private seats: SeatLook[]) {
    this.panel = label(scene, 940, 150);
    this.secs = label(scene, 110, 44);
    this.labels = [this.panel, this.secs];
    const quad = (color: string) => {
      const q = new THREE.Mesh(new THREE.PlaneGeometry(1, 1).translate(0.5, -0.5, 0), new THREE.MeshBasicMaterial({ color, depthTest: false, depthWrite: false }));
      scene.add(q);
      return q;
    };
    this.track = quad("#2a1c12");
    this.bar = quad(INK);
  }

  /** Lays out the clocks below `top` (drawing-buffer pixels, y up) on a screen `W` wide at scale `u`; returns how many labels were redrawn. */
  update(scene: GrandAlleeScene, players: PlayerInfo[], now: number, W: number, top: number, u: number): number {
    const t = scene.turn, a = t.stage === "auction" ? t.auction : null, mover = t.stage === "ask" ? t.player : -1;
    const [open, close] = t.stage === "ask" ? [t.opened_at, t.closes_at] : a ? [a.opened_at, a.closes_at] : [0, 0];
    let y = top - 14 * u, drawn = 0;
    this.panel.mesh.visible = a !== null || t.stage === "sold";
    if (this.panel.mesh.visible) {
      const key = a ? `a|${a.hotel}|${a.value}|${a.bidders}|${a.bid}|${a.passed}` : t.stage === "sold" ? `s|${t.hotel}|${t.winner}|${t.price}` : "";
      if (paint(this.panel, key, (g, w, h) => this.drawPanel(g, w, h, u, scene, players))) drawn++;
      place(this.panel, (W - this.panel.canvas.width) / 2, y);
      y -= this.panel.canvas.height + 12 * u;
    }
    const on = close > open && now < close, left = on ? (close - now) / (close - open) : 0;
    this.track.visible = this.bar.visible = this.secs.mesh.visible = on;
    if (!on) return drawn;
    const w = BAR.w * u, h = BAR.h * u, x = (W - w) / 2;
    this.track.position.set(x, y, 0);
    this.track.scale.set(w, h, 1);
    this.bar.position.set(x, y, 0);
    this.bar.scale.set(Math.max(1, w * left), h, 1);
    this.bar.material.color.set(a ? GOLD : (players.find((p) => p.id === mover)?.color ?? INK));
    const secs = Math.ceil((close - now) / 1000);
    if (paint(this.secs, secs, (g, _w, lh) => {
      g.font = `800 ${26 * u}px ${HUD_FONT}`;
      g.fillStyle = INK;
      g.textBaseline = "middle";
      g.fillText(`${secs} s`, 10 * u, lh / 2 + 2 * u);
    })) drawn++;
    place(this.secs, x + w, y - h / 2 + this.secs.canvas.height / 2);
    return drawn;
  }

  private drawPanel(g: CanvasRenderingContext2D, w: number, h: number, u: number, scene: GrandAlleeScene, players: PlayerInfo[]) {
    const t = scene.turn, hotel = (id: number) => scene.board.hotels[id]?.name ?? "", name = (id: number) => players.find((p) => p.id === id)?.name ?? "Jemand";
    pill(g, 0, 0, w, h, GOLD, 3 * u, 28 * u);
    g.textAlign = "center";
    g.textBaseline = "middle";
    g.fillStyle = INK;
    g.font = `800 ${32 * u}px ${HUD_FONT}`;
    if (t.stage === "sold") {
      g.fillText(t.winner === null ? `Kein Gebot: Die Bank kauft ${hotel(t.hotel)}` : `Zuschlag: ${name(t.winner)} – ${hotel(t.hotel)}`, w / 2, h * 0.36, w - 60 * u);
      g.fillStyle = GOLD;
      g.fillText(`für ${taler(t.price)}`, w / 2, h * 0.7);
      return;
    }
    if (t.stage !== "auction") return;
    const a = t.auction;
    g.fillText(`Versteigerung: ${hotel(a.hotel)} · Wert ${taler(a.value)}`, w / 2, 40 * u, w - 60 * u);
    // each bidder's avatar with their answer so far: bid, passed, or still thinking
    const d = 58 * u, gap = 26 * u, row = a.bidders.length * d + (a.bidders.length - 1) * gap;
    a.bidders.forEach((id, i) => {
      const look = this.seats[scene.seats.findIndex((s) => s.player === id)], cx = (w - row) / 2 + d / 2 + i * (d + gap), cy = h - 50 * u;
      const bid = a.bid.includes(id), passed = a.passed.includes(id);
      g.globalAlpha = passed ? 0.45 : 1;
      if (look) avatar(g, look, cx, cy, d, u);
      g.globalAlpha = 1;
      g.font = `900 ${26 * u}px ${HUD_FONT}`;
      g.fillStyle = bid ? BID : passed ? PASS : INK;
      g.fillText(bid ? "✓" : passed ? "✗" : "…", cx + d / 2, cy - d / 2 + 4 * u);
    });
  }

  dispose() {
    this.labels.forEach(drop);
    for (const q of [this.track, this.bar]) {
      q.geometry.dispose();
      q.material.dispose();
    }
  }
}
