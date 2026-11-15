// Showmaster mockups for the Kopfnuss arena, built from turned (lathe) parts so they cost the box little.
// ?host=nut      — an Erzgebirge nutcracker in lacquered wood; its hinged jaw is the talking mouth
// ?host=walnut   — a floating walnut head; the lower shell half is the jaw
import * as THREE from "three";

const lacquer = (color, shininess = 60) => new THREE.MeshPhongMaterial({ color, shininess, specular: 0x333333 });
const matte = (color) => new THREE.MeshLambertMaterial({ color });
const GOLD = lacquer("#e2b33c", 90), BLACK = lacquer("#16161c", 80), RED = lacquer("#b81f34"), WHITE = matte("#f3efe6");
const SKIN = lacquer("#f1c6a0", 40), GREEN = lacquer("#1d4a3a");

const lathe = (pts, mat, seg = 40) => new THREE.Mesh(new THREE.LatheGeometry(pts.map(([r, y]) => new THREE.Vector2(r, y)), seg), mat);
const cyl = (rt, rb, h, mat, seg = 28) => new THREE.Mesh(new THREE.CylinderGeometry(rt, rb, h, seg), mat);
const ball = (r, mat) => new THREE.Mesh(new THREE.SphereGeometry(r, 24, 16), mat);
const at = (m, x, y, z) => { m.position.set(x, y, z); return m; };

// Classic Erzgebirge proportions: big head, tall shako, barrel coat, stiff legs on a turned base. ~3.1 units tall.
export function nutcracker({ jaw = 0.32, micArm = true } = {}) {
  const g = new THREE.Group();
  g.add(lathe([[0, 0], [0.6, 0], [0.6, 0.1], [0.52, 0.16], [0.5, 0.24], [0, 0.24]], GREEN));
  g.add(at(new THREE.Mesh(new THREE.TorusGeometry(0.56, 0.025, 8, 48), GOLD), 0, 0.13, 0).rotateX(Math.PI / 2));
  for (const x of [-0.15, 0.15]) {
    g.add(at(cyl(0.13, 0.14, 0.46, BLACK), x, 0.47, 0));                // boots
    g.add(at(cyl(0.125, 0.13, 0.46, WHITE), x, 0.93, 0));               // trousers
  }
  for (const x of [-0.15, 0.15]) { const toe = ball(0.135, BLACK); toe.scale.set(1, 0.55, 1.45); g.add(at(toe, x, 0.27, 0.06)); }
  // coat: skirt flare, barrel chest, sloping shoulders
  g.add(lathe([[0.36, 1.06], [0.42, 1.12], [0.38, 1.3], [0.35, 1.62], [0.32, 1.86], [0.24, 1.98], [0.13, 2.02], [0, 2.02]], RED));
  g.add(at(new THREE.Mesh(new THREE.TorusGeometry(0.385, 0.035, 8, 48), BLACK), 0, 1.32, 0).rotateX(Math.PI / 2));
  g.add(at(new THREE.Mesh(new THREE.BoxGeometry(0.13, 0.1, 0.03), GOLD), 0, 1.32, 0.41));
  for (const y of [1.48, 1.62, 1.76]) g.add(at(ball(0.03, GOLD), 0, y, 0.345 - (y - 1.48) * 0.13));
  for (const x of [-0.27, 0.27]) { const e = ball(0.1, GOLD); e.scale.set(1.3, 0.45, 1); g.add(at(e, x, 1.95, 0)); }
  // left arm hangs; right arm holds the microphone up to the mouth
  const arm = (side, raised) => {
    const a = new THREE.Group();
    const upper = at(cyl(0.085, 0.09, 0.5, RED), 0, -0.25, 0);
    const cuff = at(cyl(0.095, 0.095, 0.08, WHITE), 0, -0.52, 0);
    const hand = at(ball(0.085, SKIN), 0, -0.6, 0);
    a.add(upper, cuff, hand);
    a.position.set(side * 0.37, 1.92, 0);
    if (!raised) { a.rotation.z = side * 0.12; return a; }
    const tip = new THREE.Vector3(0.22, 1.98, 0.36), dir = tip.clone().sub(a.position).normalize();
    a.quaternion.setFromUnitVectors(new THREE.Vector3(0, -1, 0), dir);
    a.scale.y = tip.distanceTo(a.position) / 0.6;
    const mic = new THREE.Group();
    mic.add(at(cyl(0.035, 0.028, 0.3, lacquer("#2a2a30", 100)), 0, 0, 0), at(ball(0.065, lacquer("#9aa3ad", 120)), 0, 0.18, 0));
    mic.position.copy(tip).add(new THREE.Vector3(-0.03, 0.08, 0.02));
    mic.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), new THREE.Vector3(-0.35, 0.9, -0.1).normalize());
    g.add(mic);
    return a;
  };
  g.add(arm(-1, false), arm(1, micArm));
  // head: turned cylinder, painted face
  const head = new THREE.Group();
  head.position.y = 2.02;
  head.add(lathe([[0, 0], [0.2, 0], [0.25, 0.08], [0.255, 0.42], [0.23, 0.5], [0, 0.5]], SKIN));
  const hair = new THREE.Mesh(new THREE.CylinderGeometry(0.27, 0.28, 0.36, 32, 1, true, Math.PI * 0.42, Math.PI * 1.16), WHITE);
  hair.material = WHITE.clone(); hair.material.side = THREE.DoubleSide;
  head.add(at(hair, 0, 0.25, -0.01));
  for (const x of [-0.085, 0.085]) {
    const white = ball(0.05, matte("#ffffff")); white.scale.set(1, 1.15, 0.5); head.add(at(white, x, 0.36, 0.235));
    head.add(at(ball(0.026, BLACK), x, 0.355, 0.258));
    head.add(at(new THREE.Mesh(new THREE.BoxGeometry(0.11, 0.025, 0.02), BLACK), x, 0.43, 0.245).rotateZ(x > 0 ? -0.18 : 0.18));
    const cheek = ball(0.05, matte("#e8738a")); cheek.scale.set(1, 0.7, 0.3); head.add(at(cheek, x * 1.75, 0.25, 0.22));
  }
  const nose = ball(0.065, lacquer("#e9a98a")); nose.scale.set(0.9, 1.2, 1); head.add(at(nose, 0, 0.28, 0.26));
  for (const x of [-0.07, 0.07]) { const m = ball(0.06, WHITE); m.scale.set(1.6, 0.55, 0.7); head.add(at(m, x, 0.2, 0.255).rotateZ(x > 0 ? 0.25 : -0.25)); }
  head.add(at(new THREE.Mesh(new THREE.BoxGeometry(0.2, 0.05, 0.05), matte("#ffffff")), 0, 0.15, 0.235)); // upper teeth
  head.add(at(new THREE.Mesh(new THREE.BoxGeometry(0.24, 0.1, 0.1), matte("#3a0d14")), 0, 0.12, 0.2));    // mouth cavity
  // the jaw hinges at the back of the head, like the real lever; open = talking
  const jawG = new THREE.Group();
  jawG.position.set(0, 0.16, -0.18);
  const chin = at(new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.14, 0.42), SKIN), 0, -0.08, 0.2);
  const lowTeeth = at(new THREE.Mesh(new THREE.BoxGeometry(0.2, 0.05, 0.05), matte("#ffffff")), 0, -0.005, 0.395);
  const beard = lathe([[0, -0.35], [0.12, -0.3], [0.2, -0.15], [0.21, 0], [0, 0]], WHITE, 24);
  beard.scale.set(1, 1, 0.7);
  jawG.add(chin, lowTeeth, at(beard, 0, -0.13, 0.27));
  jawG.rotation.x = jaw;
  head.add(jawG);
  // shako with gold band and a walnut badge: the show's emblem
  head.add(at(cyl(0.27, 0.25, 0.58, BLACK), 0, 0.79, 0));
  head.add(at(cyl(0.262, 0.262, 0.07, GOLD), 0, 0.55, 0));
  head.add(at(cyl(0.272, 0.272, 0.05, GOLD), 0, 1.06, 0));
  const badge = ball(0.1, GOLD); badge.scale.set(1, 1, 0.45); head.add(at(badge, 0, 0.8, 0.26));
  const tipCone = new THREE.Mesh(new THREE.ConeGeometry(0.03, 0.06, 12), GOLD); head.add(at(tipCone, 0, 0.92, 0.27));
  for (const r of [-0.4, 0, 0.4]) head.add(at(new THREE.Mesh(new THREE.BoxGeometry(0.012, 0.15, 0.02), lacquer("#8a6a1c")), r * 0.12, 0.8, 0.305).rotateZ(r));
  const plume = ball(0.09, lacquer("#e5487f", 20)); plume.scale.set(0.8, 1.5, 0.8); head.add(at(plume, 0, 1.18, 0));
  g.add(head);
  g.userData.jaw = jawG;
  return g;
}

// A walnut with a face: the upper shell carries the eyes, the lower half drops to talk. Floats over the stage.
export function walnutHead({ jaw = 0.38 } = {}) {
  const g = new THREE.Group();
  const shellMat = lacquer("#9a6a3a", 25), kernel = matte("#e9d2a8");
  const wrinkled = (geo) => {
    const p = geo.attributes.position, v = new THREE.Vector3();
    for (let i = 0; i < p.count; i++) {
      v.fromBufferAttribute(p, i);
      const n = Math.abs(Math.sin(v.x * 14 + Math.sin(v.y * 11) * 1.6) * Math.cos(v.y * 13 + v.z * 9)) * 0.07 + Math.abs(Math.sin(v.z * 19 + v.y * 7 + v.x * 3)) * 0.035 - 0.05;
      v.multiplyScalar(1 + n);
      p.setXYZ(i, v.x, v.y, v.z);
    }
    geo.computeVertexNormals();
    return geo;
  };
  const top = new THREE.Mesh(wrinkled(new THREE.SphereGeometry(1, 128, 64, 0, Math.PI * 2, 0, Math.PI / 2)), shellMat);
  top.scale.set(1, 1.15, 0.95);
  const lid = new THREE.Mesh(new THREE.CircleGeometry(0.97, 48), matte("#3b160f")); lid.rotation.x = Math.PI / 2; // inside, seen when talking
  g.add(top, lid);
  for (const x of [-0.34, 0.34]) {
    const w = ball(0.24, matte("#ffffff")); w.scale.set(1, 1.2, 0.6); g.add(at(w, x, 0.5, 0.86));
    g.add(at(ball(0.11, BLACK), x * 0.92, 0.47, 1.0));
    g.add(at(new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.06, 0.06), lacquer("#3d2412")), x, 0.86, 0.82).rotateZ(x > 0 ? -0.2 : 0.2));
  }
  const jawG = new THREE.Group();
  jawG.position.set(0, 0, -0.9);
  const cavity = new THREE.Mesh(new THREE.CircleGeometry(0.95, 48), matte("#3b160f")); cavity.rotation.x = -Math.PI / 2; cavity.position.set(0, -0.02, 0.9);
  jawG.add(cavity);
  const low = new THREE.Mesh(wrinkled(new THREE.SphereGeometry(1, 128, 64, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2)), shellMat);
  low.scale.set(1, 0.95, 0.95); low.position.z = 0.9;
  const tongue = new THREE.Mesh(new THREE.CircleGeometry(0.6, 48), matte("#c9405c")); tongue.rotation.x = -Math.PI / 2; tongue.position.set(0, -0.01, 0.9);
  jawG.add(low, tongue);
  jawG.rotation.x = jaw;
  g.add(jawG);
  // bow tie floating under the chin
  const tie = new THREE.Group();
  for (const s of [-1, 1]) { const c = new THREE.Mesh(new THREE.ConeGeometry(0.22, 0.42, 4), lacquer("#e5487f", 40)); c.rotation.z = s * Math.PI / 2; c.position.x = s * 0.22; tie.add(c); }
  tie.add(ball(0.09, lacquer("#e5487f", 40)));
  tie.position.set(0, -1.2, 0.3);
  g.add(tie);
  g.userData.jaw = jawG;
  return g;
}

function hostSpot(scene, x, z, height) {
  const p = cyl(1.0, 1.15, height, matte("#1f2a55"), 48);
  p.position.set(x, height / 2, z);
  const r = new THREE.Mesh(new THREE.TorusGeometry(1.02, 0.05, 8, 64), new THREE.MeshBasicMaterial({ color: "#f6c731" }));
  r.rotation.x = Math.PI / 2;
  r.position.set(x, height, z);
  const beam = new THREE.Mesh(new THREE.ConeGeometry(1.6, 9, 40, 1, true),
    new THREE.MeshBasicMaterial({ color: 0xfff1c4, transparent: true, opacity: 0.07, depthWrite: false, side: THREE.DoubleSide, blending: THREE.AdditiveBlending }));
  beam.position.set(x, height + 4.5, z);
  scene.add(p, r, beam);
}

// Where the host stands in each stage: back centre on a raised spot, between the two middle players.
const SPOTS = { pick: [0, -5.2], resolve: [0, -5.2], question: [0, -1.4], reveal: [0, -1.4], climb: [-7.5, -2], fact: [5.2, -1] };

export function addHost(scene, kind, stage, cam) {
  const [x, z] = SPOTS[stage] ?? [0, -1.4];
  const raise = stage === "pick" || stage === "resolve" ? 1.4 : 0.55;
  if (kind === "walnut") {
    const w = walnutHead({ jaw: stage === "reveal" ? 0.55 : 0.32 });
    w.scale.setScalar(0.85);
    w.position.set(x, raise + 2.35, z);
    w.lookAt(cam.position.x, raise + 3.0, cam.position.z);
    scene.add(w);
    hostSpot(scene, x, z, raise);
    return { head: w.position.clone().setY(raise + 4.6), feet: new THREE.Vector3(x, raise, z) };
  }
  hostSpot(scene, x, z, raise);
  const n = nutcracker({ jaw: stage === "reveal" ? 0.5 : 0.3 });
  n.scale.setScalar(1.15);
  n.position.set(x, raise, z);
  n.lookAt(cam.position.x * 0.5, raise, cam.position.z);
  scene.add(n);
  return { head: new THREE.Vector3(x, raise + 3.4, z), feet: new THREE.Vector3(x, raise, z) };
}
