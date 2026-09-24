/**
 * Playing what the browser refuses to decode.
 *
 * The server transcodes such a set to HLS on demand and answers with the URL
 * of a playlist. Safari plays a playlist from a plain `src`; Chrome and
 * Firefox need Media Source Extensions driven by hls.js, which is fetched
 * only on the first title that actually needs it — it is by far the largest
 * thing this page can load, and most sets never need it.
 */

import { decodesParam } from "../link.js";

/**
 * Only engines without Media Source Extensions use native HLS. Chromium's
 * canPlayType("application/vnd.apple.mpegurl") may claim support it lacks.
 */
function needsNativeHls() {
  return typeof MediaSource === "undefined" && typeof ManagedMediaSource === "undefined";
}

/** @type {Promise<any> | null} */
let pending = null;

/** Loads hls.js once, however many titles ask for it. */
function loadHls() {
  if (pending === null) {
    pending = import("/lib/hls.mjs")
      .then((module) => module.default)
      .catch((error) => {
        pending = null;
        throw error;
      });
  }
  return pending;
}

/**
 * Asks the server to transcode `setId` from `seekSeconds` and returns the
 * playlist URL. The server does not answer until a first segment exists, so a
 * player handed this URL has something to play.
 */
async function beginTranscode(setId, seekSeconds, maxrateBits, audioTrack) {
  const seek = `seek=${Math.max(0, Math.floor(seekSeconds))}`;
  // Sent only when the player has measured something. The server clamps it to
  // its own cap either way, so this is a request, not an instruction.
  const rate = maxrateBits ? `&maxrate=${Math.floor(maxrateBits)}` : "";
  // Left off for the first stream, so the common request is the short one and
  // a server that predates the chooser still answers it.
  const track = audioTrack ? `&audio=${Math.floor(audioTrack)}` : "";
  const url = `/api/sets/${encodeURIComponent(setId)}/transcode?${seek}${rate}${track}${decodesParam()}`;
  const response = await fetch(url);
  if (!response.ok) {
    // The server says why a conversion would not start; repeating its status
    // code instead would hide the one useful sentence.
    const said = await response.json().catch(() => null);
    throw new Error(said?.error ?? `the server answered ${response.status}`);
  }
  const { playlist, copied } = await response.json();
  if (typeof playlist !== "string") throw new Error("the server sent no playlist");
  // A server that predates the copy path says nothing, which reads as an
  // encode — which is what it is doing.
  return { playlist, copied: copied === true };
}

// Paused playback still owns its session; refresh inside the five-minute reap window.
const KEEPALIVE_MS = 60_000;

/** Keeps a session alive while the player is open. Returns a way to stop. */
function keepAlive(playlist) {
  const timer = setInterval(() => {
    // The playlist itself: fetching it is what marks a session as watched, so
    // this needs no endpoint of its own.
    void fetch(playlist, { cache: "no-store" }).catch(() => {});
  }, KEEPALIVE_MS);
  return () => clearInterval(timer);
}

/** Release the encoder promptly, including when the page itself is closing. */
function releaseTranscode(playlist) {
  const session = playlist.replace(/^\/hls\/([a-f0-9]{16})\/.*$/, "$1");
  if (session === playlist) return;
  void fetch(`/hls/${session}`, { method: "DELETE", keepalive: true }).catch(() => {
    /* The reaper will get it. */
  });
}

/**
 * Owns the server watcher from acquisition until release, including startup.
 * The request deliberately settles after cancellation: its reply contains the
 * session ID we must release. It may no longer attach media or update the UI.
 */
async function acquireSession(setId, options) {
  const signal = options.signal;
  signal?.throwIfAborted();
  const { playlist, copied } = await beginTranscode(
    setId,
    options.seekSeconds ?? 0,
    options.maxrateBits,
    options.audioTrack ?? 0,
  );
  let released = false;
  let stopKeepAlive = null;
  const session = {
    playlist,
    copied,
    detach: () => {},
    check() {
      signal?.throwIfAborted();
      if (released) throw new DOMException("The playback session ended", "AbortError");
    },
    keepAlive() {
      stopKeepAlive = keepAlive(playlist);
    },
    release() {
      if (released) return;
      released = true;
      signal?.removeEventListener("abort", session.release);
      stopKeepAlive?.();
      try {
        session.detach();
      } finally {
        releaseTranscode(playlist);
      }
    },
  };
  signal?.addEventListener("abort", session.release, { once: true });
  if (signal?.aborted) {
    session.release();
    signal.throwIfAborted();
  }
  return session;
}

/** Warms the next title; its eventual open joins this session before release. */
export async function warmTranscode(setId, options = {}) {
  const session = await acquireSession(setId, options);
  try {
    session.check();
    session.keepAlive();
    return session.release;
  } catch (error) {
    session.release();
    throw error;
  }
}

/**
 * Plays a conversion and returns an idempotent release. `signal` also owns
 * startup, before there is a release function to return. Rate, seek and audio
 * identify the requested conversion; onStarted reports whether video is copied.
 */
export async function playTranscoded(video, setId, options = {}) {
  const session = await acquireSession(setId, options);
  try {
    session.check();
    if (needsNativeHls()) {
      session.detach = () => {
        video.removeAttribute("src");
        video.load();
      };
      video.src = session.playlist;
    } else {
      const Hls = await loadHls();
      session.check();
      if (!Hls.isSupported()) throw new Error("this browser cannot play a transcode");
      // A growing playlist looks live. Start at its beginning, and keep a
      // minute buffered so a brief slow connection need not interrupt playback.
      const hls = new Hls({
        enableWorker: true,
        startPosition: 0,
        maxBufferLength: 60,
        maxMaxBufferLength: 120,
      });
      session.detach = () => hls.destroy();
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data.fatal) return;
        try {
          session.check();
        } catch {
          return;
        }
        session.release();
        options.onFatal?.(new Error(data.details ?? "the conversion stopped"));
      });
      hls.loadSource(session.playlist);
      session.check();
      hls.attachMedia(video);
    }
    session.check();
    options.onStarted?.({ copied: session.copied });
    session.check();
    session.keepAlive();
    return session.release;
  } catch (error) {
    session.release();
    throw error;
  }
}
