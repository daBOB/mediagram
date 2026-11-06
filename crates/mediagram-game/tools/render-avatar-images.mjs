// Renders the two small images of each game-night avatar from its 3D model: the head (256², shown in a
// player-coloured circle) and the standing figure (256×384, feet at the bottom centre, a sprite on the board).
// Usage: bun crates/mediagram-game/tools/render-avatar-images.mjs [id ...]   (no ids: all of them)
// Reads assets/game-night/avatars/<id>/<id>-tv.glb (kept out of git) and the client's three.js (bun install in client/),
// writes client/public/avatars/<id>-head.webp and <id>-figure.webp. Needs google-chrome-stable: it renders headless
// with software WebGL, so every machine gets the same pixels.
import { existsSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';

const AVATARS = ['android', 'armour-hero', 'bear', 'bodybuilder', 'bunny', 'cat', 'dark-knight', 'fashion-doll', 'fox', 'frog', 'ghost',
  'granny', 'hedgehog', 'mummy', 'panda', 'piglet', 'princess', 'raccoon', 'samurai', 'vampire', 'witch', 'yeti'];
const here = (p) => new URL(p, import.meta.url).pathname;
const THREE_DIR = here('../client/node_modules/three/');
const GLB_DIR = here('../../../assets/game-night/avatars/');
const OUT_DIR = here('../client/public/avatars/');

const ids = process.argv.length > 2 ? process.argv.slice(2) : AVATARS;
for (const id of ids) {
  if (!AVATARS.includes(id)) throw new Error(`unknown avatar ${id}`);
  if (!existsSync(`${GLB_DIR}${id}/${id}-tv.glb`)) throw new Error(`missing ${GLB_DIR}${id}/${id}-tv.glb`);
}
if (!existsSync(THREE_DIR)) throw new Error('three.js is not installed: run bun install in crates/mediagram-game/client');
mkdirSync(OUT_DIR, { recursive: true });

const PAGE = String.raw`<!doctype html><meta charset="utf-8">
<script type="importmap">{ "imports": { "three": "/three/build/three.module.js", "three/addons/": "/three/examples/jsm/" } }</script>
<script type="module">
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { KTX2Loader } from 'three/addons/loaders/KTX2Loader.js';

const SS = 4;              // supersampling: render 4× larger, shrink with smoothing for clean edges
const POSE_T = 0.5;        // seconds into the Idle clip
// yaw turns the body a little to the viewer's left; the Idle clip turns the head back towards the camera
const FIG = { w: 256, h: 384, yaw: -0.2, elev: 0.2, top: 8, bottom: 8, side: 6 };
const HEAD = { size: 256, elev: 0.06, frame: 1.85, drop: 0.1 };   // frame: square side per head bone length; drop: shift down to show shoulders
const HEAD_ROOM = { frog: 1.3, ghost: 1.2 };   // heads much wider than their bone is long, zoomed out to stay inside the circle

const post = (path, body) => fetch(path, { method: 'POST', body });
const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, preserveDrawingBuffer: true });
renderer.setClearColor(0x000000, 0);
const loader = new GLTFLoader().setKTX2Loader(new KTX2Loader().setTranscoderPath('/three/examples/jsm/libs/basis/').detectSupport(renderer));

// Matte look: Lambert only, a soft key light from the front, above and a little left, a bright sky/ground fill.
const scene = new THREE.Scene();
const key = new THREE.DirectionalLight(0xffffff, 1.9);
key.position.set(-1.2, 2.4, 3);
scene.add(key, new THREE.HemisphereLight(0xffffff, 0x8a8070, 2.4));

function shot(cam, w, h) {
  renderer.setSize(w * SS, h * SS, false);
  renderer.render(scene, cam);
  return renderer.domElement;
}
function save(name, draw, w, h) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const g = c.getContext('2d');
  g.imageSmoothingQuality = 'high';
  draw(g);
  return new Promise((res) => c.toBlob(async (b) => { await post('/out/' + name, b); res(); }, 'image/webp', 0.85));
}

async function figure(id, model) {
  model.rotation.y = FIG.yaw;
  model.updateMatrixWorld(true);
  const box = new THREE.Box3().setFromObject(model, true), c = box.getCenter(new THREE.Vector3()), sz = box.getSize(new THREE.Vector3());
  const cam = new THREE.PerspectiveCamera(20, FIG.w / FIG.h, 0.05, 100), tan = Math.tan(THREE.MathUtils.degToRad(10));
  const dist = 1.15 * Math.max(sz.y / 2 / tan, sz.x / 2 / (tan * cam.aspect)) + sz.z;
  cam.position.set(c.x, c.y + dist * Math.sin(FIG.elev), c.z + dist * Math.cos(FIG.elev));
  cam.lookAt(c);
  const src = shot(cam, FIG.w, FIG.h), W = src.width, H = src.height;
  // fit by the pixels the avatar covers: same height for all, feet centred at the bottom
  const px = Object.assign(document.createElement('canvas'), { width: W, height: H }).getContext('2d', { willReadFrequently: true });
  px.drawImage(src, 0, 0);
  const a = px.getImageData(0, 0, W, H).data;
  let x0 = W, x1 = -1, y0 = H, y1 = -1;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (a[(y * W + x) * 4 + 3] > 24) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
  if (x1 < 0) throw new Error(id + ' rendered empty');
  if (x0 === 0 || y0 === 0 || x1 === W - 1) throw new Error(id + ' figure touches the frame edge');
  let sum = 0, n = 0;
  for (let y = Math.round(y1 - (y1 - y0) * 0.06); y <= y1; y++) for (let x = x0; x <= x1; x++) if (a[(y * W + x) * 4 + 3] > 24) { sum += x; n++; }
  const fx = sum / n + 0.5;
  const k = Math.min((FIG.h - FIG.top - FIG.bottom) / (y1 + 1 - y0), (FIG.w / 2 - FIG.side) / Math.max(fx - x0, x1 + 1 - fx));
  await save(id + '-figure.webp', (g) => g.drawImage(src, FIG.w / 2 - fx * k, FIG.h - FIG.bottom - (y1 + 1) * k, W * k, H * k), FIG.w, FIG.h);
}

async function head(id, model, mesh) {
  model.rotation.y = 0;
  model.updateMatrixWorld(true);
  const at = (name) => {
    const b = mesh.skeleton.getBoneByName(name);
    if (!b) throw new Error(id + ' has no ' + name + ' bone');
    return b.getWorldPosition(new THREE.Vector3());
  };
  // The rig's Head (top of the neck) to head_end (crown) spans the skull without ears, hats or popcorn, so framing on it
  // gives every face the same size; headfront sits in front of the face, so the camera looks the way the face does.
  const base = at('Head'), crown = at('head_end'), face = at('headfront').sub(base).setY(0).normalize();
  const side = HEAD.frame * (HEAD_ROOM[id] ?? 1) * base.distanceTo(crown), dist = side / 2 / Math.tan(THREE.MathUtils.degToRad(10));
  const look = base.clone().lerp(crown, 0.5);
  look.y -= side * HEAD.drop;
  const cam = new THREE.PerspectiveCamera(20, 1, 0.05, 100);
  cam.position.copy(look).addScaledVector(face, dist * Math.cos(HEAD.elev));
  cam.position.y += dist * Math.sin(HEAD.elev);
  cam.lookAt(look);
  const src = shot(cam, HEAD.size, HEAD.size);
  await save(id + '-head.webp', (g) => g.drawImage(src, 0, 0, HEAD.size, HEAD.size), HEAD.size, HEAD.size);
}

async function avatar(id) {
  const g = await loader.loadAsync('/glb/' + id);
  const model = g.scene;
  const idle = g.animations.find((a) => a.name === 'Idle');
  if (idle) { const mixer = new THREE.AnimationMixer(model); mixer.clipAction(idle).play(); mixer.update(POSE_T); }
  let mesh;
  model.traverse((o) => {
    if (!o.isMesh) return;
    if (o.isSkinnedMesh) mesh = o;
    const m = o.material;
    o.frustumCulled = false;
    o.material = new THREE.MeshLambertMaterial({ map: m.map, normalMap: m.normalMap, normalScale: m.normalScale, side: m.side, transparent: m.transparent, alphaTest: m.alphaTest });
    m.dispose();
  });
  if (!mesh?.material.map) throw new Error(id + ' has no textured skinned mesh');
  scene.add(model);
  await figure(id, model);
  await head(id, model, mesh);
  scene.remove(model);
  model.traverse((o) => { if (o.isMesh) { o.geometry.dispose(); o.material.map?.dispose(); o.material.normalMap?.dispose(); o.material.dispose(); } });
  await post('/log', id + (idle ? '' : ' (no Idle clip: rest pose)'));
}

addEventListener('unhandledrejection', (e) => post('/done', String(e.reason?.stack ?? e.reason)));
try {
  for (const id of new URLSearchParams(location.search).get('ids').split(',')) await avatar(id);
  await post('/done', 'ok');
} catch (e) {
  await post('/done', String(e.stack ?? e));
}
</script>`;

const file = async (path) => {
  const f = Bun.file(path);
  return (await f.exists()) ? new Response(f) : new Response('not found', { status: 404 });
};
let finish;
const done = new Promise((r) => (finish = r));
const server = Bun.serve({
  port: 0,
  hostname: '127.0.0.1',
  async fetch(req) {
    const path = new URL(req.url).pathname;
    if (path === '/') return new Response(PAGE, { headers: { 'content-type': 'text/html' } });
    if (path.startsWith('/three/')) return file(THREE_DIR + path.slice('/three/'.length));
    if (path.startsWith('/glb/')) { const id = path.slice('/glb/'.length); return AVATARS.includes(id) ? file(`${GLB_DIR}${id}/${id}-tv.glb`) : new Response('', { status: 404 }); }
    if (req.method !== 'POST') return new Response('not found', { status: 404 });
    const body = path.startsWith('/out/') ? await req.arrayBuffer() : await req.text();
    if (path.startsWith('/out/')) {
      const name = path.slice('/out/'.length);
      if (!/^[a-z-]+-(head|figure)\.webp$/.test(name) || !body.byteLength) return new Response('bad output', { status: 400 });
      await Bun.write(OUT_DIR + name, body);
      console.log(`  ${name} ${(body.byteLength / 1024).toFixed(1)} KB`);
    } else if (path === '/log') console.log(body);
    else if (path === '/done') finish(body);
    return new Response('ok');
  },
});

const profile = mkdtempSync(`${tmpdir()}/render-avatar-images-`);
const chrome = Bun.spawn(['google-chrome-stable', '--headless=new', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--no-first-run',
  '--no-default-browser-check', `--user-data-dir=${profile}`, `http://127.0.0.1:${server.port}/?ids=${ids.join(',')}`], { stdout: 'ignore', stderr: 'ignore' });
const timer = setTimeout(() => finish('timed out after 10 minutes (a page that fails to load never reports)'), 10 * 60_000);
const result = await done;
clearTimeout(timer);
chrome.kill();
await chrome.exited;
server.stop(true);
rmSync(profile, { recursive: true, force: true });
if (result !== 'ok') {
  console.error('render failed:', result);
  process.exit(1);
}
console.log(`rendered ${ids.length} avatar(s) into ${OUT_DIR}`);
