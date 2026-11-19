// The stages' moving light. The sets themselves are baked pictures; these few additive shaders ride on top:
// each is a handful of arithmetic operations per pixel, no extra render pass, one shared clock.
// Blender marks an effect mesh with `fx` (which effect) and `color`; `dressStage` swaps in the shader.

import * as THREE from "three";

/** Seconds, advanced by the engine each frame (it stands still under reduced motion). */
export const clock = { value: 0 };

// glTF stores V top-down; the effects are written in Blender's bottom-up V, where 0 is a beam's lamp or a column's foot.
const VERTEX = `varying vec2 vUv; varying vec3 vN, vV;
  void main(){ vUv = vec2(uv.x, 1.0 - uv.y); vec4 mv = modelViewMatrix * vec4(position, 1.0);
    vN = normalize(normalMatrix * normal); vV = normalize(-mv.xyz); gl_Position = projectionMatrix * mv; }`;
const HEAD = `uniform float uTime, uPhase; uniform vec3 uColor; varying vec2 vUv; varying vec3 vN, vV;
  float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }`;

/** What each effect adds to the picture, as the body of `main`. `c` is the light it contributes. */
export const EFFECTS: Record<string, string> = {
  // a floor light ring: its glow breathes, and brighter beads chase round it
  ring: `float d = abs(vUv.y - 0.5) * 2.0;
    float chase = pow(0.5 + 0.5 * sin(vUv.x * 56.5 - uTime * 3.0), 10.0);
    vec3 c = uColor * (smoothstep(0.16, 0.0, d) * chase * 0.9 + smoothstep(1.0, 0.0, d) * (0.16 + 0.5 * chase));`,
  // marquee bulbs and halos: pulses of light running along the tube
  chase: `float pulse = pow(0.5 + 0.5 * sin(vUv.x * 75.0 - uTime * 4.0), 6.0);
    vec3 c = uColor * pow(abs(dot(vN, vV)), 1.5) * (0.12 + 0.75 * pulse);`,
  // a trick in flight: energy running along the arc towards its target
  energy: `float pulse = pow(fract(vUv.x * 5.0 - uTime * 1.6), 3.0);
    vec3 c = uColor * (0.7 + 2.2 * pulse);`,
  // a light column's haze: bands of light travelling up it
  column: `float across = pow(max(0.0, 1.0 - abs(vUv.x - 0.5) * 2.0), 2.2);
    float band = pow(0.5 + 0.5 * sin(vUv.y * 10.0 - uTime * 2.4 + uPhase), 6.0);
    vec3 c = uColor * across * smoothstep(0.0, 0.15, vUv.y) * smoothstep(1.0, 0.75, vUv.y) * (0.1 + 0.5 * band);`,
  // a spotlight's cone: bright through its middle, soft at its edges, dust drifting down it
  beam: `float face = pow(abs(dot(vN, vV)), 2.0);
    float dust = pow(hash(floor(vec2(vUv.x * 420.0, vUv.y * 300.0 - uTime * 6.0))), 40.0);
    vec3 c = uColor * face * (1.0 - vUv.y * 0.6) * (0.13 + dust * 0.12);`,
  // a ring of light spreading from under a player
  pad: `float r = length(vUv - 0.5) * 4.0; float wave = fract(uTime * 0.8 + uPhase);
    vec3 c = uColor * (smoothstep(0.12, 0.0, abs(r - (0.9 + wave * 0.9))) * (1.0 - wave) * 0.9
      + smoothstep(1.4, 0.8, r) * 0.22);`,
  // glitter: single flakes flashing, each on its own beat, as four-point glints
  sparkle: `vec2 g = vUv * vec2(24.0, 44.0); vec2 id = floor(g); float rnd = hash(id);
    vec2 f = fract(g) - 0.5 - (vec2(hash(id + 3.1), hash(id + 7.7)) - 0.5) * 0.5;
    float beat = pow(max(0.0, sin(uTime * (1.2 + rnd * 2.6) + rnd * 40.0)), 22.0) * step(0.62, rnd);
    float glint = smoothstep(0.2, 0.0, length(f)) + 0.7 * max(
      smoothstep(0.45, 0.0, abs(f.x) * 9.0 + abs(f.y) * 1.3), smoothstep(0.45, 0.0, abs(f.y) * 9.0 + abs(f.x) * 1.3));
    vec3 c = uColor * beat * glint * 1.4;`,
  // the summit star's glory: a soft glow with slow turning rays
  halo: `vec2 p = (vUv - 0.5) * 2.0; float r = length(p);
    float rays = pow(0.5 + 0.5 * cos(atan(p.y, p.x) * 10.0 + uTime * 0.6), 3.0) * smoothstep(1.0, 0.15, r);
    vec3 c = uColor * (pow(smoothstep(1.0, 0.0, r), 2.0) * 0.55 + rays * 0.3) * (0.85 + 0.15 * sin(uTime * 2.0));`,
};

function effect(kind: string, color: string, phase: number): THREE.ShaderMaterial {
  return new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    side: THREE.DoubleSide,
    uniforms: { uTime: clock, uColor: { value: new THREE.Color(color) }, uPhase: { value: phase } },
    vertexShader: VERTEX,
    fragmentShader: `${HEAD} void main(){ ${EFFECTS[kind]} gl_FragColor = vec4(c, 1.0); }`,
  });
}

/** Gives every effect mesh under `root` its shader, and the baked set pieces their unlit picture. */
export function dressStage(root: THREE.Object3D, maxAnisotropy: number) {
  let n = 0;
  root.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    const kind = mesh.userData.fx as string | undefined;
    const old = mesh.material as THREE.MeshStandardMaterial;
    if (kind && EFFECTS[kind]) {
      // the stage ships without normals (its sets are unlit); beams and tubes shade by theirs
      if (!mesh.geometry.attributes.normal) mesh.geometry.computeVertexNormals();
      mesh.material = effect(kind, (mesh.userData.color as string) ?? "#ffffff", n++ * 1.7);
      mesh.renderOrder = 2; // after the set and the cast, so the light lies over them
    } else if (old.emissiveMap) {
      // Blender baked light, shadow and reflections into this map: show it as it is, untouched by our lights
      old.emissiveMap.anisotropy = Math.min(8, maxAnisotropy);
      mesh.material = new THREE.MeshBasicMaterial({ map: old.emissiveMap, side: old.side });
    } else return;
    old.dispose();
  });
}

/** Confetti in the answer colours for the winner: one draw call, every flake moved in the vertex shader. */
export function confetti(count = 600, area = 16, height = 10): THREE.Points {
  const seeds = new Float32Array(count * 3);
  const colors = new Float32Array(count * 3);
  const palette = ["#2c6bed", "#f2862e", "#f6c731", "#e5487f", "#fbf8f1"].map((c) => new THREE.Color(c));
  for (let i = 0; i < count; i++) {
    seeds.set([(Math.random() - 0.5) * area, Math.random(), Math.random()], i * 3);
    colors.set(palette[i % palette.length].toArray(), i * 3);
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.BufferAttribute(seeds, 3));
  geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
  const material = new THREE.ShaderMaterial({
    uniforms: { uTime: clock },
    vertexShader: `uniform float uTime; attribute vec3 color; varying vec3 vC; varying float vSpin;
      void main(){
        float fall = fract(position.y - uTime * (0.10 + 0.08 * position.z));
        vec3 p = vec3(position.x + sin(uTime * 1.7 + position.z * 20.0) * 0.5, fall * ${height.toFixed(1)}, -3.0 + position.z * 8.0);
        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_PointSize = min(170.0 / -mv.z, 12.0);
        vC = color; vSpin = uTime * 4.0 + position.z * 30.0;
        gl_Position = projectionMatrix * mv;
      }`,
    fragmentShader: `varying vec3 vC; varying float vSpin;
      void main(){
        vec2 q = gl_PointCoord - 0.5; float s = abs(sin(vSpin));
        if (abs(q.x) > 0.5 * s + 0.08 || abs(q.y) > 0.3) discard;
        gl_FragColor = vec4(vC * (0.7 + 0.3 * s), 1.0);
        #include <colorspace_fragment>
      }`,
  });
  const points = new THREE.Points(geometry, material);
  points.frustumCulled = false; // the flakes are placed in the shader, far from their stored positions
  return points;
}

/** What shows past a set's edge: one colour, or a sky running from the first colour (top) to the last. */
export function skyTexture(stops: string[]): THREE.Texture {
  const canvas = document.createElement("canvas");
  canvas.width = 2;
  canvas.height = 256;
  const ctx = canvas.getContext("2d")!;
  const fade = ctx.createLinearGradient(0, 0, 0, canvas.height);
  stops.forEach((color, i) => fade.addColorStop(stops.length > 1 ? i / (stops.length - 1) : 0, color));
  ctx.fillStyle = fade;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

/** The one small picture all blob shadows of an arena share. */
export function shadowTexture(): THREE.Texture {
  const size = 64;
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = size;
  const ctx = canvas.getContext("2d")!;
  const fade = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
  fade.addColorStop(0, "rgba(0,0,0,0.6)");
  fade.addColorStop(0.55, "rgba(0,0,0,0.32)");
  fade.addColorStop(1, "rgba(0,0,0,0)");
  ctx.fillStyle = fade;
  ctx.fillRect(0, 0, size, size);
  return new THREE.CanvasTexture(canvas);
}

/** A soft dark disc under a figure. The baked floor cannot receive a moving shadow, and without one the
 *  cast floats. Small enough to stay on a pyramid tread. */
export function blobShadow(map: THREE.Texture): THREE.Mesh {
  const mesh = new THREE.Mesh(
    new THREE.PlaneGeometry(1.4, 1.4),
    new THREE.MeshBasicMaterial({ map, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2 }),
  );
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.y = 0.03;
  mesh.renderOrder = 1;
  return mesh;
}
