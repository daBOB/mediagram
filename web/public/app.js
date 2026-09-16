/**
 * The catalog page.
 *
 * Talks only to this server's own API: it never learns where a set's bytes
 * live, which is the point of keeping the channel and message ids on the
 * server side.
 */

const main = document.getElementById("main");
const count = document.getElementById("count");
const dialog = document.getElementById("player");
const video = document.getElementById("video");
const now = document.getElementById("now");

/** Same rules as `src/playable.ts`, which is the tested copy. */
const CONTAINERS = new Set(["mp4", "m4v", "webm"]);
const VIDEO = new Set(["h264", "avc", "avc1", "vp8", "vp9", "av1"]);
const AUDIO = new Set(["aac", "mp4a", "opus", "vorbis", "mp3"]);
const PRETTY = { hevc: "HEVC", h265: "HEVC" };

function decide(set) {
  const container = (set.container ?? "").toLowerCase();
  const v = (set.vcodec ?? "").toLowerCase();
  const a = (set.acodec ?? "").toLowerCase();
  const reasons = [];
  if (!CONTAINERS.has(container)) {
    reasons.push(container === "mkv" ? "Matroska" : container || "unknown container");
  }
  if (!VIDEO.has(v)) reasons.push(PRETTY[v] ?? v ?? "unknown video");
  if (!AUDIO.has(a)) reasons.push(a || "unknown audio");
  return reasons.length ? { direct: false, reason: reasons.join(", ") } : { direct: true };
}

function humanSize(bytes) {
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value < 10 && unit > 0 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`;
}

function humanDuration(seconds) {
  if (!seconds) return "";
  const h = Math.floor(seconds / 3600);
  const m = Math.round((seconds % 3600) / 60);
  return h ? `${h}h ${m}m` : `${m}m`;
}

/** Course lessons group under their course; episodes under their show. */
function groupOf(set) {
  if (set.show) return set.show;
  if (set.kind === "movie") return "Films";
  return "Other";
}

function subtitleOf(set) {
  const bits = [];
  if (set.season != null && set.episode) bits.push(`S${set.season}E${set.episode}`);
  else if (set.episode) bits.push(`Lesson ${set.episode}`);
  if (set.chap) bits.push(set.chap);
  if (set.year) bits.push(String(set.year));
  bits.push([set.container, set.vcodec, set.acodec].filter(Boolean).join(" · "));
  return bits.filter(Boolean).join(" — ");
}

function play(set) {
  video.src = `/api/sets/${encodeURIComponent(set.setId)}/stream`;
  now.textContent = set.title ?? set.setId;
  dialog.showModal();
  video.play().catch(() => {
    /* The viewer can press play; autoplay is often blocked. */
  });
}

document.getElementById("close").addEventListener("click", () => dialog.close());
dialog.addEventListener("close", () => {
  // Drop the connection so the server stops fetching from Telegram.
  video.pause();
  video.removeAttribute("src");
  video.load();
});

function render(sets) {
  count.textContent = `${sets.length} playable`;
  if (sets.length === 0) {
    main.innerHTML = '<p class="empty">Nothing playable in the index yet.</p>';
    return;
  }

  const groups = new Map();
  for (const set of sets) {
    const key = groupOf(set);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(set);
  }

  main.textContent = "";
  for (const [name, items] of groups) {
    const section = document.createElement("section");
    section.className = "group";
    const heading = document.createElement("h2");
    heading.textContent = `${name} · ${items.length}`;
    section.append(heading);

    for (const set of items) {
      const decision = decide(set);
      const button = document.createElement("button");
      button.className = "item";

      const title = document.createElement("div");
      title.className = "title";
      const strong = document.createElement("b");
      strong.textContent = set.title ?? set.setId;
      const sub = document.createElement("span");
      sub.textContent = subtitleOf(set);
      title.append(strong, sub);

      const meta = document.createElement("div");
      meta.className = "meta";
      meta.textContent = [humanDuration(set.duration), humanSize(set.total)]
        .filter(Boolean)
        .join(" · ");

      button.append(title);
      if (!decision.direct) {
        const badge = document.createElement("span");
        badge.className = "badge";
        badge.textContent = `needs transcode: ${decision.reason}`;
        button.append(badge);
      }
      button.append(meta);
      button.addEventListener("click", () => play(set));
      section.append(button);
    }
    main.append(section);
  }
}

try {
  const response = await fetch("/api/sets");
  if (!response.ok) throw new Error(`the catalog answered ${response.status}`);
  render(await response.json());
} catch (error) {
  main.innerHTML = "";
  const p = document.createElement("p");
  p.className = "error";
  p.textContent = `Could not load the catalog: ${error.message}`;
  main.append(p);
}
