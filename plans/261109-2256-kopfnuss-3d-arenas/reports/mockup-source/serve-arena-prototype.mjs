// PROTOTYPE: serves the Kopfnuss arena mockup with its avatar files and opens nothing; visit the printed URL.
// usage: bun plans/261109-2256-kopfnuss-3d-arenas/reports/mockup-source/serve-arena-prototype.mjs
// The avatar files are out of git, so they are read from the main mediagram checkout.
import { existsSync } from "node:fs";
const repo = `${process.env.HOME}/Workspace/mediagram`;
const roots = {
  "/game-avatars/": `${repo}/android/spieltisch/src/main/assets/game-avatars/`, // ceremony copies (variant A)
  "/avatar-games/": `${repo}/assets/game-night/avatars/`,                      // 2K game copies (variants B, C)
  "/basis/": `${repo}/crates/mediagram-game/client/public/basis/`,
};
const server = Bun.serve({
  port: 8741,
  fetch(req) {
    const path = decodeURIComponent(new URL(req.url).pathname);
    const [prefix, dir] = Object.entries(roots).find(([p]) => path.startsWith(p)) ?? ["/", `${import.meta.dir}/`];
    const file = dir + (path.slice(prefix.length) || "index.html");
    return existsSync(file) ? new Response(Bun.file(file)) : new Response("not found", { status: 404 });
  },
});
console.log(`http://127.0.0.1:${server.port}/index.html?theme=studio&stage=question&fx=1&host=walnut&variant=C&live=1`);
