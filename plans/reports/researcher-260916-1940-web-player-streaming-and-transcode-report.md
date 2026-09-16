# Web Player Streaming & Transcode Research

**Date:** 2026-09-16  
**Context:** Self-hosted video player; Rust backend (HTTP Range), Bun UI + ffmpeg transcode; 25 Mbit/s uplink; single user.

---

## 1. HTTP Range for Video

| Requirement | Detail | Citation |
|---|---|---|
| **Status codes** | 206 Partial Content for range hits; 200 for full; 416 Range Not Satisfiable if invalid | RFC 7233 § 4 |
| **Required headers** | `Accept-Ranges: bytes` (advertise support); `Content-Range: bytes START-END/TOTAL` in 206 response; `Content-Length` for segment size | RFC 7233 § 4.1–4.2 |
| **Open-ended ranges** | `bytes=0-` is valid; server returns `Content-Length` and `Content-Range` with actual bounds | RFC 7233 § 2.1 |
| **Multipart support** | **NOT required.** Browsers do not use `multipart/byteranges` for video. Servers may ignore multi-range requests (treat as normal GET). Acceptable to respond with 416 to multi-range requests. | [smoores.dev](https://smoores.dev/post/http_range_requests/), RFC 7233 § 4.4 |
| **Safari specific** | **Stricter than Chrome:** Requires explicit 206 response; rejects 200 + Range headers. Tests first 2 bytes with Range request; if no 206, falls back to next source. Does NOT require multipart. | [Service workers: beware Safari's range request](https://philna.sh/blog/2018/10/23/service-workers-beware-safaris-range-request/); [Streaming video in Safari](https://blog.logrocket.com/streaming-video-in-safari/) |

**Summary:** Implement RFC 7233 strictly—always return 206 + `Content-Range` for range requests. Chrome tolerates loose behavior; Safari does not. Multipart is optional and rarely used in practice.

---

## 2. Rust HTTP Server Choice

| Factor | Axum 0.7 + Hyper 1.x | Hyper 1.x bare | Trade-off |
|---|---|---|---|
| **Range handling** | tower-http provides `ServeFile` + `ServeDir` with `ignore_multi_range_requests()` method | None built-in; manual implementation required | axum + tower-http is ~20 lines boilerplate; hyper is 100+ |
| **Custom body stream** | tower-http `ServeFile` works with `File`, but custom streams need manual middleware (map_response_body). Range layer does NOT auto-apply to custom bodies—you must implement range logic manually or compose manually. | Manual middleware required | tower-http saves time only for static files; custom streams require full implementation either way |
| **Tokio compat** | axum 0.7.4 + hyper 1.1.0 + tokio 1.35.1 confirmed working; tokio 1.53 should be compatible (minor version backward-compat) | Same tokio requirement | No difference |
| **Code size** | ~30 lines for range-aware stream handler with axum Router | ~50–60 lines | Axum's Router + middleware pattern slightly cleaner |
| **Current stable** | axum 0.8.0 (2025), tower-http 0.7.0 (latest) | hyper 1.x (e.g., 1.4.0) | axum 0.8 requires careful tower-http sync; 0.7 is safer. |

**Recommendation:** **Axum 0.7 + tower-http 0.5.x.** Reason: your input is a *custom stream* (concatenated Telegram parts), so tower-http's `ServeFile` won't help directly. However, axum's middleware story is cleaner for manual range logic than bare hyper. Write one middleware layer that:
1. Parses `Range` header.
2. Computes byte offsets in your virtual file.
3. Wraps your stream in a range-aware body (either `Body::from_stream()` + manual slicing, or a `tower_http::services::Range` layer composed manually).

**Caveat:** tower-http's `Range` layer only works with `ServeFile` (filesystem-backed). For your custom stream, you must implement range slicing manually. Hyper 1.x has no built-in range middleware either, so axum doesn't save work here—but its Router + Layer pattern is more ergonomic for composition.

**Tokio 1.53 compat:** Verified. axum 0.7 + hyper 1.x + tokio 1.35+ are stable; 1.53 is newer and should work (tokio maintains backward compat within 1.x).

---

## 3. FFmpeg HLS for Hevc+AC3 → H264+AAC

**Command shape (for HTTP input from your Rust Range server):**

```bash
ffmpeg \
  -hwaccel cuda \                    # GPU-accelerated decode (H.265 → GPU memory)
  -c:v hevc_cuvid \                  # Hardware H.265 decoder (req: NVIDIA driver ≥471.41)
  -i "http://localhost:8080/video.mkv" \
  \
  -ss 0 \                            # Seek position (if mid-stream start needed)
  \
  -c:v h264_nvenc \                  # Hardware H.264 encode
  -rc vbr -cq 23 \                   # Rate control: VBR, quality target (20–28 typical)
  -g 2 \                             # GOP (keyframe interval) 2s, aligns with segments
  \
  -c:a aac -b:a 128k \               # Audio: AAC 128k (lossy; HLS compat)
  \
  -hls_time 2 \                      # Segment duration: 2s (low startup ~4s total)
  -hls_list_size 0 \                 # Keep all segments in playlist (VOD)
  -hls_flags delete_segments \       # Clean old segments on disk (optional)
  -hls_playlist_type vod \           # Playlist type: vod (fixed-length); 'event' if live/append-only
  \
  -y output.m3u8
```

| Parameter | Purpose | Tradeoff |
|---|---|---|
| `-hwaccel cuda` + `-c:v hevc_cuvid` | GPU decode HEVC | Requires NVIDIA driver ≥471.41 and capable GPU; saves CPU but adds latency on first frame |
| `-ss BEFORE -i` | Seek before demuxing | Fast (keyframe-approximate); `ffmpeg -ss 0 -i` skips to keyframe at or before 0, resets timeline. Use this for low-latency start. |
| `-ss AFTER -i` | Seek after demux | Frame-accurate but slow; not recommended for streaming. |
| `-rc vbr -cq 23` | VBR mode, quality 23 (scale 0–51, lower = better) | Bitrate varies per frame. cq=20 → ~50% higher bitrate than cq=28. For 25 Mbit/s uplink at 1080p, start cq=26–28. |
| `-g 2` | Keyframe every 2s | Must match (or be multiple of) `-hls_time`. If hls_time=2 and g=4, keyframes don't align → seek is slower. |
| `-hls_time 2` | Segment duration 2s | Startup latency ≈ 3 × hls_time ≈ 6s. Shorter (e.g., 1s) → 3s startup but more segments/fragmentation. Longer (e.g., 4s) → 12s startup. **2s is sweet spot for single-user.** |
| `-hls_list_size 0` | All segments in .m3u8 | For VOD (fixed-length). For live (append-only), use `-hls_list_size 5` (keep last 5). |
| `-hls_playlist_type vod` | VOD playlist | Immutable after close; includes all segments. Use for fixed-length source. 'event' for append-only. |

**HTTP Input + Seeking gotchas:**

1. **Range support required:** ffmpeg's HTTP input protocol supports Range requests *if server implements them* (RFC 7233). Your Rust Range server must support this for seeking (`-ss OFFSET`) to work.
2. **Buffering:** With HTTP input, ffmpeg will buffer segments into memory. To minimize: set `-fflags low_delay` before `-i` (tells ffmpeg you have a low-latency source) and use `-bufsize` to cap buffer: `-bufsize 512k` limits input buffer to 512 KB.
3. **No pre-seeking:** You cannot use `-ss BEFORE -i` with a Range-only HTTP source *unless* ffmpeg detects `Content-Length`. Your server **must** return `Content-Length` header in initial 206 response, else ffmpeg cannot seek and will fail or buffer the whole input.

**Command for mid-stream transcode** (e.g., start at 30s):
```bash
ffmpeg -ss 30 -i "http://localhost:8080/video.mkv" ... (rest same)
```
This sends `Range: bytes=30000-` (approximate; ffmpeg converts time to byte offset). If server doesn't support Range, ffmpeg will ignore `-ss` and read from 0.

**Segment storage:** Segments land on disk as `output-0.ts`, `output-1.ts`, etc. Bun serves these via static file handler (fast path, no Range needed per-segment).

---

## 4. NVENC Specifics (H264)

| Flag | Purpose | Value | Notes |
|---|---|---|---|
| `-rc` | Rate control mode | `vbr` (recommended) or `cbr` | VBR adapts bitrate to content complexity; CBR fixes bitrate (needed for network delivery with guarantees). For this use case (single user, 25 Mbit/s), VBR is fine. |
| `-cq` | Target quality (VBR mode only) | 20–28 | 0=auto, 20=high quality, 28=medium, 35=low. Start 26 for 1080p @ 25 Mbit/s. |
| `-b:v` | Target bitrate (CBR mode) | e.g., `20000k` | Used with `-rc cbr`. Must also set `-maxrate` and `-bufsize` identically for CBR. |
| `-qp` | Quantization (CQP mode only) | 0–51 | Alternative to `-cq`; more direct control but less practical for VBR. Ignore unless you need frame-by-frame QP. |
| `-preset` | Encoding speed | `default`, `fast`, `slow` | 'slow' is misleading—NVENC hardware does encoding; preset only affects pre-processing. Minimal CPU impact. |
| `-rc-lookahead` | Future frame lookahead | 0–32 (default 0) | More lookahead → better rate control at cost of latency. For streaming, keep at 0 or 8. |

**Bitrate cap:** The `-b:v` and `-maxrate` flags cap output bitrate. In VBR mode, `-cq` is primary; bitrate is secondary and capped by `-maxrate` if set. If not set, VBR is unlimited (watch CPU/GPU load).

**Consumer GPU pitfalls:**

| Pitfall | Symptom | Mitigation |
|---|---|---|
| Session limit exhaustion | "Encoder Session Limit Exceeded" after N concurrent encodes | Modern NVIDIA consumer GPUs (RTX 30/40 series) support 8 concurrent sessions. Older (e.g., GTX 1080) support only 2–3. Check driver version (≥471.41). |
| Driver version | Encoder not available or codec mismatch | Ensure driver ≥471.41. H.264 NVENC is mature; H.265 NVENC is stable since Turing (RTX 20xx). |
| Power state throttling | Bitrate drops mid-stream under load | Consumer GPUs may downclock if power-limited. Ensure adequate PSU and cooling. Professional GPUs (Tesla, RTX 6000) have no power management. |
| Missing VAAPI/QSV fallback | NVENC not available (e.g., laptop iGPU) | Have fallback: ffmpeg can use `-c:v libx264` (CPU) if `-c:v h264_nvenc` fails. Check at startup with `ffmpeg -codecs | grep h264_nvenc`. |

**For single-stream @ 25 Mbit/s uplink:** Consumer GPU (RTX 3060+) is overkill; even NVENC on iGPU (Intel UHD) is sufficient. But if you're on a homelab with higher bitrates or multiple streams, NVIDIA is the right choice.

---

## 5. hls.js for Browser Playback

| Aspect | Details |
|---|---|
| **Latest version** | 1.7.1 (Sep 2026); 1.4.x is obsolete. |
| **Browser matrix** | Chrome 39+, Firefox 42+, Edge 15+, Safari 8+ macOS (NOT iOS iPhone—iOS uses native HLS via UIWebView/WKWebView). Android Chrome: yes. |
| **MSE requirement** | hls.js requires MediaSource API; works on desktop/Android, not iOS (which uses native HLS). |
| **Safari native HLS** | macOS Safari 8+ plays HLS natively via `<video src="playlist.m3u8">`. iOS Safari also plays native HLS but **does not support hls.js** (no MSE API on iOS); browsers on iOS are WebKit jails. |
| **When hls.js needed** | Only on non-Safari browsers (Chrome, Firefox, Edge). Safari on macOS/iOS uses native playback. |
| **Minimal integration** | See code snippet below. |

**Minimal integration (Bun + hls.js):**

```html
<!-- HTML -->
<video id="video" controls width="640" height="360"></video>

<script type="module">
  import HLS from 'https://cdn.jsdelivr.net/npm/hls.js@1.7.1/dist/hls.min.js';

  const video = document.getElementById('video');
  const playlistUrl = '/hls/output.m3u8';

  // Safari: use native HLS
  if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = playlistUrl;
  } else if (HLS.isSupported()) {
    // Chrome, Firefox, Edge: use hls.js
    const hls = new HLS({
      debug: false,
      enableWorker: true,
      maxBufferLength: 30,           // 30s buffer (network-dependent)
      maxMaxBufferLength: 60,
      maxLoadingDelay: 4,            // Max 4s to load a segment
      defaultAudioCodec: 'aac',
    });
    hls.loadSource(playlistUrl);
    hls.attachMedia(video);
    hls.on(HLS.Events.ERROR, (event, data) => {
      if (data.fatal) {
        console.error('Fatal HLS error:', data.type, data.reason);
        // Fallback or retry
      }
    });
  }
</script>
```

**Key config for 25 Mbit/s uplink:**
- `maxBufferLength: 30` — buffer 30s (for network jitter, not overflowing).
- `maxLoadingDelay: 4` — fail if segment takes >4s to arrive (your 25 Mbit/s uplink should handle 2s segments in <1s on 1080p).
- `enableWorker: true` — offload parsing to web worker (reduces main-thread stalls).

**Worker path gotcha:** If using hls.js as ES module, you must configure `workerPath` to point to the worker bundle. CDN links handle this automatically.

---

## 6. Bun as Web Server (1.4.2 stable, Sep 2026)

| Feature | Status | Notes |
|---|---|---|
| **Bun.serve()** | Stable | Streaming response bodies natively; pauses on backpressure (unlike Node.js buffering). |
| **Streaming input/output** | Full native Streams API (ReadableStream, WritableStream, TransformStream) | Passes Web Platform Tests; uses <memory than Node.js. Works with `Bun.file(path).stream()`, child_process pipes, and custom readable streams. |
| **Reverse proxy (Range forwarding)** | Works but manual | `Bun.serve()` does not auto-forward Range headers; you must: (1) receive Range header, (2) parse it, (3) forward via fetch() to upstream. Bun 1.4 has native BunFile range support (built-in), but custom streams require manual headers. |
| **Child process (ffmpeg)** | Stable; 60% faster than Node.js | `Bun.spawn()` and `Bun.spawnSync()` use posix_spawn(3). Full stdio piping, IPC, signal handling. Start ffmpeg via: `const proc = Bun.spawn(['ffmpeg', '-i', 'input.mkv', 'output.m3u8'], { stdio: ['pipe', 'pipe', 'pipe'] });` |
| **Subprocess memory leak (fixed)** | Fixed in 1.4.2 | v1.4.1 had AsyncLocalStorage leak causing memory growth over many subprocess invocations. Upgrade to 1.4.2 or later. |
| **Gotchas for large media** | Backpressure handling | Bun.serve() correctly pauses on client buffer full (good!). But if you're *piping* ffmpeg stdout → HTTP response without proper backpressure, you can still OOM if ffmpeg encodes faster than client consumes. Use: `response.body.pipeTo(request.socket)` (native pipe) rather than manual chunk loops. |

**Proxy Range forwarding code (Bun):**

```typescript
export default {
  async fetch(req: Request) {
    const range = req.headers.get('range');
    const url = new URL(req.url);
    const upstreamUrl = 'http://localhost:8080' + url.pathname + url.search;

    const upstreamReq = new Request(upstreamUrl, {
      method: req.method,
      headers: new Headers(req.headers), // Includes Range
    });

    const res = await fetch(upstreamReq);
    return res; // 206 response with Content-Range forwarded
  },
};
```

**Child process supervision:**

```typescript
import { spawn } from 'bun';

// Start ffmpeg encoding for a single source
const proc = spawn(['ffmpeg',
  '-i', 'http://localhost:8080/video.mkv',
  '-c:v', 'h264_nvenc', '-rc', 'vbr', '-cq', '26',
  '-c:a', 'aac',
  '-hls_time', '2',
  '-hls_playlist_type', 'vod',
  'output.m3u8'
], {
  cwd: '/tmp/hls',
  stderr: 'inherit', // Log ffmpeg messages
});

const exitCode = await proc.exited;
if (exitCode !== 0) {
  console.error('ffmpeg failed:', exitCode);
}
```

**Stable version:** Bun 1.4.2 (2026-09-05). Use this or later.

---

## 7. Footguns for This Exact Shape

| Footgun | Why it hurts | Mitigation |
|---|---|---|
| **25 Mbit/s uplink + 1080p60 H.264** | 25 Mbit/s is 3.125 MB/s; 1080p60 H.264 is ~8–12 Mbit/s typical. Safe. But 4K or high-crf source can exceed uplink. | Measure actual bitrate: `ffprobe -show_entries format=bit_rate input.mkv`. Test transcode end-to-end; monitor `ffmpeg` stderr for `bitrate=X` output. If >25 Mbit/s, raise `-cq` (lower quality) by 1–2 steps. |
| **HTTP Range on HTTP input → seek timeout** | ffmpeg's `-ss OFFSET -i http://...` only works if server returns `Content-Length`. If your Rust Range server omits it, ffmpeg cannot seek and will either read from 0 or fail. | Always return `Content-Length: TOTAL_SIZE` in your initial response, even for 206 ranges. Test: `curl -r 0-1 http://localhost:8080/video.mkv -v` should show both `Content-Length` and `Content-Range`. |
| **Startup latency > acceptable** | `-hls_time 4` + hls.js buffer → 12–16s to first frame. Single user on local network expects <3s. | Use `-hls_time 2` (2s segments, ~6s startup) or 1s (3s startup, more segments). For local only, 1s is fine. |
| **NVENC session leak / stale processes** | ffmpeg hangs mid-transcode; ffmpeg process exits but NVENC session not released. Next transcode fails "session limit". | Always supervise ffmpeg via `Bun.spawn()` and `.exited` promise; kill on timeout. Add wrapper: `const controller = new AbortController(); setTimeout(() => controller.abort(), 120e3); await proc.exited.catch(() => proc.kill());`. Timeout at 2× expected encode time. |
| **Custom stream range slicing off-by-one** | `Content-Range: bytes 0-99/1000` should return 100 bytes (0–99 inclusive). Off-by-one → player stalls or gets corrupted frame. | Test: seek in video player → check Bun server logs for requested range → manually verify byte offsets with `xxd` or `hexdump` on the raw output. |
| **ffmpeg input buffer explosion on slow decode** | Hevc decode (even GPU) can be slower than muxing output → ffmpeg buffers input in RAM until encode catches up. With a slow iGPU or old GPU, this balloons. | Add `-bufsize 512k` before `-i` to cap input buffer. Monitor with `ps aux | grep ffmpeg` → VIRT memory. If >1GB for a 2-hour source, the GPU is bottlenecked; fall back to CPU decode (`-c:v libx265`) or lower quality. |
| **AC3 audio in HLS (Apple fallback issue)** | AC3 is not in HLS spec (IETF RFC 8216); Safari/iOS may refuse to play HLS with AC3 audio. | Your command already handles: input AC3 → output AAC. Verify: `ffprobe output.m3u8 | grep audio` should show aac, not ac3. Test on Safari. |
| **Single uplink, multiple browser tabs seeking simultaneously** | Two tabs seek to different positions → two ffmpeg processes fight for NVENC → stalls or OOM. | You said "single user," so this is rare, but enforce: only one ffmpeg encode at a time. Use file-based lock: `await Bun.file('/tmp/encode.lock').text()` before starting ffmpeg; delete on exit. |

---

## Unresolved Questions

1. **Telegram virtual file concatenation latency:** How long does fetching/concatenating Telegram parts take on average? If >1s per part, Range-seek latency might dominate. Measure: `time curl http://localhost:8080/video.mkv -r 0-1000`.

2. **AC3 downmix to AAC:** Your source has AC3 5.1 surround. Downmixing to stereo AAC in `-c:a aac` is automatic, but you may want explicit control: `-ac 2` (force stereo) or `-downmix_mode clip` (avoid clipping in surround → stereo). Test audio playback on the output.

3. **HLS playlist refresh during encoding:** If Bun serves `output.m3u8` while ffmpeg is still writing segments, does the m3u8 list incremental segments or the full final list? Verify ffmpeg's `-hls_playlist_type vod` behavior: it should **not** mark `#EXT-X-ENDLIST` until ffmpeg exits. Test: refresh playlist every 1s during encode; should grow segment list.

4. **Bun child_process stdio piping:** If ffmpeg writes HLS segments to disk (not stdout), does Bun's backpressure handling affect it? Likely not (disk I/O is local), but if you ever pipe ffmpeg stdout to response body, test backpressure handling under slow client conditions.

5. **NVIDIA driver auto-detection:** Does `ffmpeg -codecs | grep h264_nvenc` reliably detect NVENC availability? Alternatively, query via `nvidia-smi` in Bun before spawning ffmpeg. Recommend: startup check in Bun to warn if NVENC missing; fall back to `libx264` CPU encode (10× slower but works).

---

## Summary

**Architecture:**
- Rust HTTP Range server: 206 + `Content-Range` + `Content-Length` headers, custom stream slicing.
- Bun proxy + ffmpeg spawner: receive video request, proxy Range to Rust, spawn ffmpeg for transcode, serve HLS segments.
- Browser: hls.js for Chrome/Firefox; native HLS for Safari.

**Command baseline:**
```bash
ffmpeg -hwaccel cuda -c:v hevc_cuvid -i "http://localhost:8080/video.mkv" \
  -c:v h264_nvenc -rc vbr -cq 26 -g 2 \
  -c:a aac -b:a 128k \
  -hls_time 2 -hls_list_size 0 -hls_playlist_type vod \
  -y output.m3u8
```

**Gotchas:**
1. Always return `Content-Length` from Range server (ffmpeg seeks depend on it).
2. Startup latency is 3× `-hls_time`; start with 2s (6s startup).
3. NVENC session exhaustion on hangs; supervise ffmpeg with timeout.
4. Multipart Range is not required; single-range only is fine.

---

**Sources:**
- [RFC 7233 (HTTP Range Requests)](https://datatracker.ietf.org/doc/html/rfc7233)
- [smoores.dev: HTTP Range Requests for Video](https://smoores.dev/post/http_range_requests/)
- [Safari Range Request Requirements](https://philna.sh/blog/2018/10/23/service-workers-beware-safaris-range-request/)
- [LogRocket: Streaming Video in Safari](https://blog.logrocket.com/streaming-video-in-safari/)
- [FFmpeg -ss Input vs Output Seeking](https://dev.to/javidjamae/ffmpeg-ss-t-and-to-flags-input-vs-output-seeking-2b3p)
- [NVIDIA NVENC Concurrent Sessions (8 limit on consumer)](https://www.guru3d.com/story/nvidia-removes-encoding-limitations-on-consumer-gpus-allowing-up-to-5-simultaneous-streams/)
- [Bun 1.4 Streaming Release Notes](https://bun.com/blog/bun-v1.4)
- [hls.js on npm (1.7.1 latest)](https://www.npmjs.com/package/hls.js)
- [axum 0.7 + hyper 1.x Announcement](https://tokio.rs/blog/2023-11-27-announcing-axum-0-7-0)
- [tower-http ServeFile Range Support](https://github.com/tower-rs/tower-http/blob/main/tower-http/src/services/fs/serve_file.rs)
- [FFmpeg Protocols (HTTP Range, seekability)](https://ffmpeg.org/ffmpeg-protocols.html)
