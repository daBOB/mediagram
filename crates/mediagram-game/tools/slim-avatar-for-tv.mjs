// Makes the TV-box copy of a game-night avatar, assets/game-night/avatars/<id>/<id>-tv.glb, which
// render-avatar-images.mjs reads: fewer triangles, 1K GPU-compressed textures (KTX2), quantized vertex
// data, and the near-uniform metallic/roughness map replaced by one value. All animation clips are kept.
// Usage: bun crates/mediagram-game/tools/slim-avatar-for-tv.mjs <in.glb> <out.glb> <target triangles>
// The 22 copies were made from <id>.glb (the 2K game copy) at 15000 triangles.
// Needs toktx (KTX-Software 4.4.2) on PATH; three.js then needs KTX2Loader to read the textures.
// Resolves its packages from the client's (bun install in crates/mediagram-game/client).
import { createRequire } from 'node:module';
import { padTextureIslands } from './pad-texture-islands.mjs';

const [src, dst, targetArg] = process.argv.slice(2);
const target = Number(targetArg);
if (!src || !dst || !(target > 0)) {
  console.error('usage: bun slim-avatar-for-tv.mjs <in.glb> <out.glb> <target triangles>');
  process.exit(2);
}

const clientDir = new URL('../client/', import.meta.url).pathname;
const req = createRequire(`${clientDir}package.json`);
// The gltf-transform family loads as ES modules, as the CLI imports core: required as CommonJS,
// core would be a second copy whose documents the CLI's toktx() cannot find.
const esm = (name) => import(Bun.resolveSync(name, clientDir));
let core, extensions, functions, cli, sharp, MeshoptSimplifier;
try {
  [core, extensions, functions, cli] = await Promise.all(
    ['@gltf-transform/core', '@gltf-transform/extensions', '@gltf-transform/functions', '@gltf-transform/cli'].map(esm),
  );
  sharp = req('sharp');
  ({ MeshoptSimplifier } = req('meshoptimizer'));
} catch (e) {
  throw new Error(`packages are not installed: run bun install in crates/mediagram-game/client (${e.message})`);
}
const { dedup, prune, quantize, simplify, textureCompress, weld } = functions;
const { Mode, toktx } = cli;

const io = new core.NodeIO().registerExtensions(extensions.ALL_EXTENSIONS);
const doc = await io.read(src);
const triangles = () => doc.getRoot().listMeshes().flatMap((m) => m.listPrimitives())
  .reduce((n, p) => n + p.getIndices().getCount() / 3, 0);
const before = triangles();
const clips = doc.getRoot().listAnimations().length;

// Meshy's metallic/roughness map: metallic is 0 everywhere and roughness nearly flat, so the
// texture's mean (times the factor) gives the same look without a texture fetch per pixel.
for (const mat of doc.getRoot().listMaterials()) {
  const tex = mat.getMetallicRoughnessTexture();
  if (!tex) continue;
  const { channels } = await sharp(Buffer.from(tex.getImage())).stats();
  mat.setRoughnessFactor(mat.getRoughnessFactor() * channels[1].mean / 255);
  mat.setMetallicFactor(mat.getMetallicFactor() * channels[2].mean / 255);
  mat.setMetallicRoughnessTexture(null);
  console.log(`${mat.getName()}: roughness ${mat.getRoughnessFactor().toFixed(3)}, metallic ${mat.getMetallicFactor().toFixed(3)}`);
}

await padTextureIslands(doc);
await MeshoptSimplifier.ready;
await doc.transform(
  weld(),
  // Seams stay put: Meshy lays the texture out in many small islands, and letting the simplifier
  // collapse across them (meshoptimizer 'Permissive') smeared neighbouring islands over the face.
  // So the fox cannot go below about 12.7k triangles; a lower target stops there.
  simplify({ simplifier: MeshoptSimplifier, ratio: target / before, error: 0.1 }),
  textureCompress({ encoder: sharp, resize: [1024, 1024] }),
  // colour in the small ETC1S format; normals in UASTC, since ETC1S blocks show as dents on a normal map
  toktx({ mode: Mode.ETC1S, slots: /^baseColorTexture$/, quality: 160 }),
  toktx({ mode: Mode.UASTC, slots: /^normalTexture$/, level: 2, zstd: 18 }),
  quantize(),
  prune(),
  dedup(),
);
await io.write(dst, doc);

const after = triangles();
if (doc.getRoot().listAnimations().length !== clips) throw new Error('animation clips were lost');
const textures = doc.getRoot().listTextures().map((t) => `${t.getMimeType()} ${t.getSize()?.join('x')}`);
console.log(`${dst}: ${before} -> ${after} triangles, ${clips} clips, textures: ${textures.join(', ')}`);
