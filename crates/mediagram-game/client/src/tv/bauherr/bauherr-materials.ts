// The materials of the baked town. Everything is unlit (the light is in the vertex colours and the prints), because on
// the TV box per-pixel lighting is what costs. Texels with alpha below 1 are self-lit (lit windows, sign letters, the
// lantern): they ignore the baked light. Owner pieces are tinted per vertex with their owner's colour.

import * as THREE from "three";
import { LIGHT_MAX } from "./bauherr-data";

// bytes carry light / LIGHT_MAX; a self-lit texel shows its own colour: LIGHT_MAX · (1 / LIGHT_MAX)
const SELF_LIT = (1 / LIGHT_MAX).toFixed(4);

/** The baked town: map × baked vertex light, except where the map's alpha marks self-lit texels. */
export function townMaterial(map: THREE.Texture, params: THREE.MeshBasicMaterialParameters = {}): THREE.MeshBasicMaterial {
  const m = new THREE.MeshBasicMaterial({ map, vertexColors: true, ...params });
  m.color.setScalar(LIGHT_MAX);
  m.onBeforeCompile = (sh) => {
    sh.fragmentShader = sh.fragmentShader.replace(
      "#include <color_fragment>",
      `diffuseColor.rgb *= mix( vColor, vec3( ${SELF_LIT} ), 1.0 - sampledDiffuseColor.a );\n\tdiffuseColor.a = opacity;`,
    );
  };
  m.customProgramCacheKey = () => "bauherr-town";
  return m;
}

/** The town material for owner pieces: tinted per vertex (aTint 1) by `uOwner`, which changes when the hotel changes hands. */
export function ownerMaterial(map: THREE.Texture, params: THREE.MeshBasicMaterialParameters = {}) {
  const owner = { value: new THREE.Color(1, 1, 1) };
  const m = townMaterial(map, params);
  const lit = m.onBeforeCompile;
  m.onBeforeCompile = (sh, r) => {
    lit(sh, r);
    sh.uniforms.uOwner = owner;
    sh.vertexShader = sh.vertexShader
      .replace("#include <common>", "#include <common>\nattribute float aTint;\nvarying float vTint;")
      .replace("#include <begin_vertex>", "#include <begin_vertex>\n\tvTint = aTint;");
    sh.fragmentShader = sh.fragmentShader
      .replace("#include <common>", "#include <common>\nuniform vec3 uOwner;\nvarying float vTint;")
      .replace("diffuseColor.a = opacity;", "diffuseColor.rgb *= mix( vec3( 1.0 ), uOwner, vTint );\n\tdiffuseColor.a = opacity;");
  };
  m.customProgramCacheKey = () => "bauherr-owner";
  return { material: m, owner };
}

/**
 * A shadow decal: the texture stores the darkness a shadow takes away, and the blend multiplies the ground by 1 − it.
 * Destination alpha is kept, or the page background shows through as light boxes.
 */
export const shadowMaterial = (map: THREE.Texture | null, vertexColors: boolean) =>
  new THREE.MeshBasicMaterial({
    map, vertexColors, transparent: true, depthWrite: false, fog: false,
    blending: THREE.CustomBlending, blendSrc: THREE.ZeroFactor, blendDst: THREE.OneMinusSrcColorFactor, blendSrcAlpha: THREE.ZeroFactor, blendDstAlpha: THREE.OneFactor,
  });

/** Soft additive discs: the glow of lamps, standing in for bloom (a real bloom pass costs the TV box 14 to 23 fps). */
export const haloMaterial = (map: THREE.Texture) =>
  new THREE.MeshBasicMaterial({ map, vertexColors: true, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, fog: false });
