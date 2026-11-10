// Simplifies an avatar to a triangle budget while keeping its face and hands denser than its body: on the TV the eye
// goes to faces and gestures, and a uniform cut leaves the face as faceted as the back. Two meshoptimizer passes over
// one index buffer: the body is reduced while the face and hands are locked, then the face and hands while the body
// is locked. Seams stay put as in gltf-transform's simplify (meshoptimizer does not collapse across them).
import { createRequire } from 'node:module';

const req = createRequire(new URL('../client/package.json', import.meta.url).pathname);
const { compactPrimitive } = req('@gltf-transform/functions');
const { MeshoptSimplifier } = req('meshoptimizer');

// Meshy's rig: Head, head_end, headfront, LeftHand, RightHand (no finger bones)
const DETAIL_JOINT = /head|hand/i;

/** 1 for each vertex with at least half its skin weight on a head or hand joint. */
function detailVertices(prim, skin) {
  const joints = prim.getAttribute('JOINTS_0'), weights = prim.getAttribute('WEIGHTS_0');
  if (!joints || !weights || !skin) throw new Error('favouring the face and hands needs a skinned mesh');
  const names = skin.listJoints().map((j) => j.getName()), detailJoint = names.map((n) => DETAIL_JOINT.test(n));
  if (!detailJoint.includes(true)) throw new Error(`no head or hand joint among ${names.join(', ')}`);
  const n = joints.getCount(), out = new Uint8Array(n), j = [0, 0, 0, 0], w = [0, 0, 0, 0];
  for (let v = 0; v < n; v++) {
    joints.getElement(v, j);
    weights.getElement(v, w);
    let s = 0;
    for (let k = 0; k < 4; k++) if (detailJoint[j[k]]) s += w[k];
    out[v] = s >= 0.5 ? 1 : 0;
  }
  return out;
}

/** Simplifies `prim` (welded, float positions) to about `target` triangles with `boost` times the body's triangle
 *  density on the face and hands, which are never refined beyond what they have. Returns a line for the log. */
export function simplifyFavouringFaceAndHands(prim, skin, target, boost) {
  const pos = prim.getAttribute('POSITION').getArray();
  if (!(pos instanceof Float32Array)) throw new Error('simplify before quantizing');
  const detail = detailVertices(prim, skin);
  let idx = Uint32Array.from(prim.getIndices().getArray());
  let faceTris = 0;
  for (let t = 0; t < idx.length; t += 3) if (detail[idx[t]] + detail[idx[t + 1]] + detail[idx[t + 2]] >= 2) faceTris++;
  const bodyTris = idx.length / 3 - faceTris;
  // keep-ratios: face = boost · body, face · faceTris + body · bodyTris = target
  let body = target / (boost * faceTris + bodyTris), face = Math.min(1, boost * body);
  if (face === 1) body = Math.max(0, (target - faceTris) / bodyTris);
  body = Math.min(1, body);
  const keepFace = Math.round(face * faceTris), keepBody = Math.round(body * bodyTris);
  const pass = (indices, lock, triangles) =>
    MeshoptSimplifier.simplifyWithAttributes(indices, pos, 3, new Float32Array(0), 0, [], lock, Math.min(triangles * 3, indices.length), 0.1)[0];
  idx = pass(idx, detail, faceTris + keepBody);
  if (keepFace < faceTris) idx = pass(idx, detail.map((d) => 1 - d), keepFace + keepBody);
  prim.getIndices().setArray(idx);
  compactPrimitive(prim);
  return `face and hands ${faceTris} -> ~${keepFace}, body ${bodyTris} -> ~${keepBody} triangles`;
}
