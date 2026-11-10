// Shader effects for the Studio arena, each a few arithmetic operations per pixel: no extra render passes,
// no textures read, no post-processing. One shared clock uniform drives them all.
import * as THREE from "three";

export const clock = { value: 0 };
const additive = { transparent: true, depthWrite: false, blending: THREE.AdditiveBlending };

/** The floor: navy with LED dots, a slow light sweep and a glow in the middle. */
export function floor(radius) {
  const m = new THREE.ShaderMaterial({
    uniforms: { uTime: clock },
    vertexShader: `varying vec2 vXZ; void main(){ vXZ = position.xy; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
    fragmentShader: `uniform float uTime; varying vec2 vXZ;
      void main(){
        float r = length(vXZ);
        vec3 c = mix(vec3(0.075,0.09,0.20), vec3(0.03,0.035,0.09), smoothstep(0.0, ${radius.toFixed(1)}, r));
        vec2 g = fract(vXZ * 1.25) - 0.5;
        c += vec3(0.10,0.15,0.40) * smoothstep(0.11, 0.04, length(g)) * 0.45;
        float a = atan(vXZ.y, vXZ.x);
        c += vec3(0.30,0.36,0.75) * pow(0.5 + 0.5 * cos(a - uTime * 0.7), 28.0) * smoothstep(${radius.toFixed(1)}, 2.0, r) * 0.55;
        c += vec3(0.16,0.22,0.55) * smoothstep(6.5, 0.0, r) * 0.4;
        gl_FragColor = vec4(c, 1.0);
      }`,
  });
  const mesh = new THREE.Mesh(new THREE.CircleGeometry(radius, 96), m);
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.y = 0.005;
  return mesh;
}

/** A light ring with a chase of brighter beads running round it. */
export function chaseRing(r, color, speed = 1) {
  const m = new THREE.ShaderMaterial({
    ...additive,
    side: THREE.DoubleSide,
    uniforms: { uTime: clock, uColor: { value: new THREE.Color(color) }, uR: { value: r }, uSpeed: { value: speed } },
    vertexShader: `varying vec2 vP; void main(){ vP = position.xy; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
    fragmentShader: `uniform float uTime, uR, uSpeed; uniform vec3 uColor; varying vec2 vP;
      void main(){
        float d = abs(length(vP) - uR);
        float core = smoothstep(0.09, 0.0, d), halo = smoothstep(0.75, 0.0, d) * 0.28;
        float chase = pow(0.5 + 0.5 * sin(atan(vP.y, vP.x) * 9.0 - uTime * 3.0 * uSpeed), 10.0);
        gl_FragColor = vec4(uColor * (core * (0.75 + 0.9 * chase) + halo * (0.6 + chase)), 1.0);
      }`,
  });
  const mesh = new THREE.Mesh(new THREE.RingGeometry(r - 0.8, r + 0.8, 160, 1), m);
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.y = 0.02;
  return mesh;
}

/** A light column's haze: bands of light travelling up it. */
export function columnHaze(color, phase) {
  return new THREE.ShaderMaterial({
    ...additive,
    uniforms: { uTime: clock, uColor: { value: new THREE.Color(color) }, uPhase: { value: phase } },
    vertexShader: `varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
    fragmentShader: `uniform float uTime, uPhase; uniform vec3 uColor; varying vec2 vUv;
      void main(){
        float across = pow(1.0 - abs(vUv.x - 0.5) * 2.0, 2.2);
        float band = pow(0.5 + 0.5 * sin(vUv.y * 10.0 - uTime * 2.4 + uPhase), 6.0);
        float ends = smoothstep(0.0, 0.15, vUv.y) * smoothstep(1.0, 0.75, vUv.y);
        gl_FragColor = vec4(uColor * across * ends * (0.16 + 0.55 * band), 1.0);
      }`,
  });
}

/** A spotlight: bright through its middle, soft at its edges, dust drifting in it. */
export function spotCone(color) {
  return new THREE.ShaderMaterial({
    ...additive,
    side: THREE.DoubleSide,
    uniforms: { uTime: clock, uColor: { value: new THREE.Color(color) } },
    vertexShader: `varying vec3 vN, vV; varying vec2 vUv;
      void main(){ vUv = uv; vec4 mv = modelViewMatrix * vec4(position,1.0); vN = normalize(normalMatrix * normal); vV = normalize(-mv.xyz);
        gl_Position = projectionMatrix * mv; }`,
    fragmentShader: `uniform float uTime; uniform vec3 uColor; varying vec3 vN, vV; varying vec2 vUv;
      void main(){
        float face = pow(abs(dot(vN, vV)), 2.0);
        float down = 0.35 + 0.65 * vUv.y;
        float dust = pow(fract(sin(dot(floor(vec2(vUv.x * 160.0, vUv.y * 110.0 + uTime * 4.0)), vec2(12.9898,78.233))) * 43758.5453), 30.0);
        gl_FragColor = vec4(uColor * (face * down * 0.22 + dust * 0.18 * face), 1.0);
      }`,
  });
}

/** A trick in flight: pulses of energy running along the arc towards the target. */
export function energy(color) {
  return new THREE.ShaderMaterial({
    ...additive,
    uniforms: { uTime: clock, uColor: { value: new THREE.Color(color) } },
    vertexShader: `varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
    fragmentShader: `uniform float uTime; uniform vec3 uColor; varying vec2 vUv;
      void main(){
        float pulse = pow(fract(vUv.x * 5.0 - uTime * 1.6), 3.0);
        float side = pow(1.0 - abs(vUv.y - 0.5) * 2.0, 1.5);
        gl_FragColor = vec4(uColor * (0.7 + 2.2 * pulse) * (0.5 + side), 1.0);
      }`,
  });
}

/** A pulsing glow ring under a player, e.g. for a vote cast or a right answer. */
export function padGlow(color, phase = 0) {
  const m = new THREE.ShaderMaterial({
    ...additive,
    uniforms: { uTime: clock, uColor: { value: new THREE.Color(color) }, uPhase: { value: phase } },
    vertexShader: `varying vec2 vP; void main(){ vP = position.xy; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
    fragmentShader: `uniform float uTime, uPhase; uniform vec3 uColor; varying vec2 vP;
      void main(){
        float r = length(vP);
        float wave = fract(uTime * 0.8 + uPhase);
        float ring = smoothstep(0.12, 0.0, abs(r - (0.9 + wave * 0.9))) * (1.0 - wave);
        float base = smoothstep(1.4, 0.8, r) * 0.35;
        gl_FragColor = vec4(uColor * (ring * 0.9 + base), 1.0);
      }`,
  });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(4, 4), m);
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.y = 0.16;
  return mesh;
}

/** Confetti in the answer colours: one draw call, every flake moved in the vertex shader. */
export function confetti(count = 700, area = 18, height = 9) {
  const seeds = new Float32Array(count * 3), colors = new Float32Array(count * 3);
  const palette = ["#2c6bed", "#f2862e", "#f6c731", "#e5487f", "#fbf8f1"].map((c) => new THREE.Color(c));
  for (let i = 0; i < count; i++) {
    seeds.set([(Math.random() - 0.5) * area, Math.random(), Math.random()], i * 3);
    colors.set(palette[i % palette.length].toArray(), i * 3);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute("position", new THREE.BufferAttribute(seeds, 3));
  g.setAttribute("color", new THREE.BufferAttribute(colors, 3));
  const m = new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    uniforms: { uTime: clock },
    vertexShader: `uniform float uTime; attribute vec3 color; varying vec3 vC; varying float vSpin;
      void main(){
        float fall = fract(position.y - uTime * (0.10 + 0.08 * position.z));
        vec3 p = vec3(position.x + sin(uTime * 1.7 + position.z * 20.0) * 0.5, fall * ${height.toFixed(1)}, -2.0 + position.z * 8.0);
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
      }`,
  });
  return new THREE.Points(g, m);
}

/** Edge light for the matcap avatars: a few operations in their shader, in a colour per player. From the smooth
 *  surface normal, not the normal map, so a light map's bumps do not light up inside the silhouette. */
export function rimLight(shader, color, strength) {
  shader.uniforms.uRim = { value: new THREE.Color(color) };
  shader.uniforms.uRimStrength = { value: strength };
  shader.fragmentShader = shader.fragmentShader
    .replace("void main() {", "uniform vec3 uRim; uniform float uRimStrength;\nvoid main() {")
    .replace("#include <opaque_fragment>",
      "outgoingLight += uRim * pow(1.0 - clamp(dot(normalize(vNormal), normalize(vViewPosition)), 0.0, 1.0), 3.5) * uRimStrength;\n#include <opaque_fragment>");
}

// ── Neon look: grid lines and lit edges on dark shapes ──────────────────────────────────────────────

const plain = (fragment, uniforms = {}) => new THREE.ShaderMaterial({
  uniforms: { uTime: clock, ...uniforms },
  vertexShader: `varying vec2 vUv; varying vec3 vP; void main(){ vUv = uv; vP = position; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
  fragmentShader: `uniform float uTime; uniform vec3 uColor; varying vec2 vUv; varying vec3 vP; ${fragment}`,
});

/** A grid floor: sharp lines at any distance (fwidth), waves of light running across them, fading at the rim. */
export function gridFloor(radius, color) {
  const m = plain(`void main(){
      vec2 p = vP.xy / 1.4;
      vec2 g = abs(fract(p - 0.5) - 0.5) / fwidth(p);
      float line = 1.0 - min(min(g.x, g.y), 1.0);
      float r = length(vP.xy);
      float wave = pow(0.5 + 0.5 * sin((vP.x * 0.8 + vP.y) * 0.32 - uTime * 2.2), 16.0);
      vec3 c = vec3(0.008, 0.012, 0.03) + uColor * 0.06 * smoothstep(9.0, 0.0, r);
      c += uColor * line * (0.32 + 1.3 * wave) * smoothstep(${radius.toFixed(1)}, ${(radius * 0.25).toFixed(1)}, r);
      gl_FragColor = vec4(c, 1.0);
    }`, { uColor: { value: new THREE.Color(color) } });
  const mesh = new THREE.Mesh(new THREE.CircleGeometry(radius, 96), m);
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.y = 0.006;
  return mesh;
}

/** The arena wall seen from inside: dark panels, glowing seams, a bright top edge, a slow pulse running round. */
export function gridWall(radius, height, color) {
  const m = plain(`void main(){
      vec2 p = vec2(vUv.x * 56.0, vUv.y * 4.0);
      vec2 g = abs(fract(p - 0.5) - 0.5) / fwidth(p);
      float seam = 1.0 - min(min(g.x, g.y), 1.0);
      float top = smoothstep(0.03, 0.0, 1.0 - vUv.y) + 0.35 * smoothstep(0.12, 0.0, 1.0 - vUv.y);
      float foot = smoothstep(0.05, 0.0, vUv.y);
      float run = pow(0.5 + 0.5 * sin(vUv.x * 6.2832 * 3.0 - uTime * 0.9), 10.0);
      vec3 c = mix(vec3(0.012, 0.02, 0.05), vec3(0.03, 0.05, 0.11), vUv.y);
      c += uColor * (seam * (0.22 + 0.8 * run) + top * 1.4 + foot * 0.6);
      gl_FragColor = vec4(c, 1.0);
    }`, { uColor: { value: new THREE.Color(color) } });
  m.side = THREE.BackSide;
  const wall = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, height, 128, 1, true, Math.PI * 0.55, Math.PI * 0.9), m);
  wall.position.y = height / 2;
  return wall;
}

/** A dark block with lit edges (box faces have 0–1 UVs each). */
export function edgeBox(color) {
  return plain(`void main(){
      float d = min(min(vUv.x, 1.0 - vUv.x), min(vUv.y, 1.0 - vUv.y));
      float edge = smoothstep(0.03, 0.0, d) * 1.4 + smoothstep(0.16, 0.0, d) * 0.25;
      gl_FragColor = vec4(vec3(0.015, 0.022, 0.05) + uColor * edge, 1.0);
    }`, { uColor: { value: new THREE.Color(color) } });
}

/** A dark drum with a lit top edge and faint vertical seams; caps get a lit rim. Use as [side, cap, cap]. */
export function edgeDrum(color) {
  const side = plain(`void main(){
      vec2 p = vec2(vUv.x * 14.0, 0.0);
      float seam = 1.0 - min(abs(fract(p.x - 0.5) - 0.5) / fwidth(p.x), 1.0);
      float top = smoothstep(0.08, 0.0, 1.0 - vUv.y) * 1.3 + smoothstep(0.05, 0.0, vUv.y) * 0.5;
      gl_FragColor = vec4(vec3(0.015, 0.022, 0.05) + uColor * (top + seam * 0.18), 1.0);
    }`, { uColor: { value: new THREE.Color(color) } });
  const cap = plain(`void main(){
      float r = length(vUv - 0.5) * 2.0;
      float rim = smoothstep(0.05, 0.0, abs(r - 0.94)) * 1.4 + smoothstep(0.35, 0.0, abs(r - 0.94)) * 0.15;
      gl_FragColor = vec4(vec3(0.02, 0.03, 0.07) + uColor * rim, 1.0);
    }`, { uColor: { value: new THREE.Color(color) } });
  return [side, cap, cap];
}

// ── Dithering (?dither=1): noise of half an 8-bit step on every pixel breaks the bands in dark gradients ────────

export const DITHER = new URLSearchParams(location.search).get("dither") === "1";
// interleaved gradient noise: a fixed screen pattern, no texture, no animation
export const ditherGlsl = "gl_FragColor.rgb += (fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) - 0.5) / 255.0;\n";

/** The backdrop as a screen-filling shader: top to bottom through up to three sRGB stops, computed per pixel (a
 *  canvas texture holds 8 bits a channel, so its gradient is stepped before any dithering could help). */
export function backdrop(stops) {
  const hex = (c) => new THREE.Vector3(...[1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16) / 255));
  const [s0, s1, s2] = [0, 1, 2].map((i) => stops[Math.min(i, stops.length - 1)]);
  const m = new THREE.ShaderMaterial({
    depthTest: false, depthWrite: false,
    uniforms: { uC0: { value: hex(s0[1]) }, uC1: { value: hex(s1[1]) }, uC2: { value: hex(s2[1]) }, uAt: { value: s1[0] } },
    vertexShader: `varying float vT; void main(){ vT = 0.5 - position.y * 0.5; gl_Position = vec4(position.xy, 1.0, 1.0); }`,
    fragmentShader: `uniform vec3 uC0, uC1, uC2; uniform float uAt; varying float vT;
      void main(){
        vec3 c = vT < uAt ? mix(uC0, uC1, vT / uAt) : mix(uC1, uC2, (vT - uAt) / (1.0 - uAt));
        gl_FragColor = vec4(c, 1.0);
      }`,
  });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), m);
  mesh.frustumCulled = false;
  mesh.renderOrder = -1;
  return mesh;
}
