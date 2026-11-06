// The race board as a three.js scene, built once per game: lacquered table,
// dark wooden frame, printed cherry board, a wooden die and a turned pawn per
// piece. Matte (Lambert) everywhere; the big flat table and board face are
// unlit with their light baked in, which the box draws faster at the same look.

import * as THREE from "three";
import { RoundedBoxGeometry } from "three/examples/jsm/geometries/RoundedBoxGeometry.js";
import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";
import type { Figures } from "../settings/tv-settings";
import { BOARD, BOARD_Y, TABLE_Y } from "./race-geometry";
import { printBoard } from "./race-print";

export const SHADOW = { x: -0.42, z: -0.58 }; // ground offset per unit of height, sun at +x +z
export const MARK_COLORS = ["#2c6bed", "#f2862e", "#f6c731", "#e5487f"]; // the phone's answer colours, in order
const MARKS = ["●", "▲", "■", "◆"];
const EMPTY_CORNER = "#b9a58a";

export interface Pawn {
  body: THREE.Group;
  shadow: THREE.Mesh;
  /** The turned pawn, shown for "Holzfiguren". */
  wood: THREE.Object3D;
  /** The player's avatar standing on a base in their colour, shown for "Avatare"; null without an image. */
  figure: THREE.Object3D | null;
}
export interface Marker {
  ring: THREE.Mesh;
  target: THREE.Mesh;
  badge: THREE.Sprite;
}
export interface World {
  renderer: THREE.WebGLRenderer;
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  die: THREE.Mesh;
  dieShadow: THREE.Mesh;
  /** `pawns[seat][piece]`, seats in the scene's order. */
  pawns: Pawn[][];
  markers: Marker[];
}

/** Shows every piece as its turned pawn or as its player's avatar (a player without an image keeps the pawn). */
export function showFigures(w: World, style: Figures) {
  for (const p of w.pawns.flat()) {
    const avatar = style === "avatars" && p.figure !== null;
    p.wood.visible = !avatar;
    if (p.figure) p.figure.visible = avatar;
  }
}

const image = (url: string) =>
  new Promise<HTMLImageElement>((ok, fail) => {
    const i = new Image();
    i.onload = () => ok(i);
    i.onerror = () => fail(new Error(`image ${url}`));
    i.src = url;
  });

/** An image, or null if it does not load: a missing avatar picture falls back to the pawn or the initial. */
export const imageOrNull = (url: string) => image(url).catch(() => null);

export function canvasOf(w: number, h: number, draw: (g: CanvasRenderingContext2D) => void) {
  const c = document.createElement("canvas");
  c.width = w;
  c.height = h;
  draw(c.getContext("2d")!);
  return c;
}

function tex(src: HTMLCanvasElement | HTMLImageElement, repeat = 0) {
  const t = src instanceof HTMLCanvasElement ? new THREE.CanvasTexture(src) : new THREE.Texture(src);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 8;
  t.needsUpdate = true;
  if (repeat) {
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.repeat.set(repeat, repeat);
  }
  return t;
}

const lam = (o: THREE.MeshLambertMaterialParameters) => new THREE.MeshLambertMaterial(o);

/** Height of an avatar figure on the board, world units (a pawn is ~1). */
const FIGURE_H = 1.3;

/**
 * `seats[i]`: corner, colour and avatar figure image of the i-th seated player.
 * Both figure styles are built up front so switching them mid-game creates nothing.
 */
export async function buildWorld(
  canvas: HTMLCanvasElement,
  seats: { corner: number; color: string; figure: HTMLImageElement | null }[],
): Promise<World> {
  const [woodBoard, woodFrame, woodTable] = await Promise.all([
    image("/race/board-cherry-veneer.jpg"),
    image("/race/frame-dark-wood.jpg"),
    image("/race/table-lacquered-cherry.jpg"),
  ]);
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.autoClear = false;
  const camera = new THREE.PerspectiveCamera(36, 16 / 9, 0.1, 300);
  const scene = new THREE.Scene();
  scene.background = new THREE.Color("#1a110b");
  const sun = new THREE.DirectionalLight(0xffe2bc, 2.25);
  sun.position.set(5, 12, 7);
  scene.add(sun, new THREE.HemisphereLight(0xfff1de, 0x4a3020, 1.2));
  // what Lambert gives an upward face under this sun and sky (matches within 0.2 %)
  const upLit = new THREE.Color(0xffe2bc)
    .multiplyScalar((2.25 * sun.position.clone().normalize().y) / Math.PI)
    .add(new THREE.Color(0xfff1de).multiplyScalar(1.2 / Math.PI));
  const flat = (o: THREE.MeshBasicMaterialParameters) => new THREE.MeshBasicMaterial({ ...o, color: upLit });

  const blobMat = new THREE.MeshBasicMaterial({
    transparent: true,
    depthWrite: false,
    map: tex(canvasOf(128, 128, (g) => {
      const r = g.createRadialGradient(64, 64, 0, 64, 64, 64);
      r.addColorStop(0, "rgba(20,10,0,0.6)");
      r.addColorStop(0.55, "rgba(20,10,0,0.3)");
      r.addColorStop(1, "rgba(20,10,0,0)");
      g.fillStyle = r;
      g.fillRect(0, 0, 128, 128);
    })),
  });
  const blobGeo = new THREE.PlaneGeometry(1, 1).rotateX(-Math.PI / 2);
  const blob = () => {
    const m = new THREE.Mesh(blobGeo, blobMat);
    scene.add(m);
    return m;
  };

  // table under a lamp over the board: the falloff lives in vertex colours, not in an overlay
  const tableGeo = new THREE.PlaneGeometry(120, 120, 60, 60).rotateX(-Math.PI / 2);
  const tp = tableGeo.attributes.position, tcol: number[] = [];
  for (let i = 0; i < tp.count; i++) {
    const k = 1 - 0.8 * THREE.MathUtils.smoothstep(Math.hypot(tp.getX(i), tp.getZ(i) * 1.25), 8, 22);
    tcol.push(k, k * 0.96, k * 0.92);
  }
  tableGeo.setAttribute("color", new THREE.Float32BufferAttribute(tcol, 3));
  const table = new THREE.Mesh(tableGeo, flat({ map: tex(woodTable, 12), vertexColors: true }));
  table.position.y = TABLE_Y;
  const under = new THREE.Mesh(
    blobGeo,
    new THREE.MeshBasicMaterial({
      transparent: true,
      depthWrite: false,
      // a blur on this small canvas costs nothing; it is big canvases the box CPU is slow with
      map: tex(canvasOf(256, 256, (g) => {
        g.filter = "blur(18px)";
        g.fillStyle = "rgba(10,4,0,0.75)";
        g.beginPath();
        g.roundRect(40, 40, 176, 176, 14);
        g.fill();
      })),
    }),
  );
  under.scale.set(19.5, 1, 19.5);
  under.position.set(SHADOW.x * 0.6, TABLE_Y + 0.005, SHADOW.z * 0.6);
  scene.add(table, under);

  const frameMat = lam({ map: tex(woodFrame, 2) }), out = BOARD + 1.2;
  const slab = new THREE.Mesh(new RoundedBoxGeometry(out, 0.5, out, 3, 0.1), frameMat);
  slab.position.y = BOARD_Y - 0.27; // top just under the printed face, so the two never fight
  scene.add(slab);
  for (const [x, z, w, d] of [[0, -(out - 0.6) / 2, out, 0.6], [0, (out - 0.6) / 2, out, 0.6], [-(out - 0.6) / 2, 0, 0.6, out - 1.2], [(out - 0.6) / 2, 0, 0.6, out - 1.2]]) {
    const rim = new THREE.Mesh(new RoundedBoxGeometry(w, 0.22, d, 3, 0.08), frameMat);
    rim.position.set(x, BOARD_Y + 0.08, z);
    scene.add(rim);
  }
  const colors = [0, 1, 2, 3].map((c) => seats.find((s) => s.corner === c)?.color ?? EMPTY_CORNER);
  const face = new THREE.Mesh(new THREE.PlaneGeometry(BOARD, BOARD).rotateX(-Math.PI / 2), flat({ map: tex(printBoard(woodBoard, colors)) }));
  face.position.y = BOARD_Y;
  scene.add(face);

  // die: board wood with burnt pips; faces +x,-x,+y,-y,+z,-z = 1,6,2,5,3,4
  const pips: Record<number, number[][]> = {
    1: [[64, 64]], 2: [[36, 36], [92, 92]], 3: [[34, 34], [64, 64], [94, 94]], 4: [[36, 36], [92, 36], [36, 92], [92, 92]],
    5: [[34, 34], [94, 34], [64, 64], [34, 94], [94, 94]], 6: [[36, 30], [92, 30], [36, 64], [92, 64], [36, 98], [92, 98]],
  };
  const dieFaces = [1, 6, 2, 5, 3, 4].map((n) => lam({ map: tex(canvasOf(128, 128, (g) => {
    g.drawImage(woodBoard, 0, 0, 256, 256, 0, 0, 128, 128);
    g.fillStyle = "rgba(255,225,180,0.25)";
    g.fillRect(0, 0, 128, 128);
    for (const [x, y] of pips[n]) {
      const r = g.createRadialGradient(x, y, 2, x, y, 12);
      r.addColorStop(0, "#1e0f06"); r.addColorStop(0.7, "#3a1e0c"); r.addColorStop(1, "rgba(58,30,12,0)");
      g.fillStyle = r;
      g.beginPath(); g.arc(x, y, 12, 0, 7); g.fill();
    }
  })) }));
  const die = new THREE.Mesh(new RoundedBoxGeometry(0.85, 0.85, 0.85, 3, 0.14), dieFaces);
  scene.add(die);

  // pawns: turned wood, lacquered in the player colour
  const profile = [[0, 0], [0.34, 0], [0.36, 0.04], [0.33, 0.09], [0.24, 0.14], [0.17, 0.28], [0.13, 0.5], [0.12, 0.6], [0.19, 0.64], [0.19, 0.68], [0.1, 0.72], [0, 0.72]];
  const pawnGeo = mergeGeometries([
    new THREE.LatheGeometry(profile.map(([x, y]) => new THREE.Vector2(x, y)), 24),
    new THREE.SphereGeometry(0.17, 16, 12).translate(0, 0.86, 0),
  ]);
  // avatar figures: the pre-rendered image as a sprite standing on a disc in the player colour
  // (16 alpha-tested sprites held 59.8 fps on the box in the race look test)
  const baseGeo = new THREE.CylinderGeometry(0.3, 0.33, 0.07, 28).translate(0, 0.035, 0);
  const pawns = seats.map((s) => {
    const mat = lam({ color: s.color });
    let figureMat: THREE.SpriteMaterial | null = null, aspect = 1;
    if (s.figure) {
      const map = tex(s.figure);
      renderer.initTexture(map);
      figureMat = new THREE.SpriteMaterial({ map, alphaTest: 0.04 });
      aspect = s.figure.width / s.figure.height;
    }
    return [0, 1, 2, 3].map(() => {
      const body = new THREE.Group();
      const wood = new THREE.Mesh(pawnGeo, mat);
      let figure: THREE.Object3D | null = null;
      if (figureMat) {
        const sprite = new THREE.Sprite(figureMat);
        sprite.center.set(0.5, 0); // stands on its feet
        sprite.scale.set(FIGURE_H * aspect, FIGURE_H, 1);
        sprite.position.y = 0.06;
        figure = new THREE.Group().add(new THREE.Mesh(baseGeo, mat), sprite);
        figure.visible = false;
        body.add(figure);
      }
      body.add(wood);
      scene.add(body);
      return { body, shadow: blob(), wood, figure };
    });
  });

  // move markers: a ring round the piece, a ring on its target and a badge with the phone button's mark
  const markers = MARK_COLORS.map((color, i) => {
    const ring = new THREE.Mesh(new THREE.RingGeometry(0.42, 0.56, 36).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color }));
    const target = new THREE.Mesh(new THREE.RingGeometry(0.3, 0.42, 36).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.85 }));
    const badgeTex = tex(canvasOf(128, 128, (g) => {
      g.beginPath(); g.arc(64, 64, 58, 0, 7); g.fillStyle = "#fff"; g.fill();
      g.beginPath(); g.arc(64, 64, 50, 0, 7); g.fillStyle = color; g.fill();
      g.fillStyle = "#fff"; g.font = "900 56px sans-serif"; g.textAlign = "center"; g.textBaseline = "middle";
      g.fillText(MARKS[i], 64, 68);
    }));
    renderer.initTexture(badgeTex);
    const badge = new THREE.Sprite(new THREE.SpriteMaterial({ map: badgeTex, transparent: true, depthTest: false }));
    badge.scale.set(0.62, 0.62, 1);
    for (const o of [ring, target, badge]) {
      o.visible = false;
      scene.add(o);
    }
    return { ring, target, badge };
  });

  return { renderer, scene, camera, die, dieShadow: blob(), pawns, markers };
}
