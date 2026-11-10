// Three arena looks for the Kopfnuss mockup. Each builds the set and its props: the category plinths,
// the summit for the climb and the winner's pedestal. Cheap on purpose: Lambert and unlit materials,
// canvas textures, no shadows or environment maps (the TV box's budget).
import * as THREE from "three";
import { backdrop, chaseRing, columnHaze, DITHER, edgeBox, edgeDrum, floor, gridFloor, gridWall, spotCone } from "./arena-fx.js?v=fx7";

const FX = new URLSearchParams(location.search).get("fx") === "1";

const COLORS = { blue: "#2c6bed", orange: "#f2862e", yellow: "#f6c731", pink: "#e5487f" };
const MARKS = { blue: "●", orange: "▲", yellow: "■", pink: "◆" };

function tex(w, h, draw) {
  const cv = document.createElement("canvas");
  cv.width = w;
  cv.height = h;
  draw(cv.getContext("2d"), w, h);
  const t = new THREE.CanvasTexture(cv);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 4;
  return t;
}
const gradient = (stops) => tex(4, 512, (g, w, h) => {
  const l = g.createLinearGradient(0, 0, 0, h);
  stops.forEach(([at, c]) => l.addColorStop(at, c));
  g.fillStyle = l;
  g.fillRect(0, 0, w, h);
});
const lambert = (color, extra = {}) => new THREE.MeshLambertMaterial({ color, ...extra });
const glow = (color, opacity = 0.16) =>
  new THREE.MeshBasicMaterial({ color, transparent: true, opacity, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide });

/** A category sign: a cream card with the answer colour's band, its mark and the name. */
const sign = (color, label) => tex(512, 680, (g, w, h) => {
  g.fillStyle = "#fbf8f1";
  g.beginPath();
  g.roundRect(0, 0, w, h, 56);
  g.fill();
  g.fillStyle = COLORS[color];
  g.beginPath();
  g.roundRect(0, 0, w, 250, [56, 56, 0, 0]);
  g.fill();
  g.fillStyle = "#0c2a22";
  g.font = "800 150px 'Bricolage Grotesque'";
  g.textAlign = "center";
  g.fillText(MARKS[color], w / 2, 185);
  g.font = `800 ${label.length > 9 ? 70 : 84}px 'Bricolage Grotesque'`;
  g.fillText(label, w / 2, 470);
});

function spotlight(scene, at, color = 0xfff3c4) {
  const cone = new THREE.Mesh(new THREE.ConeGeometry(2.3, 11, 40, 8, true), FX ? spotCone(color) : glow(color, 0.14));
  cone.position.copy(at).add(new THREE.Vector3(0, 5.5, 0));
  scene.add(cone);
  const pool = new THREE.Mesh(new THREE.CircleGeometry(2.3, 40), glow(color, 0.22));
  pool.rotation.x = -Math.PI / 2;
  pool.position.copy(at).setY(0.03);
  scene.add(pool);
}

function chips(scene, at, n, colors, r = 0.42, h = 0.13) {
  for (let i = 0; i < n; i++) {
    const chip = new THREE.Mesh(new THREE.CylinderGeometry(r, r, h, 32), lambert(colors[i % colors.length]));
    chip.position.copy(at).setY(at.y + h / 2 + i * h * 1.02);
    chip.rotation.y = i * 0.7;
    scene.add(chip);
  }
}

/** The categories stand as big signs at the back; votes are chips in front of each; the chosen one rises into a light. */
function plinths(look) {
  return (scene, cats, { votes, chosen = null }) => {
    cats.forEach(([color, label], i) => {
      const x = (i - 1) * 4.2, z = -3.2, lifted = chosen === i ? 0.9 : 0;
      const base = new THREE.Mesh(new THREE.BoxGeometry(3.1, 0.8 + lifted, 1.6), look.box ? look.box(color) : lambert(look.plinth));
      base.position.set(x, (0.8 + lifted) / 2, z);
      scene.add(base);
      const card = new THREE.Mesh(new THREE.PlaneGeometry(2.7, 3.6), new THREE.MeshBasicMaterial({ map: sign(color, label), transparent: true,
        color: chosen !== null && chosen !== i ? 0x6f7f7a : 0xffffff }));
      card.position.set(x, 0.8 + lifted + 1.85, z + 0.2);
      scene.add(card);
      if (votes) chips(scene, new THREE.Vector3(x, 0, z + 1.6), votes[i], [COLORS[color], "#fbf8f1"]);
      if (chosen === i) spotlight(scene, new THREE.Vector3(x, 0, z + 0.4));
    });
  };
}

/** A spiral of steps up to a flag; returns where a climber stands on each step. */
function summit(look) {
  return (scene, n) => {
    const spots = [];
    for (let i = 0; i < n; i++) {
      const a = -2.2 + i * 0.62, r = 3.3 - i * 0.12, h = 0.35 + i * 0.42;
      const step = look.step(h, i);
      step.position.set(Math.sin(a) * r, 0, Math.cos(a) * r);
      scene.add(step);
      spots.push(new THREE.Vector3(Math.sin(a) * r, h, Math.cos(a) * r));
    }
    const top = n * 0.42 + 0.9;
    const core = look.step(top, n);
    scene.add(core);
    const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 2.4, 8), lambert("#fbf8f1"));
    pole.position.set(0, top + 1.2, 0);
    const flag = new THREE.Mesh(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 0, 0), new THREE.Vector3(1.3, -0.35, 0), new THREE.Vector3(0, -0.75, 0)]),
      new THREE.MeshBasicMaterial({ color: COLORS.pink, side: THREE.DoubleSide }));
    flag.position.set(0.05, top + 2.35, 0);
    scene.add(pole, flag);
    return spots;
  };
}

const CAMERA = {
  default: { pos: [0, 4.4, 20], at: [0, 1.9, 0] },
  question: { pos: [0, 3.1, 19.5], at: [0, 2.85, 0] },
  reveal: { pos: [0, 3.1, 19.5], at: [0, 2.85, 0] },
  climb: { pos: [0, 6.2, 14.5], at: [0, 3.5, 0] },
  fact: { pos: [0, 3.6, 15], at: [0, 2.6, 0] },
};

// 1 — Spieltisch: the arena is a giant game table, felt and a wooden rim, with oversized cards, chips and a die.
const tableLook = { plinth: "#6b3f22", step: (h, i) => {
  const g = new THREE.Group(), colors = ["#f6c731", "#e5487f", "#2c6bed", "#fbf8f1", "#f2862e"];
  for (let y = 0; y < h; y += 0.14) {
    const chip = new THREE.Mesh(new THREE.CylinderGeometry(0.85, 0.85, 0.13, 32), lambert(colors[(i + Math.round(y / 0.14)) % 5]));
    chip.position.y = y + 0.065;
    g.add(chip);
  }
  return g;
} };
const table = {
  ground: 0x2a1a10, pad: "#0a4433", camera: CAMERA, plinths: plinths(tableLook), summit: summit(tableLook),
  pedestal(scene, at) { chips(scene, at, 7, ["#f6c731", "#fbf8f1"], 1.25, 0.13); spotlight(scene, at); },
  build(scene, stage) {
    scene.background = gradient([[0, "#120c08"], [0.55, "#2b1c12"], [1, "#3b2717"]]);
    const felt = tex(1024, 1024, (g, w) => {
      g.fillStyle = "#0f5a43";
      g.fillRect(0, 0, w, w);
      for (let i = 0; i < 9000; i++) {
        g.fillStyle = `rgba(${Math.random() < 0.5 ? "255,255,255" : "0,0,0"},0.035)`;
        g.fillRect(Math.random() * w, Math.random() * w, 2, 2);
      }
      g.strokeStyle = "#1c7258";
      g.lineWidth = 10;
      for (const r of [300, 470]) { g.beginPath(); g.arc(w / 2, w / 2, r, 0, Math.PI * 2); g.stroke(); }
    });
    const top = new THREE.Mesh(new THREE.CylinderGeometry(15, 15, 0.4, 96), [lambert("#3d2414"), new THREE.MeshLambertMaterial({ map: felt }), lambert("#3d2414")]);
    top.position.y = -0.2;
    const rim = new THREE.Mesh(new THREE.TorusGeometry(15.2, 0.6, 16, 120), lambert("#7a4a28"));
    rim.rotation.x = Math.PI / 2;
    scene.add(top, rim);
    const back = (color, label, x, rot) => {
      const c = new THREE.Mesh(new THREE.PlaneGeometry(4.2, 5.8), new THREE.MeshBasicMaterial({ map: sign(color, label), transparent: true }));
      c.position.set(x, 3.1, -12.5);
      c.rotation.set(-0.08, rot, rot * 0.3);
      scene.add(c);
    };
    if (stage !== "pick" && stage !== "resolve") {
      back("pink", "Kopfnuss", -10.5, 0.45);
      back("blue", "Kopfnuss", 10.5, -0.45);
    }
    chips(scene, new THREE.Vector3(-11, 0, -4), 12, ["#e5487f", "#fbf8f1"], 0.9, 0.18);
    chips(scene, new THREE.Vector3(11.5, 0, -3), 9, ["#2c6bed", "#fbf8f1"], 0.9, 0.18);
    const die = new THREE.Mesh(new THREE.BoxGeometry(2, 2, 2), lambert("#fbf8f1"));
    die.position.set(10.8, 1, -8);
    die.rotation.y = 0.6;
    scene.add(die);
    const lamp = new THREE.Mesh(new THREE.CircleGeometry(30, 64), glow(0xffc77a, 0.08));
    lamp.position.set(0, 14, -30);
    scene.add(lamp);
  },
};

// 2 — Studio: a dark stage, rings of light in the four answer colours on the floor, light columns behind.
const studioLook = { plinth: "#1b2348", step: (h, i) => {
  const g = new THREE.Group();
  const body = new THREE.Mesh(new THREE.CylinderGeometry(0.85, 0.95, h, 32), lambert("#1f2a55"));
  body.position.y = h / 2;
  const top = new THREE.Mesh(new THREE.CylinderGeometry(0.86, 0.86, 0.06, 32), new THREE.MeshBasicMaterial({ color: Object.values(COLORS)[i % 4] }));
  top.position.y = h;
  return g.add(body, top);
} };
const studio = {
  ground: 0x0b1026, pad: "#2a3566", camera: CAMERA, plinths: plinths(studioLook), summit: summit(studioLook),
  pedestal(scene, at) {
    const p = new THREE.Mesh(new THREE.CylinderGeometry(1.3, 1.45, 0.9, 48), lambert("#1f2a55"));
    p.position.copy(at).setY(0.45);
    const r = new THREE.Mesh(new THREE.TorusGeometry(1.32, 0.06, 8, 64), new THREE.MeshBasicMaterial({ color: COLORS.yellow }));
    r.rotation.x = Math.PI / 2;
    r.position.copy(at).setY(0.9);
    scene.add(p, r);
    spotlight(scene, at, 0xbfd4ff);
  },
  build(scene) {
    const sky = [[0, "#05081a"], [0.6, "#121a3e"], [1, "#1a2350"]];
    if (DITHER) scene.add(backdrop(sky));
    else scene.background = gradient(sky);
    const base = new THREE.Mesh(new THREE.CylinderGeometry(15, 15, 0.4, 96), lambert("#10162e"));
    base.position.y = -0.2;
    scene.add(base);
    if (FX) scene.add(floor(15));
    [[5, "yellow"], [9.5, "pink"], [14, "blue"]].forEach(([r, c], i) => {
      if (FX) return scene.add(chaseRing(r, COLORS[c], i % 2 ? -0.7 : 1));
      const ring = new THREE.Mesh(new THREE.TorusGeometry(r, 0.07, 6, 128), new THREE.MeshBasicMaterial({ color: COLORS[c] }));
      ring.rotation.x = Math.PI / 2;
      ring.position.y = 0.02;
      scene.add(ring);
      const halo = new THREE.Mesh(new THREE.RingGeometry(r - 0.5, r + 0.5, 128), glow(COLORS[c], 0.12));
      halo.rotation.x = -Math.PI / 2;
      halo.position.y = 0.03;
      scene.add(halo);
    });
    Object.values(COLORS).concat(Object.values(COLORS)).forEach((c, i) => {
      const x = (i - 3.5) * 4.2;
      const col = new THREE.Mesh(new THREE.BoxGeometry(0.5, 12, 0.5), new THREE.MeshBasicMaterial({ color: c }));
      col.position.set(x, 6, -16);
      const haze = new THREE.Mesh(new THREE.PlaneGeometry(2.6, 13), FX ? columnHaze(c, i * 1.3) : glow(c, 0.1));
      haze.position.set(x, 6.2, -15.6);
      scene.add(col, haze);
    });
  },
};

// 3 — Bergarena: a stone ring on a mountain meadow, peaks behind, the climb up a rock to a summit flag.
const alpineLook = { plinth: "#8c857a", step: (h, i) => {
  const rock = new THREE.Mesh(new THREE.CylinderGeometry(0.8, 1.05, h, 7), lambert(i % 2 ? "#a39b8e" : "#948c80", { flatShading: true }));
  rock.position.y = h / 2;
  return rock;
} };
const alpine = {
  ground: 0x4c7a3a, pad: "#7b746a", camera: CAMERA, plinths: plinths(alpineLook), summit: summit(alpineLook),
  pedestal(scene, at) {
    const p = new THREE.Mesh(new THREE.CylinderGeometry(1.3, 1.55, 0.9, 9), lambert("#a39b8e", { flatShading: true }));
    p.position.copy(at).setY(0.45);
    scene.add(p);
    spotlight(scene, at);
  },
  build(scene) {
    scene.background = gradient([[0, "#5fb2ea"], [0.65, "#bfe3fa"], [1, "#eef8ff"]]);
    const meadow = new THREE.Mesh(new THREE.CircleGeometry(90, 64), lambert("#6aa84f"));
    meadow.rotation.x = -Math.PI / 2;
    meadow.position.y = -0.05;
    const stone = tex(1024, 1024, (g, w) => {
      g.fillStyle = "#c2baad";
      g.fillRect(0, 0, w, w);
      g.strokeStyle = "#9d9587";
      g.lineWidth = 6;
      for (let r = 120; r < 512; r += 110) { g.beginPath(); g.arc(w / 2, w / 2, r, 0, Math.PI * 2); g.stroke(); }
      for (let a = 0; a < 24; a++) {
        g.beginPath();
        g.moveTo(w / 2 + Math.cos(a / 3.82) * 120, w / 2 + Math.sin(a / 3.82) * 120);
        g.lineTo(w / 2 + Math.cos(a / 3.82) * 512, w / 2 + Math.sin(a / 3.82) * 512);
        g.stroke();
      }
    });
    const ring = new THREE.Mesh(new THREE.CylinderGeometry(15, 15.4, 0.4, 96), [lambert("#8c857a"), new THREE.MeshLambertMaterial({ map: stone }), lambert("#8c857a")]);
    ring.position.y = -0.18;
    scene.add(meadow, ring);
    for (let i = 0; i < 9; i++) {
      const x = (i - 4) * 16 + (i % 2) * 5, h = 26 + ((i * 7) % 5) * 6, z = -70 - (i % 3) * 12;
      const peak = new THREE.Mesh(new THREE.ConeGeometry(h * 0.62, h, 6), lambert("#8996a3", { flatShading: true }));
      peak.position.set(x, h / 2 - 2, z);
      const snow = new THREE.Mesh(new THREE.ConeGeometry(h * 0.62 * 0.32, h * 0.32, 6), lambert("#f4f8fb", { flatShading: true }));
      snow.position.set(x, h - 2 - h * 0.16 + 0.05, z);
      scene.add(peak, snow);
    }
    for (let i = 0; i < 22; i++) {
      const a = Math.PI * 0.95 + (i / 21) * Math.PI * 1.1, r = 19 + (i % 3) * 2.5;
      const tree = new THREE.Mesh(new THREE.ConeGeometry(1.3, 4.6, 7), lambert("#2f5d34", { flatShading: true }));
      tree.position.set(Math.cos(a) * r * 1.1, 2.2, Math.sin(a) * r * 0.7 - 6);
      scene.add(tree);
    }
  },
};

// 4 — Neon: the Studio with a neon grid: grid floor, a wall of lit panels, dark shapes with lit edges, cyan edge light.
const CYAN = "#3fd8ff";
const neonLook = {
  plinth: "#0b1124",
  box: (color) => edgeBox(COLORS[color] ?? CYAN),
  step: (h, i) => {
    const g = new THREE.Group();
    const drum = new THREE.Mesh(new THREE.CylinderGeometry(0.85, 0.85, h, 40), edgeDrum(i % 3 ? CYAN : Object.values(COLORS)[i % 4]));
    drum.position.y = h / 2;
    return g.add(drum);
  },
};
const neon = {
  ground: 0x05070f, pad: "#0b1124", camera: CAMERA, plinths: plinths(neonLook), summit: summit(neonLook), rim: [CYAN, 0.9],
  padRing: (r) => chaseRing(r, CYAN, 0.4),
  pedestal(scene, at) {
    const p = new THREE.Mesh(new THREE.CylinderGeometry(1.35, 1.35, 0.9, 48), edgeDrum(COLORS.yellow));
    p.position.copy(at).setY(0.45);
    scene.add(p);
    spotlight(scene, at, 0x9fe8ff);
  },
  build(scene) {
    scene.background = gradient([[0, "#010207"], [0.55, "#040a1c"], [1, "#081530"]]);
    const base = new THREE.Mesh(new THREE.CylinderGeometry(15, 15, 0.4, 96), lambert("#03050c"));
    base.position.y = -0.2;
    scene.add(base, gridFloor(15, CYAN), gridWall(21, 6.5, CYAN));
    [[5, CYAN, 1], [9.5, COLORS.pink, -0.6], [14, CYAN, 0.8]].forEach(([r, c, v]) => scene.add(chaseRing(r, c, v)));
  },
};

export const THEMES = { table, studio, alpine, neon };
