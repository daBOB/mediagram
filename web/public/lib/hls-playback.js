/**
 * Playing what the browser refuses to decode.
 *
 * The server transcodes such a set to HLS on demand and answers with the URL
 * of a playlist. Safari plays a playlist from a plain `src`; Chrome and
 * Firefox need Media Source Extensions driven by hls.js, which is fetched
 * only on the first title that actually needs it — it is by far the largest
 * thing this page can load, and most sets never need it.
 */

/**
 * Whether this browser has to play the playlist itself.
 *
 * Deliberately not "does it claim to support HLS": Chromium answers `maybe`
 * to `canPlayType("application/vnd.apple.mpegurl")` on platforms where it
 * then fails with a format error, so a claim is not evidence. Media Source
 * Extensions are, and where they exist hls.js is the better player anyway.
 * Only a browser without them — iOS Safari — is left to do it natively.
 */
function needsNativeHls() {
  return typeof MediaSource === "undefined" && typeof ManagedMediaSource === "undefined";
}

/** @type {Promise<any> | null} */
let pending = null;

/** Loads hls.js once, however many titles ask for it. */
function loadHls() {
  if (pending === null) pending = import("/lib/hls.mjs").then((module) => module.default);
  return pending;
}

/**
 * Asks the server to transcode `setId` from `seekSeconds` and returns the
 * playlist URL. The server does not answer until a first segment exists, so a
 * player handed this URL has something to play.
 */
async function beginTranscode(setId, seekSeconds) {
  const url = `/api/sets/${encodeURIComponent(setId)}/transcode?seek=${Math.max(0, Math.floor(seekSeconds))}`;
  const response = await fetch(url);
  if (!response.ok) {
    // The server says why a conversion would not start; repeating its status
    // code instead would hide the one useful sentence.
    const said = await response.json().catch(() => null);
    throw new Error(said?.error ?? `the server answered ${response.status}`);
  }
  const { playlist } = await response.json();
  if (typeof playlist !== "string") throw new Error("the server sent no playlist");
  return playlist;
}

/**
 * Tells the server this transcode is finished with.
 *
 * Worth doing rather than leaving to the idle reaper: a transcode holds the
 * hardware encoder, and jumping elsewhere in a film would otherwise run two
 * of them for minutes. `keepalive` so it still goes out from a page that is
 * closing.
 */
function releaseTranscode(playlist) {
  const session = playlist.replace(/^\/hls\/([a-f0-9]{16})\/.*$/, "$1");
  if (session === playlist) return;
  void fetch(`/hls/${session}`, { method: "DELETE", keepalive: true }).catch(() => {
    /* The reaper will get it. */
  });
}

/**
 * Plays a set through a transcode, and returns a function that stops it.
 *
 * Detaching matters more than it looks: hls.js holds a MediaSource and a
 * fetch loop, and one left running behind a closed dialog keeps the server
 * encoding for a viewer who has gone.
 */
export async function playTranscoded(video, setId, seekSeconds = 0) {
  const playlist = await beginTranscode(setId, seekSeconds);

  if (needsNativeHls()) {
    video.src = playlist;
    return () => {
      video.removeAttribute("src");
      video.load();
      releaseTranscode(playlist);
    };
  }

  const Hls = await loadHls();
  if (!Hls.isSupported()) throw new Error("this browser cannot play a transcode");

  // `startPosition: 0` because the playlist has no end marker while ffmpeg is
  // still writing it, and hls.js reads a playlist without one as live: left
  // alone it opens a film several minutes in, wherever encoding had reached.
  const hls = new Hls({ enableWorker: true, startPosition: 0 });
  hls.loadSource(playlist);
  hls.attachMedia(video);
  return () => {
    hls.destroy();
    releaseTranscode(playlist);
  };
}
