import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { bakeStudioLook } from "../ceremony/ceremony-studio-look";
import { arenaCast, disposeTree } from "./arena-cast";
import { clock, confetti, dressStage, skyTexture } from "./arena-fx";
import { ARENA_SET, type ArenaKind, type ArenaSet, type ArenaView } from "./arena-view";

const HOST_POS = {
  pick: [10.5, 3.6, -2.8],
  resolve: [-10.5, 3.6, -2.8],
  question: [0, 3.6, -2.3],
  reveal: [0, 3.6, -2.3],
  climb: [-6.8, 3.5, 3],
  fact: [5.4, 3.6, -0.9],
} as const;
const PRETTY = { pick: "Pick", resolve: "Resolve", question: "Question", reveal: "Reveal", climb: "Climb", fact: "Fact" } as const;
/** Per set: what shows past the set's edge (the pyramid stands in open sky, painted top to bottom), and how
 *  brightly the host, the one lit thing, is lit there. */
const MOOD: Record<ArenaSet, { sky: string[]; fill: number; rim: number }> = {
  Doors: { sky: ["#ddd4e9"], fill: 2.1, rim: 8 },
  Quiz: { sky: ["#05061a"], fill: 1.25, rim: 28 },
  Pyramid: { sky: ["#93a8ff", "#cdb8f4", "#f7bcdc", "#ffd9c4"], fill: 2.3, rim: 6 },
  Winner: { sky: ["#1c0508"], fill: 1.1, rim: 20 },
};

/** Blender supplies the stage and fixed seats; React keeps all timing and game text authoritative. */
export function startKopfnussArena(
  canvas: HTMLCanvasElement,
  getView: () => ArenaView,
  onReady: (ready: boolean) => void,
): () => void {
  let renderer: THREE.WebGLRenderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
  } catch {
    canvas.style.display = "none";
    onReady(false);
    return () => {};
  }
  renderer.setPixelRatio(1); // dense 3D figures never spend TV fill rate on HiDPI
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.NoToneMapping;
  const scene = new THREE.Scene();
  const skies = Object.fromEntries(Object.entries(MOOD).map(([set, mood]) => [set, skyTexture(mood.sky)]));
  const ambient = new THREE.HemisphereLight("#b8c6ff", "#16091e", 1.25);
  scene.add(ambient);
  const sun = new THREE.DirectionalLight("#fff1d4", 3.0);
  sun.position.set(5, 12, 9);
  scene.add(sun);
  const cyan = new THREE.PointLight("#12aaff", 28, 22, 2);
  cyan.position.set(9, 5, 4);
  scene.add(cyan);
  const pink = new THREE.PointLight("#ff3c97", 28, 22, 2);
  pink.position.set(-9, 5, 3);
  scene.add(pink);
  const camera = new THREE.PerspectiveCamera(36, 16 / 9, 0.1, 150); // as far as the stage cameras see: the cloud sea lies past 100
  camera.position.set(0, 10, 19);
  camera.lookAt(0, 2, 0);
  const look = bakeStudioLook(renderer, camera);
  const loader = new GLTFLoader();
  const cast = arenaCast(scene, renderer, look.dress);
  const reduced = matchMedia("(prefers-reduced-motion: reduce)");
  const targetPos = camera.position.clone();
  const targetQuat = camera.quaternion.clone();
  let targetFov = 36;
  let currentFov = 36;
  let stage: THREE.Group | null = null;
  let host: THREE.Group | null = null;
  let jaw: THREE.Object3D | null = null;
  const flakes = confetti();
  flakes.visible = false;
  scene.add(flakes);
  let alive = true;
  let raf = 0;

  const fail = () => {
    if (!alive) return;
    alive = false; // the 2D show takes over: nothing more is drawn behind it
    cancelAnimationFrame(raf);
    canvas.dataset.ready = "false";
    canvas.style.display = "none";
    onReady(false);
  };
  canvas.addEventListener("webglcontextlost", fail);
  void Promise.all([
    loader.loadAsync("/kopfnuss/kopfnuss-arena.glb"),
    loader.loadAsync("/kopfnuss/showmaster.glb"),
  ]).then(([arena, walnut]) => {
    if (!alive) return;
    stage = arena.scene;
    dressStage(stage, renderer.capabilities.getMaxAnisotropy());
    scene.add(stage);
    host = walnut.scene;
    scene.add(host);
    jaw = host.getObjectByName("WalnutJaw") ?? null;
    canvas.dataset.ready = "true";
    onReady(true);
  }).catch(fail);

  const resize = () => {
    const w = canvas.clientWidth || 1600;
    const h = canvas.clientHeight || 900;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  };
  const observer = new ResizeObserver(resize);
  observer.observe(canvas);
  resize();
  let last = performance.now();
  const started = last; // the effects' clock counts from here: a page open for days would wear its precision thin
  let talked = 0;

  const frame = (now: number) => {
    if (!alive) return;
    raf = requestAnimationFrame(frame);
    const dt = Math.min((now - last) / 1000, 0.06);
    last = now;
    const view = getView();
    const set = ARENA_SET[view.kind];
    const mood = MOOD[set];
    scene.background = skies[set];
    ambient.intensity = mood.fill;
    cyan.intensity = pink.intensity = mood.rim;
    flakes.visible = view.kind === "fact" && !reduced.matches; // frozen confetti would only hang in the air
    if (!reduced.matches) clock.value = (now - started) / 1000;
    void cast.sync(view);
    if (stage) {
      for (const [stageKind, name] of Object.entries(PRETTY) as [ArenaKind, string][]) {
        const group = stage.getObjectByName(`${name}_Stage`);
        if (group) group.visible = view.kind === stageKind;
      }
      for (const name of Object.keys(MOOD)) {
        const group = stage.getObjectByName(`Set_${name}`);
        if (group) group.visible = name === set;
      }
      const source = stage.getObjectByName(`Camera_${PRETTY[view.kind]}`) as THREE.PerspectiveCamera | undefined;
      if (source) {
        source.getWorldPosition(targetPos);
        source.getWorldQuaternion(targetQuat);
        targetFov = source.fov || 36;
        // a slow sway: the set is a real room, and a camera that breathes shows it
        if (!reduced.matches) {
          targetPos.x += Math.sin(now * 0.00023) * 0.35;
          targetPos.y += Math.sin(now * 0.00017 + 1) * 0.15;
        }
      }
      cast.place(view, stage, camera, reduced.matches ? 0 : dt, now / 1000);
    }
    if (host) {
      const hp = HOST_POS[view.kind];
      host.position.set(hp[0], hp[1], hp[2]);
      if (!reduced.matches) host.position.y += Math.sin(now * 0.0014) * 0.12;
    }
    if (jaw) jaw.rotation.x = reduced.matches ? 0.08 : 0.08 + Math.abs(Math.sin(talked * 8)) * 0.48;
    talked += dt;
    const k = reduced.matches ? 1 : 1 - Math.exp(-dt * 5);
    camera.position.lerp(targetPos, k);
    camera.quaternion.slerp(targetQuat, k);
    currentFov = THREE.MathUtils.lerp(currentFov, targetFov, k);
    const portraitBoost = Math.max(1, (16 / 9) / camera.aspect);
    camera.fov = THREE.MathUtils.radToDeg(2 * Math.atan(Math.tan(THREE.MathUtils.degToRad(currentFov) / 2) * portraitBoost));
    camera.updateProjectionMatrix();
    renderer.render(scene, camera);
    canvas.dataset.kind = view.kind;
  };
  raf = requestAnimationFrame(frame);

  return () => {
    alive = false;
    cancelAnimationFrame(raf);
    observer.disconnect();
    canvas.removeEventListener("webglcontextlost", fail);
    // everything this game put on the GPU goes with it: the four set pictures alone are some 90 MB
    cast.dispose();
    if (stage) disposeTree(stage, true);
    if (host) disposeTree(host);
    flakes.geometry.dispose();
    (flakes.material as THREE.Material).dispose();
    for (const sky of Object.values(skies)) sky.dispose();
    look.dispose();
    renderer.dispose();
  };
}
