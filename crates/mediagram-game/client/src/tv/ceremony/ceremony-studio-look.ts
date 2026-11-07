// The avatars' light: the soft studio light of the viewer the avatars were approved in (model-viewer's neutral
// environment, which three ships as RoomEnvironment, at exposure 1.1), without reflections. It is rendered once onto
// a white sphere as the ceremony camera sees it; each avatar pixel then looks that picture up by its surface
// direction (a "matcap") and multiplies its colour by it. The camera never moves, so this matches real
// environment lighting, which ran at 14–17 fps on the TV box against ~42 for the lookup.

import * as THREE from "three";
import { RoomEnvironment } from "three/examples/jsm/environments/RoomEnvironment.js";

/** The viewer's exposure, folded into the light. */
const EXPOSURE = 1.1;
const SIZE = 256;
/** The picture holds light ÷ HEADROOM, so 8 bits reach above white; the shader multiplies it back. Half-float
 *  pictures would need no headroom, but the box reads them so slowly that the ceremony fell to 25 fps. */
const HEADROOM = 2;

export interface StudioLook {
  /** The avatar's material for the loaded one (keeps its colour and normal maps). */
  dress(old: THREE.MeshStandardMaterial): THREE.Material;
  dispose(): void;
}

/** Bakes the light picture with `renderer`, as `camera` sees the stage. */
export function bakeStudioLook(renderer: THREE.WebGLRenderer, camera: THREE.Camera): StudioLook {
  const pmrem = new THREE.PMREMGenerator(renderer);
  const env = pmrem.fromScene(new RoomEnvironment(), 0.04);
  const studio = new THREE.Scene();
  studio.environment = env.texture;
  studio.environmentIntensity = EXPOSURE / HEADROOM;
  // diffuse light only: no specular, so no reflections
  const sphere = new THREE.Mesh(
    new THREE.SphereGeometry(1, 96, 48),
    new THREE.MeshPhysicalMaterial({ color: 0xffffff, roughness: 1, specularIntensity: 0 }),
  );
  studio.add(sphere);
  // looking at the sphere the way the ceremony camera looks at the stage, so the light falls the same way
  const eye = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 10);
  eye.quaternion.copy(camera.quaternion);
  eye.position.set(0, 0, 5).applyQuaternion(camera.quaternion);
  // sRGB-encoded 8 bits: the dark side of the sphere keeps its gradations (linear 8 bits would band)
  const light = new THREE.WebGLRenderTarget(SIZE, SIZE, { colorSpace: THREE.SRGBColorSpace });
  renderer.setRenderTarget(light);
  renderer.render(studio, eye);
  renderer.setRenderTarget(null);
  sphere.geometry.dispose();
  sphere.material.dispose();
  env.dispose();
  pmrem.dispose();

  const dressed: THREE.Material[] = [];
  return {
    dress(old) {
      const material = new THREE.MeshMatcapMaterial({
        map: old.map,
        // GLTFLoader's normalScale (y flipped for the derived tangents the rebake assumes)
        normalMap: old.normalMap ?? undefined,
        normalScale: old.normalScale,
        matcap: light.texture,
        color: old.map ? 0xffffff : old.color,
        // Meshy marks every avatar double-sided, but they are closed figures: front faces alone look the same on
        // all 22 (capes and hoods included) and spare the box shading every hidden back face
        side: THREE.FrontSide,
        transparent: old.transparent,
        alphaTest: old.alphaTest,
      });
      material.onBeforeCompile = (shader) => {
        shader.fragmentShader = shader.fragmentShader.replace(
          "vec3 outgoingLight = diffuseColor.rgb * matcapColor.rgb;",
          `vec3 outgoingLight = diffuseColor.rgb * matcapColor.rgb * ${HEADROOM.toFixed(1)};`,
        );
      };
      material.customProgramCacheKey = () => "ceremony-studio-look";
      dressed.push(material);
      return material;
    },
    dispose() {
      for (const m of dressed) m.dispose();
      light.dispose();
    },
  };
}
