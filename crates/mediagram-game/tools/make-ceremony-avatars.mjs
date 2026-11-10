// Makes the awards-ceremony copy of each game-night avatar from its 2K game copy: only the five clips the podium
// plays, all ~51k triangles (the TV box draws three at ~44 fps at full resolution; the user's floor is 30, and 30k
// looked low-poly), colour and normal maps re-unwrapped into padded charts and baked from the 4K master's maps
// (rebake-texture-atlas.mjs) into WebP (EXT_texture_webp, which GLTFLoader reads natively): colour 2048, normal 1024
// (fabric folds and fur relief; an avatar is ~600 px tall on the TV). Compressed with EXT_meshopt_compression (the
// page needs MeshoptDecoder). No metallic/roughness map: the look is matte Lambert.
// Usage: bun crates/mediagram-game/tools/make-ceremony-avatars.mjs [--tris=N] [--tex=2048] [--ntex=1024] [--out=dir]
//   [--in=dir] [--clips=a,b,...] [--padding=6] [--textures=webp|uastc] [--detail=1] [--ao=0] [id ...]
// The Kopfnuss arena's copies: --tris=20000 --detail=1.5 --ao=0.7 --padding=4 --textures=uastc --out=<dir> --clips=...
// --textures=uastc writes KTX2 UASTC (KHR_texture_basisu; the page needs KTX2Loader) and needs KTX-Software's `ktx`
// on PATH. --detail=N keeps N times the body's triangle density on the face and hands; --ao=S multiplies ambient
// occlusion at strength S (0..1) into the colour map.
// Reads assets/game-night/avatars/<id>/<id>.glb and <id>-master.glb (kept out of git; --in elsewhere), writes
// android/app/src/main/assets/game-avatars/<id>.glb (generated and gitignored; the TV app serves it to the game page).
// Resolves its packages from the client's (bun install in crates/mediagram-game/client).
import { existsSync, mkdirSync, readdirSync, renameSync, rmSync, statSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { bakeAmbientOcclusion } from './bake-ambient-occlusion.mjs';
import { fullDetailNormalFrames } from './full-detail-normal-frames.mjs';
import { padTextureIslands } from './pad-texture-islands.mjs';
import { rebakeTextureAtlas } from './rebake-texture-atlas.mjs';
import { simplifyFavouringFaceAndHands } from './simplify-favouring-face-and-hands.mjs';

const here = (p) => new URL(p, import.meta.url).pathname;
const opt = (name, dflt) => process.argv.find((a) => a.startsWith(`--${name}=`))?.slice(name.length + 3) ?? dflt;
const IN_DIR = opt('in', here('../../../assets/game-night/avatars/')).replace(/\/?$/, '/');
const KEEP = opt('clips', 'Idle_15,victory,Happy_Jump,Cheer_With_One_Hand_Up,Shrug').split(',');
const TRIS = Number(opt('tris', Infinity));
// texels between charts; 4 at 2048 lets the charts cover 64% of the map instead of 43% at 6 (block-aligned for KTX2)
const PADDING = Number(opt('padding', 6));
// uastc stays compressed in GPU memory (sharper at 2048 than webp at 1024, in less memory); webp is unpacked there
const FORMAT = opt('textures', 'webp');
if (!['webp', 'uastc'].includes(FORMAT)) throw new Error('--textures is webp or uastc');
const DETAIL = Number(opt('detail', 1));
const AO = Number(opt('ao', 0));
if (!(DETAIL >= 1) || !(AO >= 0 && AO <= 1)) throw new Error('--detail is 1 or more, --ao between 0 and 1');
const TEX = Number(opt('tex', 2048));
const NTEX = Number(opt('ntex', 1024));
// absolute: the KTX2 step runs in the client's folder
const OUT_DIR = resolve(opt('out', here('../../../android/app/src/main/assets/game-avatars/'))) + '/';
if (!(TRIS > 0) || !(TEX > 0) || !(NTEX > 0) || !(PADDING >= 0)) throw new Error('--tris, --tex, --ntex and --padding must be positive numbers');

const req = createRequire(here('../client/package.json'));
let core, extensions, functions, sharp, MeshoptEncoder, MeshoptDecoder, MeshoptSimplifier;
try {
  core = req('@gltf-transform/core');
  extensions = req('@gltf-transform/extensions');
  functions = req('@gltf-transform/functions');
  sharp = req('sharp');
  ({ MeshoptEncoder, MeshoptDecoder, MeshoptSimplifier } = req('meshoptimizer'));
} catch (e) {
  throw new Error(`packages are not installed: run bun install in crates/mediagram-game/client (${e.message})`);
}
const { NodeIO } = core;
const { prune, dedup, weld, simplify, quantize, textureCompress, meshopt } = functions;
const io = new NodeIO().registerExtensions(extensions.ALL_EXTENSIONS)
  .registerDependencies({ 'meshopt.encoder': MeshoptEncoder, 'meshopt.decoder': MeshoptDecoder });

const triangles = (root) => root.listMeshes().flatMap((m) => m.listPrimitives())
  .reduce((n, p) => n + p.getIndices().getCount() / 3, 0);

const all = readdirSync(IN_DIR).filter((id) => existsSync(`${IN_DIR}${id}/${id}.glb`)).sort();
const ids = process.argv.slice(2).filter((a) => !a.startsWith('--'));
if (ids.length === 0) ids.push(...all);
for (const id of ids) if (!all.includes(id)) throw new Error(`no ${IN_DIR}${id}/${id}.glb`);
mkdirSync(OUT_DIR, { recursive: true });
await Promise.all([MeshoptEncoder.ready, MeshoptDecoder.ready, MeshoptSimplifier.ready]);

/** The rebake samples the 4K colour and normal maps of `<id>-master.glb` instead of the game copy's 2K ones; both
 *  files carry the same mesh and texture coordinates, which is checked. */
async function useMasterMaps(doc, id) {
  const prim = (d) => d.getRoot().listMeshes()[0].listPrimitives()[0];
  const master = prim(await io.read(`${IN_DIR}${id}/${id}-master.glb`)), copy = prim(doc);
  const a = master.getAttribute('TEXCOORD_0').getArray(), b = copy.getAttribute('TEXCOORD_0').getArray();
  if (a.length !== b.length || a.some((x, i) => x !== b[i])) throw new Error(`${id}: master and game copy differ in texture coordinates`);
  const [mm, cm] = [master.getMaterial(), copy.getMaterial()];
  for (const slot of ['BaseColor', 'Normal']) {
    const from = mm[`get${slot}Texture`](), to = cm[`get${slot}Texture`]();
    if (!from || !to) throw new Error(`${id}: no ${slot} map in the master or the game copy`);
    to.setImage(from.getImage()).setMimeType(from.getMimeType());
  }
}

let total = 0;
for (const id of ids) {
  const doc = await io.read(`${IN_DIR}${id}/${id}.glb`);
  const root = doc.getRoot();
  const names = root.listAnimations().map((a) => a.getName());
  for (const k of KEEP) if (!names.includes(k)) throw new Error(`${id}: clip ${k} is missing (has ${names.join(', ')})`);
  for (const a of root.listAnimations()) if (!KEEP.includes(a.getName())) a.dispose();
  const before = triangles(root);
  for (const m of root.listMaterials()) {
    // Meshy's metallic/roughness map is near-uniform: its mean as a factor looks the same (Lambert ignores both anyway).
    const mr = m.getMetallicRoughnessTexture();
    if (mr) {
      const { channels } = await sharp(Buffer.from(mr.getImage())).stats();
      m.setRoughnessFactor(m.getRoughnessFactor() * channels[1].mean / 255);
      m.setMetallicFactor(m.getMetallicFactor() * channels[2].mean / 255);
      m.setMetallicRoughnessTexture(null);
    }
  }
  await useMasterMaps(doc, id);
  await padTextureIslands(doc); // on the full-detail mesh, so island extents are exact
  if (AO > 0) console.log(await bakeAmbientOcclusion(doc, AO));
  await doc.transform(prune(), weld());
  // the normal map is rebaked against the full mesh, so detail the simplification flattens stays in it
  const fullFrame = TRIS < before ? fullDetailNormalFrames(root.listMeshes()[0].listPrimitives()[0]) : null;
  if (TRIS < before && DETAIL > 1) {
    const mesh = root.listMeshes()[0], skin = root.listNodes().find((n) => n.getMesh() === mesh)?.getSkin();
    console.log(simplifyFavouringFaceAndHands(mesh.listPrimitives()[0], skin, TRIS, DETAIL));
  } else if (TRIS < before) {
    // Seams stay put (the default): every triangle stays inside one of Meshy's islands, so the rebake below can
    // interpolate its old texture coordinates (collapsing across them also smeared the islands over the face).
    await doc.transform(simplify({ simplifier: MeshoptSimplifier, ratio: TRIS / before, error: 0.1 }));
  }
  const rebaked = await rebakeTextureAtlas(doc, TEX, PADDING, fullFrame);
  const raster = FORMAT === 'uastc' ? 'png' : FORMAT; // the KTX2 encoder reads PNG
  // quality is for webp only: given to PNG, sharp turns it into a 256-colour palette
  const q = (n) => (raster === 'webp' ? { quality: n } : {});
  await doc.transform(
    textureCompress({ encoder: sharp, targetFormat: raster, slots: /^baseColorTexture$/, resize: [TEX, TEX], ...q(90) }),
    textureCompress({ encoder: sharp, targetFormat: raster, slots: /^normalTexture$/, resize: [NTEX, NTEX], ...q(95) }),
    quantize(),
    prune(),
    dedup(),
    meshopt({ encoder: MeshoptEncoder, level: 'high' }),
  );
  // built and checked under a temporary name, so a failed step never leaves a half-made avatar where the app ships it
  const out = `${OUT_DIR}${id}.glb`, tmp = `${OUT_DIR}${id}.part.glb`;
  try {
    await io.write(tmp, doc);
    if (FORMAT === 'uastc') {
      // gltf-transform's command line in its own process: its ES-module copy of the library cannot share a document
      // with the CommonJS copy loaded here. Its write drops the meshopt compression, so that runs again after.
      for (const args of [['uastc', tmp, tmp, '--slots', '{baseColorTexture,normalTexture}', '--level', '2', '--rdo', '--zstd', '18'],
        ['meshopt', tmp, tmp, '--level', 'high']]) {
        const r = spawnSync('bunx', ['gltf-transform', ...args], { cwd: here('../client/'), encoding: 'utf8' });
        if (r.status !== 0) throw new Error(`${id}: gltf-transform ${args[0]} failed (is KTX-Software's ktx on PATH?) ${r.stderr || r.error}`);
      }
    }
    // read back: the file on disk is what the app ships
    const back = (await io.read(tmp)).getRoot();
    if (FORMAT === 'uastc' && !back.listExtensionsUsed().some((e) => e.extensionName === 'EXT_meshopt_compression')) throw new Error(`${id}: meshopt compression lost`);
    const clips = back.listAnimations().map((a) => a.getName()).sort().join();
    if (clips !== [...KEEP].sort().join()) throw new Error(`${id}: wrote clips ${clips}`);
    const mat = back.listMaterials()[0], colour = mat?.getBaseColorTexture(), normal = mat?.getNormalTexture();
    const mime = FORMAT === 'uastc' ? 'image/ktx2' : `image/${FORMAT}`;
    if (back.listTextures().length !== 2 || [colour, normal].some((t) => t?.getMimeType() !== mime)) {
      throw new Error(`${id}: expected a ${mime} colour and normal map, got ${back.listTextures().map((t) => t.getMimeType()).join()}`);
    }
    renameSync(tmp, out);
    const size = statSync(out).size;
    total += size;
    console.log(`${id}.glb ${(size / 1024).toFixed(0)} KB, ${before} -> ${triangles(back)} triangles, ${rebaked}, ` +
      `colour ${colour.getSize()?.join('x')}, normal ${normal.getSize()?.join('x')}`);
  } finally {
    rmSync(tmp, { force: true });
  }
}
console.log(`total ${(total / 1048576).toFixed(2)} MB for ${ids.length} avatars`);
