// Player tags in the board corners and the turn banner, drawn as textured
// quads inside the 3D canvas: HTML over the WebGL canvas cost the box 3–8 fps.
// Every state a game can show is drawn and uploaded at load, because a
// texture created mid-game stalled the box GPU for a frame or two.

import * as THREE from "three";
import { CORNER_LEFT, CORNER_TOP } from "./race-geometry";
import { canvasOf } from "./race-world";

export const HUD_FONT = '"Bricolage Grotesque", "Atkinson Hyperlegible", system-ui, sans-serif';

export interface HudPlayer {
  name: string;
  color: string;
  corner: number;
  /** The avatar's head, drawn in the colour circle; without it the initial. */
  head: HTMLImageElement | null;
}

interface Item {
  mesh: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>;
  w: number;
  h: number;
}

const measure = canvasOf(1, 1, () => {}).getContext("2d")!;

export class Hud {
  readonly scene = new THREE.Scene();
  private camera = new THREE.OrthographicCamera(0, 1920, 1080, 0, -1, 1);
  private cache = new Map<string, { tex: THREE.CanvasTexture; w: number; h: number }>();
  private tags: Item[];
  private banner: Item;
  private u = 1;
  private bannerKey: [number, string] | null = null;
  private tagState: string[];

  constructor(
    private renderer: THREE.WebGLRenderer,
    private players: HudPlayer[],
    private bannerTexts: string[],
  ) {
    this.tags = players.map(() => this.item());
    this.banner = this.item();
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

  /** `label`: the line under the name, e.g. "2 im Ziel" or "1. Platz". */
  setTag(seat: number, active: boolean, label: string) {
    if (this.tagState[seat] === `${active}|${label}`) return;
    const p = this.players[seat], u = this.u, d = 64 * u, pad = 10 * u;
    measure.font = `800 ${30 * u}px ${HUD_FONT}`;
    const nw = measure.measureText(p.name).width;
    measure.font = `700 ${22 * u}px ${HUD_FONT}`;
    const lw = measure.measureText(label).width;
    const w = pad + d + 16 * u + Math.max(nw, lw) + 30 * u, h = d + pad * 2;
    this.tagState[seat] = `${active}|${label}`;
    this.paint(this.tags[seat], `tag${seat}${active}${label}`, w, h, (g) => {
      g.strokeStyle = p.color;
      this.pill(g, w, h, active ? 4 * u : 0);
      const cx = pad + d / 2, cy = h / 2;
      g.beginPath();
      g.arc(cx, cy, d / 2, 0, 7);
      g.fillStyle = p.color;
      g.fill();
      if (p.head) {
        // the head inside a ring of the player's colour, as in the lobby
        const r = d / 2 - 4 * u;
        g.save();
        g.beginPath();
        g.arc(cx, cy, r, 0, 7);
        g.clip();
        g.drawImage(p.head, cx - r, cy - r, r * 2, r * 2);
        g.restore();
      } else {
        g.fillStyle = "#1c120b";
        g.font = `800 ${34 * u}px ${HUD_FONT}`;
        g.textAlign = "center";
        g.textBaseline = "middle";
        g.fillText([...p.name.trim()][0]?.toUpperCase() ?? "?", cx, cy + 2 * u);
      }
      g.textAlign = "left";
      g.textBaseline = "alphabetic";
      g.fillStyle = "#fff4e2";
      g.font = `800 ${30 * u}px ${HUD_FONT}`;
      g.fillText(p.name, pad + d + 16 * u, h / 2 - 1 * u);
      g.globalAlpha = 0.72;
      g.font = `700 ${22 * u}px ${HUD_FONT}`;
      g.fillText(label, pad + d + 16 * u, h / 2 + 25 * u);
    });
    this.place();
  }

  setBanner(seat: number, rest: string) {
    if (this.bannerKey?.[0] === seat && this.bannerKey[1] === rest) return;
    this.bannerKey = [seat, rest];
    const p = this.players[seat], u = this.u, font = `800 ${36 * u}px ${HUD_FONT}`;
    measure.font = font;
    const a = measure.measureText(p.name).width, b = measure.measureText(rest).width, h = 66 * u, w = a + b + 84 * u;
    this.paint(this.banner, `ban${seat}${rest}`, w, h, (g) => {
      this.pill(g, w, h, 0);
      g.font = font;
      g.textBaseline = "middle";
      g.fillStyle = p.color;
      g.fillText(p.name, 42 * u, h / 2 + 2 * u);
      g.fillStyle = "#fff4e2";
      g.fillText(rest, 42 * u + a, h / 2 + 2 * u);
    });
    this.place();
  }

  /** After a resize: new scale, every state drawn again up front. */
  layout() {
    const size = this.renderer.getDrawingBufferSize(new THREE.Vector2());
    this.u = size.x / 1920;
    this.camera.right = size.x;
    this.camera.top = size.y;
    this.camera.updateProjectionMatrix();
    for (const e of this.cache.values()) e.tex.dispose();
    this.cache.clear();
    const tags = [...this.tagState], banner = this.bannerKey;
    this.tagState = this.players.map(() => "");
    this.bannerKey = null;
    this.players.forEach((_, seat) => {
      for (const active of [true, false]) for (let n = 0; n <= 4; n++) this.setTag(seat, active, `${n} im Ziel`);
      for (const rest of this.bannerTexts) this.setBanner(seat, rest);
    });
    // put back what was showing (the pre-draw left the last drawn state up)
    const drawn = [...this.tagState];
    tags.forEach((t, seat) => {
      const [active, label] = (t || drawn[seat]).split("|");
      this.tagState[seat] = "";
      this.setTag(seat, active === "true", label);
    });
    this.bannerKey = null;
    if (banner) this.setBanner(...banner);
    else this.banner.mesh.visible = false;
  }

  private place() {
    const size = this.renderer.getDrawingBufferSize(new THREE.Vector2()), m = 42 * this.u;
    this.players.forEach((p, seat) => {
      const it = this.tags[seat];
      const x = CORNER_LEFT[p.corner] ? m : size.x - it.w - m;
      const top = CORNER_TOP[p.corner] ? m : size.y - it.h - m;
      it.mesh.position.set(Math.round(x), Math.round(size.y - top), 0);
    });
    this.banner.mesh.position.set(Math.round((size.x - this.banner.w) / 2), Math.round(size.y - m), 0);
  }

  render() {
    this.renderer.render(this.scene, this.camera);
  }

  dispose() {
    for (const e of this.cache.values()) e.tex.dispose();
  }
}
