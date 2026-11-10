// Renders the arena mockup per theme and stage in headless Chrome at 1920×1080.
// usage: bun shoot-arenas.mjs <out-dir> theme:stage [theme:stage ...]
const [out, ...shots] = process.argv.slice(2);
const B = `${process.env.HOME}/.claude/skills/gstack/browse/dist/browse`;
const run = (...a) => {
  const r = Bun.spawnSync([B, ...a], { cwd: "/home/andre/Workspace/spieltisch/wt-dreifelder-merge" });
  return (r.stdout.toString() + r.stderr.toString()).trim();
};
run("viewport", "1920x1080");
for (const shot of shots) {
  const [theme, stage, fx, lod] = shot.split(":");
  run("goto", `http://127.0.0.1:8741/index.html?theme=${theme}&stage=${stage}${fx ? "&fx=1" : ""}${lod ? `&lod=${lod}` : ""}&n=${Date.now()}`);
  let title = "";
  for (let i = 0; i < 120 && !title.startsWith("ready"); i++) {
    await Bun.sleep(250);
    title = run("js", "document.title").split("\n").at(-1);
  }
  if (!title.startsWith("ready")) console.log(shot, "NOT READY", run("console", "--errors").slice(-600));
  if (fx) run("js", "setT(1.2)");
  run("screenshot", "--viewport", `${out}/arena-${theme}-${stage}${fx ? "-fx" : ""}${lod ? `-${lod}` : ""}.png`);
  console.log(shot, title);
}
