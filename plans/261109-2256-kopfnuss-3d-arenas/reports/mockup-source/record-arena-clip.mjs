// Records one arena stage with effects as frames (setT steps the shader clock and the avatars), then an MP4.
// usage: bun record-arena-clip.mjs <out.mp4> <theme> <stage> [seconds=3] [fps=12]
const [out, theme, stage, secs = "3", fps = "12"] = process.argv.slice(2);
const B = `${process.env.HOME}/.claude/skills/gstack/browse/dist/browse`;
const dir = `${out}.frames`;
const run = (...a) => (Bun.spawnSync([B, ...a], { cwd: "/home/andre/Workspace/spieltisch/wt-dreifelder-merge" }).stdout.toString().trim());
Bun.spawnSync(["mkdir", "-p", dir]);
run("viewport", "1920x1080");
run("goto", `http://127.0.0.1:8741/index.html?theme=${theme}&stage=${stage}&fx=1&n=${Date.now()}`);
for (let i = 0; i < 120 && !run("js", "document.title").includes("ready"); i++) await Bun.sleep(250);
const n = Number(secs) * Number(fps);
for (let i = 0; i < n; i++) {
  run("js", `setT(${(i / Number(fps)).toFixed(3)})`);
  run("screenshot", "--viewport", `${dir}/f${String(i).padStart(3, "0")}.png`);
}
const r = Bun.spawnSync(["ffmpeg", "-y", "-loglevel", "error", "-framerate", fps, "-i", `${dir}/f%03d.png`, "-vf", "scale=1280:-2",
  "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "22", out]);
console.log(out, r.exitCode === 0 ? "ok" : r.stderr.toString());
