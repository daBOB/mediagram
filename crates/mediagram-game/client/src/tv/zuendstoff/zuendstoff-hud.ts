// The game's name and what is happening, top left, and every player's name with their lives under their feet: canvas text
// drawn as quads in the 3D canvas's own frame (HTML over the WebGL canvas cost the TV box frames). Labels are sized at layout
// and drawn again only when their text changes.

import * as THREE from "three";
import type { ZuendstoffScene } from "../../protocol/ZuendstoffScene";
import { drop, fit, label, paint, place, type Label } from "../grand-allee/grand-allee-hud-label";
import { HUD_FONT } from "../table/hud-font";
import type { Seat } from "./zuendstoff-circle";

const INK = "#fbf8f1", SOFT = "#a9cdbf", HEART = "#e5487f";

/** What is happening, for the line under the title. */
export function statusLine(scene: ZuendstoffScene, nameOf: (id: number) => string, now: number): string {
  const st = scene.stage;
  switch (st.stage) {
    case "hold":
      return st.dropped ? `${nameOf(st.holder)} hebt die Bombe auf` : `${nameOf(st.holder)} hat die Bombe`;
    case "flight":
      if (now >= st.lands_at && st.caught !== null) return st.caught ? `${nameOf(st.to)} fängt sie!` : `${nameOf(st.to)} lässt sie fallen!`;
      return `${nameOf(st.from)} wirft zu ${nameOf(st.to)}`;
    case "boom":
      return `BUMM! ${nameOf(st.victim)} ${st.out ? "ist raus" : "verliert ein Leben"}`;
    case "final":
      return `${nameOf(st.order[0])} gewinnt!`;
  }
}

export class ZuendstoffHud {
  private readonly scene = new THREE.Scene();
  private readonly camera = new THREE.OrthographicCamera(0, 1920, 1080, 0, -1, 1);
  private readonly size = new THREE.Vector2();
  private readonly feet = new THREE.Vector3();
  private readonly title: Label;
  private readonly tags: Label[];
  private u = 1;

  constructor(private readonly renderer: THREE.WebGLRenderer, private readonly seats: Seat[], private readonly names: string[]) {
    this.title = label(this.scene, 1100, 118);
    this.tags = seats.map(() => label(this.scene, 300, 86));
    // the board is tone-mapped; the HUD keeps its colours
    for (const l of [this.title, ...this.tags]) l.mesh.material.toneMapped = false;
  }

  /** After a resize: sizes every label and puts the name tags under the feet as `camera` (fixed) sees them. */
  layout(camera: THREE.Camera, scene: ZuendstoffScene, now: number) {
    const { x: W, y: H } = this.renderer.getDrawingBufferSize(this.size);
    this.u = W / 1920;
    Object.assign(this.camera, { right: W, top: H });
    this.camera.updateProjectionMatrix();
    for (const l of [this.title, ...this.tags]) fit(l, this.u);
    camera.updateMatrixWorld();
    this.seats.forEach((s, i) => {
      const v = this.feet.copy(s.pos).setY(-0.1).project(camera), tag = this.tags[i];
      place(tag, (v.x * 0.5 + 0.5) * W - tag.canvas.width / 2, (v.y * 0.5 + 0.5) * H - 2 * this.u);
    });
    place(this.title, 40 * this.u, H - 28 * this.u);
    this.update(scene, now);
    for (const l of [this.title, ...this.tags]) this.renderer.initTexture(l.tex);
  }

  update(scene: ZuendstoffScene, now: number) {
    const u = this.u, nameOf = (id: number) => this.names[scene.seats.findIndex((s) => s.player === id)] ?? "Jemand";
    const line = `Runde ${scene.round} · ${statusLine(scene, nameOf, now)}`;
    paint(this.title, line, (g) => {
      shade(g, u);
      g.textBaseline = "top";
      g.font = `800 ${44 * u}px ${HUD_FONT}`;
      g.fillStyle = INK;
      g.fillText("Zündstoff", 0, 6 * u);
      g.font = `700 ${30 * u}px ${HUD_FONT}`;
      g.fillStyle = SOFT;
      g.fillText(line, 0, 64 * u);
    });
    scene.seats.forEach((seat, i) => {
      const tag = this.tags[i];
      if (!tag) return;
      paint(tag, seat.lives, (g, w) => {
        shade(g, u);
        g.globalAlpha = seat.lives === 0 ? 0.45 : 1;
        g.textAlign = "center";
        g.textBaseline = "top";
        g.font = `800 ${36 * u}px ${HUD_FONT}`;
        g.fillStyle = INK;
        g.fillText(this.names[i], w / 2, 4 * u, w - 8 * u);
        g.font = `700 ${28 * u}px ${HUD_FONT}`;
        g.fillStyle = seat.lives === 0 ? SOFT : HEART;
        g.fillText(seat.lives === 0 ? "raus" : "♥".repeat(seat.lives), w / 2, 48 * u);
      });
    });
  }

  compile() {
    this.renderer.compile(this.scene, this.camera);
  }
  render() {
    this.renderer.render(this.scene, this.camera);
  }
  dispose() {
    [this.title, ...this.tags].forEach(drop);
  }
}

/** Text over the felt and the dark room: a soft shadow under every letter. */
function shade(g: CanvasRenderingContext2D, u: number) {
  g.shadowColor = "rgba(0,0,0,0.85)";
  g.shadowBlur = 8 * u;
  g.shadowOffsetY = 2 * u;
}
