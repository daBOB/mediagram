// Kopfnuss arena mockup: one 3D arena behind every stage, the text on top as HTML.
// ?theme=table|studio|alpine&stage=pick|resolve|question|reveal|climb|fact
import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { MeshoptDecoder } from "three/addons/libs/meshopt_decoder.module.js";
import { KTX2Loader } from "three/addons/loaders/KTX2Loader.js";
import { RoomEnvironment } from "three/addons/environments/RoomEnvironment.js";
import { THEMES } from "./arena-themes.js?v=fx7";
import { clock, confetti, DITHER, ditherGlsl, energy, padGlow, rimLight } from "./arena-fx.js?v=fx7";

const q = new URLSearchParams(location.search);
const theme = THEMES[q.get("theme") ?? "table"], stage = q.get("stage") ?? "question", FX = q.get("fx") === "1";
const mixers = [];
const COLORS = { blue: "#2c6bed", orange: "#f2862e", yellow: "#f6c731", pink: "#e5487f" };
const MARKS = { blue: "●", orange: "▲", yellow: "■", pink: "◆" };
const PLAYERS = [
  { name: "Ana", avatar: "fox", pick: "orange", tricks: ["Kleber", "Spiegel"], pts: 933 },
  { name: "Ben", avatar: "raccoon", pick: "blue", tricks: ["Spiegel"], pts: 0 },
  { name: "Cem", avatar: "panda", pick: "orange", tricks: [], pts: 871 },
  { name: "Dana", avatar: "frog", pick: "yellow", tricks: ["Spiegel"], pts: 0 },
  { name: "Eve", avatar: "bunny", pick: "orange", tricks: [], pts: 802 },
  { name: "Finn", avatar: "yeti", pick: "pink", tricks: ["Wechselbad"], pts: 0 },
];

const canvas = document.getElementById("c"), hud = document.getElementById("hud");
document.body.dataset.theme = q.get("theme") ?? "table";
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setPixelRatio(1);
renderer.setSize(1920, 1080, false);
renderer.outputColorSpace = THREE.SRGBColorSpace;
const scene = new THREE.Scene();
const cam = new THREE.PerspectiveCamera(30, 16 / 9, 0.5, 200);
const view = theme.camera[stage] ?? theme.camera.default;
cam.position.set(...view.pos);
cam.lookAt(...view.at);
// ?zoom=N&aim=y: the fact stage's winner seen N times closer, aimed at height y, for comparing avatar copies
const zoom = Number(q.get("zoom") ?? 0);
if (zoom) { cam.fov = 30 / zoom; cam.lookAt(0, Number(q.get("aim") ?? 2.75), 2); cam.updateProjectionMatrix(); }
cam.updateMatrixWorld(); // the HTML tags project through it before the first render
scene.add(new THREE.HemisphereLight(0xffffff, theme.ground, 1.6));
const sun = new THREE.DirectionalLight(0xffffff, 1.4);
sun.position.set(4, 10, 6);
scene.add(sun);

// The ceremony's matcap studio light, so the avatars look as they do there and cost what they cost there.
function studioLook() {
  const pmrem = new THREE.PMREMGenerator(renderer), env = pmrem.fromScene(new RoomEnvironment(), 0.04);
  const studio = new THREE.Scene();
  studio.environment = env.texture;
  studio.environmentIntensity = 0.55;
  studio.add(new THREE.Mesh(new THREE.SphereGeometry(1, 96, 48), new THREE.MeshPhysicalMaterial({ roughness: 1, specularIntensity: 0 })));
  const eye = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 10);
  eye.quaternion.copy(cam.quaternion);
  eye.position.set(0, 0, 5).applyQuaternion(cam.quaternion);
  const rt = new THREE.WebGLRenderTarget(256, 256, { colorSpace: THREE.SRGBColorSpace });
  renderer.setRenderTarget(rt);
  renderer.render(studio, eye);
  renderer.setRenderTarget(null);
  return (old, dim, rim) => {
    const m = new THREE.MeshMatcapMaterial({ map: old.map, normalMap: old.normalMap ?? undefined, normalScale: old.normalScale,
      matcap: rt.texture, color: dim ? 0x6d7d78 : 0xffffff });
    m.onBeforeCompile = (s) => {
      s.fragmentShader = s.fragmentShader.replace(
        "vec3 outgoingLight = diffuseColor.rgb * matcapColor.rgb;", "vec3 outgoingLight = diffuseColor.rgb * matcapColor.rgb * 2.0;");
      if (FX && rim) rimLight(s, rim[0], rim[1]);
    };
    m.customProgramCacheKey = () => (FX && rim ? "rim" : "plain");
    return m;
  };
}
const dress = studioLook();
const ktx2 = new KTX2Loader().setTranscoderPath("./basis/").detectSupport(renderer);
const loader = new GLTFLoader().setMeshoptDecoder(MeshoptDecoder).setKTX2Loader(ktx2);

async function avatar(id, { height = 1.9, clip = "Idle_15", at = 0.8, dim = false, rim = theme.rim ?? ["#7f9bff", 0.55], url = null } = {}) {
  const gltf = await loader.loadAsync(url ?? `./game-avatars/${q.get("lod") ? q.get("lod") + "/" : ""}${id}.glb`);
  const root = gltf.scene;
  root.traverse((o) => { if (o.isMesh) { o.frustumCulled = false; o.material = dress(o.material, dim, rim); } });
  const box = new THREE.Box3().setFromObject(root), size = box.getSize(new THREE.Vector3()), mid = box.getCenter(new THREE.Vector3());
  const s = height / size.y;
  root.scale.setScalar(s);
  root.position.set(-mid.x * s, -box.min.y * s, -mid.z * s);
  const mixer = new THREE.AnimationMixer(root), c = THREE.AnimationClip.findByName(gltf.animations, clip);
  if (c) { mixer.clipAction(c).play(); mixer.update(at); }
  mixers.push(mixer);
  return new THREE.Group().add(root);
}

const blob = (() => {
  const cv = document.createElement("canvas");
  cv.width = cv.height = 128;
  const g = cv.getContext("2d"), r = g.createRadialGradient(64, 64, 4, 64, 64, 62);
  r.addColorStop(0, "rgba(0,0,0,.55)");
  r.addColorStop(1, "rgba(0,0,0,0)");
  g.fillStyle = r;
  g.fillRect(0, 0, 128, 128);
  return new THREE.CanvasTexture(cv);
})();
function pad(color, glow = false) {
  const g = new THREE.Group();
  const shadow = new THREE.Mesh(new THREE.PlaneGeometry(1.9, 1.9), new THREE.MeshBasicMaterial({ map: blob, transparent: true, depthWrite: false }));
  shadow.rotation.x = -Math.PI / 2;
  shadow.position.y = 0.02;
  g.add(shadow);
  if (color) {
    const disc = new THREE.Mesh(new THREE.CylinderGeometry(0.85, 0.92, 0.14, 40), new THREE.MeshLambertMaterial({ color, emissive: color, emissiveIntensity: glow ? 0.55 : 0.12 }));
    disc.position.y = 0.07;
    g.add(disc);
  }
  return g;
}

const px = (v) => {
  const p = v.clone().project(cam);
  return [(p.x + 1) * 960, (1 - p.y) * 540];
};
const html = (s) => hud.insertAdjacentHTML("beforeend", s);
function tagAt(pos, text, extra = "") {
  const [x, y] = px(pos);
  html(`<div class="tag" style="left:${x}px;top:${y + 8}px"><b>${text}</b>${extra}</div>`);
}
function tricksAt(pos, tricks) {
  if (!tricks.length) return;
  const [x, y] = px(pos);
  html(`<div class="trick" style="left:${x}px;top:${y - 8}px">${tricks.map((t) => `<span>${t}</span>`).join("")}</div>`);
}
const answers = (top, opts, right) =>
  html(`<div class="answers" style="top:${top}px;grid-template-columns:repeat(4,1fr)">${opts.map(([c, l]) =>
    `<div class="a ${right ? (c === right ? "right" : "dim") : ""}" style="background:${COLORS[c]}">${MARKS[c]} ${l}</div>`).join("")}</div>`);

const ring = (n, r, z0, spread = 1) =>
  [...Array(n).keys()].map((i) => {
    const a = (i / (n - 1) - 0.5) * spread * Math.PI * 0.62;
    return new THREE.Vector3(Math.sin(a) * r, 0, z0 - (1 - Math.cos(a)) * r);
  });

async function lineUp({ spots, pick = false, poses = {}, dimAll = false, glowDone = [], rims = {} }) {
  await Promise.all(PLAYERS.map(async (p, i) => {
    const pos = spots[i];
    const color = pick ? COLORS[p.pick] : glowDone.includes(p.name) ? "#f6c731" : theme.pad;
    const g = pad(color, pick || glowDone.includes(p.name));
    g.position.copy(pos);
    scene.add(g);
    if (theme.padRing) { const pr = theme.padRing(0.9); pr.position.x = pos.x; pr.position.z = pos.z; pr.position.y = 0.16; scene.add(pr); }
    if (FX && (glowDone.includes(p.name) || (pick && p.pick === "orange"))) {
      const halo = padGlow(glowDone.includes(p.name) ? "#f6c731" : COLORS[p.pick], i * 0.17);
      halo.position.x = pos.x; halo.position.z = pos.z;
      scene.add(halo);
    }
    const a = await avatar(p.avatar, { clip: poses[p.name] ?? "Idle_15", at: 0.6 + i * 0.37, dim: dimAll, rim: rims[p.name] });
    a.position.copy(pos).setY(0.14);
    a.lookAt(cam.position.x * 0.3, 0.14, cam.position.z);
    scene.add(a);
    p.head = pos.clone().setY(2.25);
    p.feet = pos.clone();
  }));
}

const STAGES = {
  async pick() {
    html(`<div class="bar"><span>Kopfnuss</span><span>Runde 1 von 7</span></div>
      <div class="q"><h1>Welche Kategorie? Und wen ärgert ihr?</h1><div class="meter"><i></i></div></div>`);
    theme.plinths(scene, [["blue", "Sprache"], ["orange", "Musik"], ["yellow", "Geschichte"]], { votes: null });
    await lineUp({ spots: ring(6, 8.6, 1.6), glowDone: ["Ana", "Ben", "Dana"] });
    for (const p of PLAYERS) tagAt(p.feet, p.name, ["Ana", "Ben", "Dana"].includes(p.name) ? `<span class="pts">✓ gewählt</span>` : "");
  },
  async resolve() {
    html(`<div class="bar"><span>Kopfnuss</span><span>Runde 1 von 7</span></div>`);
    theme.plinths(scene, [["blue", "Sprache"], ["orange", "Musik"], ["yellow", "Geschichte"]], { votes: [1, 2, 3], chosen: 2 });
    await lineUp({ spots: ring(6, 8.6, 1.6), poses: { Ana: "Shrug", Dana: "Cheer_With_One_Hand_Up" }, rims: { Ana: ["#ff5c97", 1.1], Cem: ["#ff5c97", 1.1] } });
    const hits = [[3, 0, "Spiegel"], [1, 0, "Kleber"], [5, 2, "Wechselbad"]];
    for (const [from, to, trick] of hits) {
      const a = PLAYERS[from].head, b = PLAYERS[to].head, mid = a.clone().lerp(b, 0.5).setY(4.4);
      const curve = new THREE.QuadraticBezierCurve3(a, mid, b);
      scene.add(new THREE.Mesh(new THREE.TubeGeometry(curve, 60, FX ? 0.13 : 0.06, 8), FX ? energy(COLORS.pink) : new THREE.MeshBasicMaterial({ color: COLORS.pink })));
      const tip = new THREE.Mesh(new THREE.SphereGeometry(0.22, 20, 12), new THREE.MeshBasicMaterial({ color: COLORS.pink }));
      tip.position.copy(b);
      scene.add(tip);
    }
    for (const p of PLAYERS) tagAt(p.feet, p.name);
    tricksAt(PLAYERS[0].head.clone().setY(2.6), ["Spiegel von Dana", "Kleber von Ben"]);
    tricksAt(PLAYERS[2].head.clone().setY(2.6), ["Wechselbad von Finn"]);
  },
  async question(reveal = false) {
    html(`<div class="bar"><span>Geschichte</span><span>Runde 1 von 7 · Frage 1 von 3</span></div>
      <div class="q"><h1>Wie heißt die Epoche zwischen Antike und Neuzeit?</h1>${reveal ? "" : `<div class="meter"><i></i></div>`}</div>`);
    answers(reveal ? 258 : 296, [["blue", "Barock"], ["orange", "Mittelalter"], ["yellow", "Romantik"], ["pink", "Steinzeit"]], reveal ? "orange" : null);
    const right = (p) => p.pick === "orange";
    await lineUp({ spots: ring(6, 8.4, 4.6, 1.0), pick: reveal,
      poses: reveal ? Object.fromEntries(PLAYERS.map((p, i) => [p.name, right(p) ? (i % 2 ? "Cheer_With_One_Hand_Up" : "victory") : "Shrug"])) : {},
      rims: reveal ? Object.fromEntries(PLAYERS.map((p) => [p.name, right(p) ? ["#ffd84a", 1.0] : theme.rim ? [theme.rim[0], 0.4] : ["#7f9bff", 0.3]])) : {} });
    if (FX && reveal) scene.add(confetti());
    for (const p of PLAYERS) {
      tagAt(p.feet, p.name, reveal && p.pts ? `<span class="pts">+${p.pts}</span>` : "");
      if (!reveal) tricksAt(p.head, p.tricks);
    }
  },
  async reveal() { await STAGES.question(true); },
  async climb() {
    html(`<div class="bar"><span>Gipfelsturm</span><span>noch 42 s</span></div>`);
    const steps = theme.summit(scene, 10);
    const at = [9, 7, 6, 4, 3, 1];
    await Promise.all(PLAYERS.map(async (p, i) => {
      const s = steps[at[i]];
      const a = await avatar(p.avatar, { height: 1.75, clip: i === 0 ? "victory" : "Idle_15", at: 0.5 + i * 0.4 });
      a.position.copy(s);
      a.lookAt(cam.position.x, s.y, cam.position.z);
      scene.add(a);
      p.feet = s.clone();
    }));
    for (const p of PLAYERS) tagAt(p.feet, `${p.name} · ${at[PLAYERS.indexOf(p)] + 1}/10`);
  },
  async fact() {
    const spot = new THREE.Vector3(0, 0, 2);
    theme.pedestal(scene, spot);
    const w = await avatar("fox", { height: 2.3, clip: q.get("clip") ?? "victory", at: 1.1, rim: ["#ffd84a", 1.1], url: q.get("winner") });
    if (FX) scene.add(confetti(260, 14));
    w.position.copy(spot).setY(0.9);
    w.lookAt(cam.position.x, 0.9, cam.position.z);
    scene.add(w);
    const others = PLAYERS.slice(1);
    await Promise.all(others.map(async (p, i) => {
      const pos = ring(5, 9, 4.5)[i].add(new THREE.Vector3(0, 0, -1.5));
      if (Math.abs(pos.x) < 2.2) pos.x += Math.sign(pos.x || 1) * 2.6;
      const a = await avatar(p.avatar, { height: 1.7, clip: "Cheer_With_One_Hand_Up", at: 0.4 + i * 0.5, dim: true });
      a.position.copy(pos);
      a.lookAt(0, 0, spot.z + 3);
      scene.add(a);
    }));
    html(`<div class="bar"><span>Kopfnuss</span><span>Runde 3 von 7</span></div>
      <div class="card" style="left:60px;right:60px;bottom:48px"><h2>Ana wusste es zuerst!</h2>
      <p>Das Mittelalter dauerte rund 1000 Jahre, vom Ende des Weströmischen Reichs 476 bis um 1500.</p></div>`);
  },
};

await Promise.all([document.fonts.load("800 80px 'Bricolage Grotesque'"), document.fonts.load("700 24px 'Atkinson Hyperlegible'")]);
theme.build(scene, stage);
await STAGES[stage]();
// dithering: three's own for its materials, the same noise appended to the shader effects
if (DITHER) scene.traverse((o) => [o.material].flat().forEach((m) => {
  if (!m) return;
  if (m.isShaderMaterial) m.fragmentShader = m.fragmentShader.replace(/}\s*$/, ditherGlsl + "}");
  else m.dithering = true;
}));
for (let i = 0; i < 3; i++) renderer.render(scene, cam);
// For the clips: set the effect clock and step the avatars to time t (seconds).
let last = 0;
window.setT = (t) => { for (const m of mixers) m.update(t - last); last = t; clock.value = t; renderer.render(scene, cam); return t; };
const info = renderer.info.render;
const wc = ktx2.workerConfig ?? {};
if (zoom) hud.style.display = "none";
document.title = `ready ${info.calls} calls ${info.triangles} tris · gpu formats ${Object.keys(wc).filter((k) => wc[k] === true).join(',')}`;
console.log(document.title);
