# Phase 3: Clip export server

Spec § The export. Lives beside the conversions in `web/src/transcode/` and reuses their input URL
(`${baseUrl}/api/sets/{setId}/stream`, see `ffmpeg.ts` `FfmpegRunner.start`), `pacedReads` probe and progress parsing.

### Task 4: ffmpeg arguments for a clip

**Files:**
- Create: `web/src/transcode/clip-args.ts`
- Test: `web/test/clip-args.test.ts`

**Interfaces:**
- Produces: `type ClipHdr = "SDR" | "HDR10" | "HLG" | "DV" | null` (the catalog's `hdr` column value, null = SDR); `interface ClipRequest { input: string; output: string; from: number; to: number; audioTrack: number; hdr: ClipHdr; pacedReads: boolean }`; `function clipArgs(request: ClipRequest): string[]`; `const CLIP_MAX_SECONDS = 180`.

- [ ] **Step 1: Write the failing tests:**
  - `seeks before the input and cuts the exact length` — `-ss 3600` appears before `-i`, `-t 30` after it, for from 3600 / to 3630.
  - `maps the first video and the chosen audio` — `-map 0:v:0`, `-map 0:a:2` for audioTrack 2; `-sn -dn`.
  - `encodes H.264 and stereo AAC into a fast-start MP4` — `-c:v libx264`, `-crf 18`, `-preset medium`, `-pix_fmt yuv420p`, `-c:a aac -b:a 160k -ac 2`, `-movflags +faststart`, output path last.
  - `fits within 1080p and never scales up` — `-vf` contains `scale=w='min(1920,iw)':h='min(1080,ih)':force_original_aspect_ratio=decrease` (or an equivalent the test pins) and `force_divisible_by=2`.
  - `tone maps HDR10 and HLG` — filter contains `zscale=t=linear`, `tonemap=tonemap=hable`, `zscale=t=bt709:m=bt709:p=bt709` before the scale.
  - `sends Dolby Vision through libplacebo` — filter contains `libplacebo=` with `colorspace=bt709:color_primaries=bt709:color_trc=bt709`, and no `tonemap=`.
  - `paces reads only when the probe allowed it` — `-readrate {READ_RATE} -readrate_initial_burst {READ_BURST_SECONDS}` (imported from `args.ts`, not copied) before `-i` iff `pacedReads`.
- [ ] **Step 2: Run** `cd web && bun test test/clip-args.test.ts` — FAIL.
- [ ] **Step 3: Implement** `clipArgs` (pure). Verify the HDR chains by hand once on this machine with a short generated HDR10 test clip before finishing.
- [ ] **Step 4: Run** the test file — PASS.
- [ ] **Step 5: Commit** `feat(web): ffmpeg arguments for an Instagram-ready clip`.

### Task 5: Clip jobs and their routes

**Files:**
- Create: `web/src/transcode/clip-jobs.ts` (job registry + temp files + expiry)
- Create: `web/src/transcode/clip-routes.ts` (route handlers)
- Modify: `web/src/routes.ts` (dispatch the new paths), `web/src/index.ts` (construct `ClipJobs` with the same `baseUrl`/`pacedReads` the `FfmpegRunner` gets)
- Test: `web/test/clip-jobs.test.ts`, `web/test/clip-routes.test.ts`

**Interfaces:**
- Consumes: `clipArgs`, `CLIP_MAX_SECONDS` (Task 4); `playableSet(db, setId)` for the set's `hdr` and title; `WatchState.has(profileId)`.
- Produces: `class ClipJobs { constructor(options: { baseUrl: string; pacedReads: boolean; directory: string; spawn?: (cmd: string[]) => ClipProcess; now?: () => number }); start(spec: ClipSpec): { id: string } | "busy"; status(id: string): ClipStatus | null; file(id: string): { path: string; name: string; profileId: string } | null; ready(profileId: string, setId: string): ClipStatus[]; sweep(): void }`.
- `interface ClipSpec { profileId: string; setId: string; title: string; from: number; to: number; audioTrack: number; hdr: ClipHdr }`; `interface ClipStatus { id: string; setId: string; from: number; to: number; state: "running" | "done" | "failed"; progress: number /* 0..1 */; error?: string; name: string }`.
- Command: `["nice", "-n", "10", "ffmpeg", ...clipArgs(...), "-progress", "pipe:1", "-nostats"]`; progress = `out_time_us` / (to − from).
- File name: `` `${safeTitle} – ${h}h${mm}m${ss}s.mp4` `` with `safeTitle` = title with `/\\:*?"<>|` and control chars removed, trimmed, cut to 80 chars, fallback `"clip"`; served with `Content-Disposition: attachment; filename*=UTF-8''…`.
- DV failure copy: `"This title's Dolby Vision colours can't be converted yet."`; other failure: `"The clip could not be made."`. Busy copy: `"Another clip is being made. Try again in a moment."`
- HTTP:
  - `POST /api/profiles/{p}/clips` body `{ setId, from, to, audio }` → `202 {"id"}`; 404 unknown profile/unplayable set; 400 when not finite, `from < 0`, `to - from < 1`, `to - from > 180`, `audio` not an integer ≥ 0; 409 + busy copy.
  - `GET /api/clips/{id}` → `ClipStatus` JSON or 404.
  - `GET /api/clips/{id}/file?profile={p}` → the MP4 (200, `video/mp4`) when done and `p` matches the job's profile; 403 on mismatch; 404 otherwise.
  - `GET /api/profiles/{p}/clips?setId={s}` → `ClipStatus[]` of done and running jobs for that profile and set.
- Expiry: `sweep()` deletes files and forgets jobs whose `done/failed` time is more than 3 600 000 ms ago; called on a 5-minute timer and at start. Temp dir under the player's existing data/temp location; removed jobs' files deleted at shutdown.

- [ ] **Step 1: Write the failing tests** (fake `spawn` that emits progress lines and exits 0/1):
  - `clip-jobs.test.ts`: `a job runs, reports progress, and ends done with its file`; `a second start while one runs is busy`; `a failed DV job says why`; `ready lists only that profile's jobs for that set`; `sweep forgets a job an hour after it ended and deletes its file`; `the file name strips unsafe characters` (`"AC/DC: Live?"` → `"ACDC Live – 1h02m00s.mp4"`).
  - `clip-routes.test.ts`: 202 happy path; each 400 case above; 404 unknown set; 409 busy; file 403 for another profile; file 404 while running.
- [ ] **Step 2: Run** `cd web && bun test test/clip-jobs.test.ts test/clip-routes.test.ts` — FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `cd web && bun test && bun run typecheck` — PASS.
- [ ] **Step 5: Commit** `feat(web): clip export jobs and routes`.
