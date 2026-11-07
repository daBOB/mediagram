// The round counter, the turn banner, the die's result and the player tags, drawn into small canvases that sit as
// textured quads in the 3D canvas's own frame (HTML over a WebGL canvas cost the TV box frames). Each label owns one
// texture, uploaded at load and redrawn in place when its text changes; nothing is created while the game runs.

import * as THREE from "three";
import type { BauherrScene } from "../../protocol/BauherrScene";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { HUD_FONT } from "../race/race-hud";
import { describeTurn, taler } from "./bauherr-status";

export interface TagLook {
  name: string;
  color: string;
  head: HTMLImageElement | null;
}
interface Label {
  mesh: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>;
  canvas: HTMLCanvasElement;
  tex: THREE.CanvasTexture;
  key: string;
  w: number;
  h: number;
}
const INK = "#fff4e2", PILL = "rgba(28,18,11,0.84)";

export class BauherrHud {
  private scene = new THREE.Scene();
  private camera = new THREE.OrthographicCamera(0, 1920, 1080, 0, -1, 1);
  private u = 1;
  private round: Label;
  private banner: Label;
  private result: Label;
  private tags: Label[];
  private tagW = 0;

  constructor(private renderer: THREE.WebGLRenderer, private seats: TagLook[]) {
    this.round = this.label(300, 66);
    this.banner = this.label(1500, 66);
    this.result = this.label(560, 104);
    this.tags = seats.map(() => this.label(330, 84));
  }

  private label(w: number, h: number): Label {
    const canvas = document.createElement("canvas"), tex = new THREE.CanvasTexture(canvas);
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.generateMipmaps = false;
    tex.minFilter = THREE.LinearFilter;
    const mesh = new THREE.Mesh(new THREE.PlaneGeometry(1, 1).translate(0.5, -0.5, 0), new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthTest: false, depthWrite: false }));
    this.scene.add(mesh);
    return { mesh, canvas, tex, key: "", w, h };
  }
  private paint(l: Label, key: string, draw: (g: CanvasRenderingContext2D, w: number, h: number) => void) {
    if (l.key === key) return;
    l.key = key;
    const w = Math.ceil(l.w * this.u), h = Math.ceil(l.h * this.u);
    if (l.canvas.width !== w || l.canvas.height !== h) {
      l.canvas.width = w;
      l.canvas.height = h;
    }
    const g = l.canvas.getContext("2d")!;
    g.clearRect(0, 0, w, h);
    draw(g, w, h);
    l.tex.needsUpdate = true;
    l.mesh.scale.set(w, h, 1);
  }
  private pill(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, border = "", bw = 0) {
    g.beginPath();
    g.roundRect(x + bw / 2, y + bw / 2, w - bw, h - bw, h / 2);
    g.fillStyle = PILL;
    g.fill();
    if (bw) {
      g.lineWidth = bw;
      g.strokeStyle = border;
      g.stroke();
    }
  }

  /** After a resize: the scale, and everything drawn again at it. */
  layout(scene: BauherrScene, players: PlayerInfo[], now: number) {
    const size = this.renderer.getDrawingBufferSize(new THREE.Vector2());
    this.u = size.x / 1920;
    this.camera.right = size.x;
    this.camera.top = size.y;
    this.camera.updateProjectionMatrix();
    for (const l of [this.round, this.banner, this.result, ...this.tags]) l.key = "";
    const n = this.seats.length;
    this.tagW = Math.min(330, (1920 - 84 - 14 * (n - 1)) / Math.max(1, n));
    this.tags.forEach((t) => (t.w = this.tagW));
    // every canvas gets its final size before its texture is uploaded: a texture cannot grow after that
    for (const l of [this.round, this.banner, this.result, ...this.tags]) {
      l.tex.dispose();
      l.canvas.width = Math.ceil(l.w * this.u);
      l.canvas.height = Math.ceil(l.h * this.u);
      l.mesh.scale.set(l.canvas.width, l.canvas.height, 1);
    }
    this.update(scene, players, now);
    for (const l of [this.round, this.banner, this.result, ...this.tags]) this.renderer.initTexture(l.tex);
  }

  update(scene: BauherrScene, players: PlayerInfo[], now: number) {
    const u = this.u, size = this.renderer.getDrawingBufferSize(new THREE.Vector2()), m = 42 * u, byId = new Map(players.map((p) => [p.id, p]));
    const onTurn = "player" in scene.turn ? scene.turn.player : scene.turn.stage === "final" ? scene.turn.winner : -1;
    const text = describeTurn(scene, players, now);

    const roundText = `Runde ${scene.round} / ${scene.rounds}`;
    this.paint(this.round, roundText, (g, w, h) => {
      this.pill(g, 0, 0, w, h);
      g.font = `800 ${34 * u}px ${HUD_FONT}`;
      g.fillStyle = INK;
      g.textBaseline = "middle";
      g.textAlign = "center";
      g.fillText(roundText, w / 2, h / 2 + 2 * u);
    });
    this.place(this.round, m, size.y - m);

    const who = byId.get(onTurn), name = who?.name ?? "";
    this.paint(this.banner, `${onTurn}|${text.text}`, (g, w, h) => {
      g.font = `800 ${36 * u}px ${HUD_FONT}`;
      g.textBaseline = "middle";
      const lead = name && text.text.startsWith(name) ? name : "", rest = text.text.slice(lead.length);
      const a = g.measureText(lead).width, tw = Math.min(w - 40 * u, a + g.measureText(rest).width + 84 * u), x = (w - tw) / 2;
      this.pill(g, x, 0, tw, h);
      g.fillStyle = who?.color ?? INK;
      g.fillText(lead, x + 42 * u, h / 2 + 2 * u);
      g.fillStyle = INK;
      g.fillText(rest, x + 42 * u + a, h / 2 + 2 * u, w - 124 * u - a);
    });
    this.place(this.banner, (size.x - this.banner.canvas.width) / 2, size.y - m);

    // the die's result, big, while there is one to show
    const show = text.die && text.die !== "…" ? text.die : "";
    this.result.mesh.visible = show !== "";
    if (show) {
      this.paint(this.result, show, (g, w, h) => {
        g.font = `800 ${60 * u}px ${HUD_FONT}`;
        const tw = Math.min(w, Math.max(240 * u, g.measureText(show).width + 90 * u)), x = (w - tw) / 2;
        this.pill(g, x, 0, tw, h);
        g.fillStyle = INK;
        g.textAlign = "center";
        g.textBaseline = "middle";
        g.fillText(show, w / 2, h / 2 + 4 * u);
      });
      this.place(this.result, size.x * 0.84 - this.result.canvas.width / 2, size.y * 0.34);
    }

    const hotels = new Map<number, number>();
    for (const e of scene.estates) hotels.set(e.owner, (hotels.get(e.owner) ?? 0) + 1);
    const gap = 14 * u, row = scene.seats.length * this.tagW * u + gap * (scene.seats.length - 1), x0 = (size.x - row) / 2;
    scene.seats.forEach((seat, i) => {
      const look = this.seats[i], tag = this.tags[i], active = seat.player === onTurn, n = hotels.get(seat.player) ?? 0;
      const line = seat.out ? "raus" : `${taler(seat.cash, true)} · ${n} ${n === 1 ? "Hotel" : "Hotels"}`;
      this.paint(tag, `${active}|${seat.out}|${line}`, (g, w, h) => {
        g.globalAlpha = seat.out ? 0.5 : 1;
        this.pill(g, 0, 0, w, h, look.color, active ? 4 * u : 0);
        const d = h - 16 * u, cx = 8 * u + d / 2, cy = h / 2;
        g.beginPath();
        g.arc(cx, cy, d / 2, 0, 7);
        g.fillStyle = look.color;
        g.fill();
        if (look.head) {
          g.save();
          g.beginPath();
          g.arc(cx, cy, d / 2 - 3 * u, 0, 7);
          g.clip();
          g.drawImage(look.head, cx - d / 2, cy - d / 2, d, d);
          g.restore();
        }
        g.textBaseline = "alphabetic";
        g.textAlign = "left";
        g.fillStyle = INK;
        g.font = `800 ${26 * u}px ${HUD_FONT}`;
        g.fillText(look.name, 8 * u + d + 12 * u, h / 2 - 3 * u, w - d - 40 * u);
        g.globalAlpha *= 0.78;
        g.font = `700 ${19 * u}px ${HUD_FONT}`;
        g.fillText(line, 8 * u + d + 12 * u, h / 2 + 22 * u, w - d - 40 * u);
      });
      this.place(tag, x0 + i * (this.tagW * u + gap), m + tag.canvas.height);
    });
  }

  private place(l: Label, x: number, yTop: number) {
    l.mesh.position.set(Math.round(x), Math.round(yTop), 0);
  }
  compile() {
    this.renderer.compile(this.scene, this.camera);
  }
  render() {
    this.renderer.render(this.scene, this.camera);
  }
  dispose() {
    for (const l of [this.round, this.banner, this.result, ...this.tags]) {
      l.tex.dispose();
      l.mesh.geometry.dispose();
      l.mesh.material.dispose();
    }
  }
}
