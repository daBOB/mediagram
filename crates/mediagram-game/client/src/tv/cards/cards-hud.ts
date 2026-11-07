// Player tags, card counts, the banner and the standing note, drawn as textured
// quads inside the 3D canvas (HTML over the WebGL canvas cost the box frames).
// Every text the game can show is drawn and uploaded at load: a texture made
// mid-game stalled the box GPU for a frame or two. Counts come from one number
// atlas, so they need no texture of their own.

import * as THREE from "three";
import type { Spot } from "./cards-layout";
import { COLS, HUD_FONT, ROWS, printNumbers } from "./cards-print";
import { canvasOf } from "./cards-world";

export interface HudPlayer {
  name: string;
  color: string;
  /** The avatar's head, drawn in the colour circle; without it the initial. */
  head: HTMLImageElement | null;
}

interface Item {
  mesh: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>;
  w: number;
  h: number;
}

const measure = canvasOf(1, 1, () => {}).getContext("2d")!;

export class CardsHud {
  readonly scene = new THREE.Scene();
  private camera = new THREE.OrthographicCamera(0, 1920, 1080, 0, -1, 1);
  private cache = new Map<string, { tex: THREE.CanvasTexture; w: number; h: number }>();
  private tags: Item[];
  private counts: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>[];
  private banner: Item;
  private note: Item;
  private numbers = new THREE.CanvasTexture(printNumbers());
  private u = 1;
  private anchors: { x: number; y: number }[] = [];
  private tagState: string[];
  private bannerKey: [number, string] | null = null;
  private noteKey: string | null = null;

  constructor(
    private renderer: THREE.WebGLRenderer,
    private camera3d: THREE.PerspectiveCamera,
    private players: HudPlayer[],
    private spots: Spot[],
    private texts: { banner: string[]; note: string[]; marker: string[] },
  ) {
    this.numbers.colorSpace = THREE.SRGBColorSpace;
    this.numbers.generateMipmaps = false;
    this.numbers.minFilter = THREE.LinearFilter;
    this.tags = players.map(() => this.item());
    this.banner = this.item();
    this.note = this.item();
    this.counts = players.map(() => {
      const t = this.numbers.clone();
      t.needsUpdate = true;
      this.renderer.initTexture(t);
      t.repeat.set(1 / COLS, 1 / ROWS);
      const mesh = new THREE.Mesh(
        new THREE.PlaneGeometry(1, 1).translate(0.5, -0.5, 0),
        new THREE.MeshBasicMaterial({ map: t, transparent: true, depthTest: false, depthWrite: false }),
      );
      mesh.visible = false;
      this.scene.add(mesh);
      return mesh;
    });
    this.tagState = players.map(() => "");
  }

  private item(): Item {
    const mesh = new THREE.Mesh(
      new THREE.PlaneGeometry(1, 1).translate(0.5, -0.5, 0),
      new THREE.MeshBasicMaterial({ transparent: true, depthTest: false, depthWrite: false }),
    );
    mesh.visible = false;
    this.scene.add(mesh);
    return { mesh, w: 0, h: 0 };
  }

  private paint(item: Item, key: string, w: number, h: number, draw: (g: CanvasRenderingContext2D) => void) {
    let e = this.cache.get(key);
    if (!e) {
      const c = canvasOf(Math.ceil(w), Math.ceil(h), draw);
      const tex = new THREE.CanvasTexture(c);
      tex.colorSpace = THREE.SRGBColorSpace;
      tex.generateMipmaps = false;
      tex.minFilter = THREE.LinearFilter;
      this.renderer.initTexture(tex);
      e = { tex, w: c.width, h: c.height };
      this.cache.set(key, e);
    }
    if (item.mesh.material.map !== e.tex) {
      item.mesh.material.map = e.tex;
      item.mesh.material.needsUpdate = true;
    }
    item.mesh.scale.set(e.w, e.h, 1);
    item.mesh.visible = true;
    item.w = e.w;
    item.h = e.h;
  }

  private pill(g: CanvasRenderingContext2D, w: number, h: number, border: number) {
    g.beginPath();
    g.roundRect(border / 2 + 1, border / 2 + 1, w - border - 2, h - border - 2, h / 2);
    g.fillStyle = "rgba(28,18,11,0.84)";
    g.fill();
    if (border) {
      g.lineWidth = border;
      g.stroke();
    }
  }

  setTag(seat: number, active: boolean, marker: string | null, count: number) {
    const mesh = this.counts[seat], n = Math.min(count, COLS * ROWS - 1);
    mesh.material.map!.offset.set((n % COLS) / COLS, 1 - (Math.floor(n / COLS) + 1) / ROWS);
    mesh.visible = true;
    const state = `${active}|${marker ?? ""}`;
    if (this.tagState[seat] !== state) {
      this.tagState[seat] = state;
      this.drawTag(seat, active, marker);
    }
    this.place();
  }

  private drawTag(seat: number, active: boolean, marker: string | null) {
    const p = this.players[seat], u = this.u, d = 56 * u, pad = 9 * u;
    measure.font = `800 ${28 * u}px ${HUD_FONT}`;
    const nw = measure.measureText(p.name).width;
    measure.font = `800 ${22 * u}px ${HUD_FONT}`;
    const mw = marker ? measure.measureText(marker).width : 0;
    const w = pad + d + 14 * u + Math.max(nw, mw) + 26 * u, h = d + pad * 2;
    this.paint(this.tags[seat], `tag${seat}|${active}|${marker ?? ""}`, w, h, (g) => {
      g.strokeStyle = active ? "#f6c731" : p.color;
      this.pill(g, w, h, (active ? 5 : 0) * u);
      const cx = pad + d / 2, cy = h / 2;
      g.beginPath();
      g.arc(cx, cy, d / 2, 0, 7);
      g.fillStyle = p.color;
      g.fill();
      if (p.head) {
        const r = d / 2 - 4 * u;
        g.save();
        g.beginPath();
        g.arc(cx, cy, r, 0, 7);
        g.clip();
        g.drawImage(p.head, cx - r, cy - r, r * 2, r * 2);
        g.restore();
      } else {
        g.fillStyle = "#1c120b";
        g.font = `800 ${30 * u}px ${HUD_FONT}`;
        g.textAlign = "center";
        g.textBaseline = "middle";
        g.fillText([...p.name.trim()][0]?.toUpperCase() ?? "?", cx, cy + 2 * u);
      }
      g.textAlign = "left";
      g.textBaseline = "alphabetic";
      g.fillStyle = "#fff4e2";
      g.font = `800 ${28 * u}px ${HUD_FONT}`;
      g.fillText(p.name, pad + d + 14 * u, marker ? h / 2 - 2 * u : h / 2 + 10 * u);
      if (marker) {
        g.fillStyle = "#f6c731";
        g.font = `800 ${22 * u}px ${HUD_FONT}`;
        g.fillText(marker, pad + d + 14 * u, h / 2 + 22 * u);
      }
    });
  }

  setBanner(seat: number, rest: string) {
    if (this.bannerKey?.[0] === seat && this.bannerKey[1] === rest) return;
    this.bannerKey = [seat, rest];
    const p = this.players[seat], u = this.u, font = `800 ${34 * u}px ${HUD_FONT}`;
    measure.font = font;
    const a = measure.measureText(p.name).width, b = measure.measureText(rest).width, h = 62 * u, w = a + b + 80 * u;
    this.paint(this.banner, `ban${seat}${rest}`, w, h, (g) => {
      this.pill(g, w, h, 0);
      g.font = font;
      g.textBaseline = "middle";
      g.fillStyle = p.color;
      g.fillText(p.name, 40 * u, h / 2 + 2 * u);
      g.fillStyle = "#fff4e2";
      g.fillText(rest, 40 * u + a, h / 2 + 2 * u);
    });
    this.place();
  }

  setNote(text: string | null) {
    if (this.noteKey === text) return;
    this.noteKey = text;
    if (text === null) {
      this.note.mesh.visible = false;
      return;
    }
    const u = this.u, font = `800 ${34 * u}px ${HUD_FONT}`;
    measure.font = font;
    const w = measure.measureText(text).width + 60 * u, h = 60 * u;
    this.paint(this.note, `note${text}`, w, h, (g) => {
      g.beginPath();
      g.roundRect(1, 1, w - 2, h - 2, h / 2);
      g.fillStyle = "#f6c731";
      g.fill();
      g.font = font;
      g.fillStyle = "#1c120b";
      g.textAlign = "center";
      g.textBaseline = "middle";
      g.fillText(text, w / 2, h / 2 + 2 * u);
    });
    this.place();
  }

  /** After a resize: new scale, every text drawn again up front, tags put at their seats. */
  layout() {
    const size = this.renderer.getDrawingBufferSize(new THREE.Vector2());
    this.u = size.x / 1920;
    this.camera.right = size.x;
    this.camera.top = size.y;
    this.camera.updateProjectionMatrix();
    this.camera3d.updateMatrixWorld();
    this.anchors = this.spots.map((s) => this.project(s.tagX, s.tagZ, size));
    for (const e of this.cache.values()) e.tex.dispose();
    this.cache.clear();
    const tags = [...this.tagState], banner = this.bannerKey, note = this.noteKey;
    this.players.forEach((_, seat) => {
      for (const active of [true, false]) for (const marker of [null, ...this.texts.marker]) this.drawTag(seat, active, marker);
      for (const rest of this.texts.banner) this.setBanner(seat, rest);
    });
    for (const text of this.texts.note) {
      this.noteKey = null;
      this.setNote(text);
    }
    // put back what was showing (the pre-draw left the last drawn state up)
    this.tagState = this.players.map(() => "");
    tags.forEach((t, seat) => {
      if (!t) return;
      const [active, marker] = t.split("|");
      this.tagState[seat] = t;
      this.drawTag(seat, active === "true", marker || null);
    });
    this.bannerKey = null;
    if (banner) this.setBanner(...banner);
    else this.banner.mesh.visible = false;
    this.noteKey = null;
    this.setNote(note);
    this.place();
  }

  private project(x: number, z: number, size: THREE.Vector2) {
    const v = new THREE.Vector3(x, 0, z).project(this.camera3d);
    return { x: ((v.x + 1) / 2) * size.x, y: ((v.y + 1) / 2) * size.y };
  }

  private place() {
    const size = this.renderer.getDrawingBufferSize(new THREE.Vector2()), m = 30 * this.u, c = 44 * this.u;
    this.players.forEach((_, seat) => {
      const it = this.tags[seat], a = this.anchors[seat];
      if (!a) return;
      const total = it.w + c + 8 * this.u;
      const x = Math.min(size.x - m - total, Math.max(m, a.x - total / 2));
      // below the banner at the top, above the edge at the bottom
      const y = Math.min(size.y - m - 74 * this.u, Math.max(m + it.h, a.y + it.h / 2));
      it.mesh.position.set(Math.round(x), Math.round(y), 0);
      const badge = this.counts[seat];
      badge.scale.set(c, c, 1);
      badge.position.set(Math.round(x + it.w + 8 * this.u), Math.round(y - (it.h - c) / 2), 0);
    });
    this.banner.mesh.position.set(Math.round((size.x - this.banner.w) / 2), Math.round(size.y - m), 0);
    const centre = this.project(0, -2.6, size);
    this.note.mesh.position.set(Math.round(centre.x - this.note.w / 2), Math.round(centre.y + this.note.h / 2), 0);
  }

  render() {
    this.renderer.render(this.scene, this.camera);
  }

  dispose() {
    for (const e of this.cache.values()) e.tex.dispose();
    this.numbers.dispose();
  }
}
