# Ryzen 3 4300U (Renoir) + Radeon iGPU: datasheet and optimization techniques

Date: 2026-11-21. Scope: what manufacturers and upstream projects say (sections A-D), plus one section of measurements taken on the machine itself and the actions ranked from both.

Tags: **[primary]** owner doc, spec, kernel/Mesa/ffmpeg/three.js source, bug tracker. **[secondary]** press, Wikipedia, spec aggregators, OEM pages, forums, search-snippet summaries. **[unverified]** no source traced, or sources conflict.

Access notes: amd.com product page timed out (WebFetch). gitlab.freedesktop.org (Mesa MRs) and several other pages returned Anubis "access denied". Notebookcheck, TechPowerUp, and Phoronix were read via search snippets only. Anything marked snippet-only is weaker than a fetched page.

---

## Findings that change what we should do

1. Renoir has no AV1 hardware decode or encode; AMD's own table lists AV1 as N/A for VCN 2.0 (Cezanne row, Renoir mapping not on the page). So AV1 content on this box is CPU decode, period. [primary, mapping unverified] https://github.com/GPUOpen-LibrariesAndSDKs/AMF/wiki/GPU-and-APU-HW-Features-and-Support
2. The h264_vaapi encoder path is 8-bit only: AMD's table gives AVC encode 8b only (4K), HEVC encode 8/10b (4K). Any 10-bit source going to the web player's H.264 encode must be converted to 8-bit first. [primary, column alignment inferred] https://github.com/GPUOpen-LibrariesAndSDKs/AMF/wiki/GPU-and-APU-HW-Features-and-Support
3. This is a 4-core, 4-thread part (no SMT), so software decode and game worker threads have 4 hardware threads total. [secondary] https://www.cpu-monkey.com/en/cpu-amd_ryzen_3_4300u
4. Renoir's iGPU is Vega (GFX9). Mesa's 2018 statement says GFX9 handles ETC2 in hardware; ASTC was added in software for gallium drivers without hardware support. [secondary] https://lists.freedesktop.org/archives/mesa-dev/2018-January/181065.html and https://lists.freedesktop.org/archives/mesa-dev/2018-July/200867.html
5. We ship three.js 0.170. Its KTX2Loader has no rule against emulated formats and sorts both inputs by one list: ASTC (UASTC input only), BC7, ETC2, ETC1, BC1/BC3. So ETC1S textures get BC7 here, and UASTC textures get ASTC whenever the WebView exposes it. Later three.js disables ASTC/ETC1/ETC2 on Linux, but skips Android user agents, so an upgrade does not cover the WayDroid WebView. Its comment states Mesa on AMD exposes ETC1, ETC2 and ASTC without hardware support and decompresses on the main thread. BC7 is 8 bits per texel, a quarter of RGBA8. [primary] https://raw.githubusercontent.com/mrdoob/three.js/r170/examples/jsm/loaders/KTX2Loader.js and https://raw.githubusercontent.com/mrdoob/three.js/dev/examples/jsm/loaders/KTX2Loader.js
6. The mediump gain from the Mali box probably does not carry over: radeonsi's FP16 mediump path is opt-in (a `radeonsi_fp16` option gating an LLVM-era merge request), so mediump is likely treated as highp by default. Default value not confirmed. [secondary, MR page blocked] https://gitlab.freedesktop.org/mesa/mesa/-/merge_requests/9051
7. Mesa's GLSL precision lowering only happens for drivers that opt in; otherwise mediump qualifiers are ignored. [secondary] https://fuchsia.googlesource.com/third_party/mesa/+/main/src/compiler/glsl/lower_precision.cpp
8. The kernel amd-pstate page names no Renoir support, requires CPPC via ACPI `_CPC`, and falls back to acpi-cpufreq when that fails. Check the active scaling driver on the box before any tuning. [primary] https://docs.kernel.org/admin-guide/pm/amd-pstate.html
9. Dual-channel DDR4-3200 gives 51.2 GB/s peak shared by CPU and iGPU; single-channel halves it. Full-screen passes at 4K RGBA8 cost about 66 MB of read+write each (computed), so bandwidth, not shader ALU, is the first limit. [secondary, computed] https://www.slideshare.net/slideshow/hot-chips-amd-next-gen-7nm-ryzen-4000-apu-238080884/238080884
10. Chromium on Linux runs ANGLE over OpenGL in recent builds; WebGPU on Linux runs on Vulkan with rollout starting from Intel and AMD listed as tentative. WebGL2 is the realistic target here. [primary for Chrome blog, secondary for forum] https://developer.chrome.com/blog/new-in-webgpu-144.md.txt and https://bbs.archlinux.org/viewtopic.php?id=311691
11. WayDroid's own docs only document a software fallback (swiftshader and default gralloc), and say QEMU is the only VM with working 3D. No primary doc found for HWC, overlay, HDR or refresh-rate behaviour; treat the Android path's hardware presentation as unknown until measured. [primary, gap] https://docs.waydro.id/faq/get-waydroid-to-work-through-a-vm
12. Upscaling is the cheapest large win: FSR 1 is a published spatial shader upscaler with per-pass costs of 0.4 to 1.0 ms at 4K on the GPU tiers listed (not an iGPU); its cost on a 5-CU part is untested. [primary] https://gpuopen.com/fidelityfx-superresolution/

---

## Measured on funstation (2026-11-21, read-only over SSH)

Nothing was launched or played; an evening's accumulated state, not a controlled run.

| Item | Measured |
|---|---|
| CPU | 4 cores / 4 threads, 3.8 GHz max, L2 4x512 KiB, L3 4 MiB, AVX2 FMA AES SHA, no AVX-512 |
| Frequency scaling | acpi-cpufreq + schedutil, steps 1.4 / 1.7 / 2.7 GHz + boost. Kernel log: `amd_pstate: the _CPC object is not present in SBIOS or ACPI disabled`. CPU flag `cppc` present |
| Memory | 2x8 GiB LPDDR4 on channels A and B, rated 4266, running 3733 MT/s (about 59.7 GB/s peak, computed) |
| GPU memory | carve-out 1 GiB with 955 MiB used; GTT 7.3 GiB with 1.4 GiB used |
| GPU clocks, power | 200 / 400 / 1400 MHz; idle SoC 6-8 W, 41-50 C |
| Drivers | host Mesa 26.2.6: radeonsi GLES 3.2, RADV Vulkan 1.4.354. Android container: Mesa 26.0.3 radeonsi GLES 3.2, gralloc minigbm_gbm_mesa |
| VA-API decode | MPEG-2, VC-1, H.264 CB/Main/High, HEVC Main/Main10, VP9 profile 0/2, JPEG. No AV1 |
| VA-API encode | H.264 CB/Main/High, HEVC Main/Main10. VideoProc entry point present |
| Screen | HP OMEN 27k. 4K modes: 120, 119.88, 100, 60, 59.94, 50, 30 Hz. EDID VRR 48-144 Hz, KWin VRR "Never" |
| Link at 4K120 | 8 bpc, BT709_YCC: YCbCr 4:2:0 inside the 600 MHz TMDS limit. EDID lists FRL rates the chip does not use |
| HDR | EDID has no HDR static metadata or colorimetry block; KWin: "HDR incapable", "Wide Color Gamut incapable" |
| Android display | one mode, 3840x2160@120, density 360 (device pixel ratio 2.25, 1707x960 CSS px) |
| Android codec | c2.ffmpeg, hwaccel drm on, output pixel format RGBX_8888 (8-bit) |
| Media app frames | 27,752 frames: median 46 ms, p90 53, p99 61; 10,528 "slow draw commands", 4,969 slow UI thread |
| Spieltisch frames | 1,404 frames: median 22 ms, p90 36, p99 48 |
| ABI | media app x86_64; Spieltisch arm64-v8a through libndk_translation; WebView 146.0.7680.153 |
| Host Chromium 156, headless, ANGLE on RADV | compressed formats BPTC, RGTC, S3TC only; MAX_SAMPLES 8; mediump reported as 10-bit (reported format, not a speed claim). GLES and GL backends gave no context headless |

In our code: the table stage renders at min(devicePixelRatio, 2) with MSAA (`tv/table/stage-host.ts:113`), about 3413x1920 here; the ceremony starts there and steps down to 1.25 when slower than ~32 fps (`tv/ceremony/ceremony-stage.ts:132`); Kopfnuss pins 1 (`tv/kopfnuss/arena-engine.ts:40`). Avatar textures are UASTC (ceremony colour 2048, normal 1024) and ETC1S (slim TV copies, Grand Allee). The player turns frame-rate switching off on TVs (`PlayerFactory.kt:142`) and skips its effect graph for HDR (`VideoRoute.kt:42`).

## Actions, ranked

Each is a test first; none is applied.

1. **Android render size.** UI, video and games are all drawn at 4K by this iGPU, and the UI's slow frames are almost all GPU draw time. Test: `waydroid prop set persist.waydroid.width 1920` and `height 1080`, restart the session, compare `dumpsys gfxinfo` and look at a 4K film. Risk: if WayDroid composes video inside Android (C5, unknown), films drop to 1080p; then it is a choice between 4K detail and a smooth UI.
2. **Refresh rate by content.** 119.88 Hz for 23.976 / 29.97 / 59.94 fps, 100 Hz for 25 / 50, 120 Hz for 24 / 30 / 60. Test by hand with `kscreen-doctor` during a 25 fps episode and check that `dumpsys display` follows; only then wire the app to the host.
3. **Texture target for UASTC.** Check `WEBGL_compressed_texture_astc` in the WebView. If present, set the loader's `astcSupported` to false whenever BPTC is present too (one shared helper for the three loader sites). A three.js upgrade does not do this for Android user agents.
4. **Game render scale.** Give the table stage the ceremony's step-down loop, and compare MSAA off at ratio 1.5 and above. Moot if action 1 ends at 1080p.
5. **HDR10 on the SDR link.** Look at one HDR10 film. If washed out, let the effect graph tone-map when the display reports no HDR.
6. **CPPC in the BIOS.** Enables amd-pstate. Compare frame percentiles and idle watts before and after. The board may not offer the switch.
7. **AV1 ceiling.** `ffmpeg -c:v libdav1d -i <title> -f null -` on one 1080p and one 4K AV1 title. If below real time, prefer the HEVC version on this machine.

Not worth doing here: porting the Mali mediump work (no gain expected on Radeon, D2), WebGPU (D3), an FSR pass before plain pixel-ratio scaling is used up (D4), resizing the carve-out (A6), an x86_64 Spieltisch release unless `top` shows the translated library busy during a game (D5).

---

## SIMD on this chip (measured 2026-11-21)

The 4300U has AVX2, FMA, AES-NI, SHA-NI and PCLMUL; no AVX-512. Counted by disassembling the libraries that are actually installed.

| Where | Uses it | Evidence |
|---|---|---|
| Host ffmpeg and dav1d (web player, clip export) | yes | CachyOS `-v3` repos active; dav1d, x264, x265 are `x86_64_v3` packages; 60,408 ymm instructions in libdav1d, 20,500 in libavcodec |
| Android video decoder (c2.ffmpeg: H.264, HEVC, VP9, MPEG-4 software paths) | yes | Android's `libavcodec.so` (FFmpeg 7.1, `--enable-optimizations`): 20,334 ymm instructions |
| Android AV1 decoder (dav1d linked into that libavcodec) | probably not | AVX2 pixel-op count 3,183, against 2,361 for the host libavcodec without dav1d and 7,558 for the host dav1d alone; about 9,900 expected if dav1d's assembly were in. The platform's own dav1d codec (not registered) has 0 |
| Our Rust core (Telegram AES and SHA-256) | yes, on x86_64 and arm64 | x86_64: 727 `aesenc`, 64 `sha256rnds2`, 519 `pclmulqdq`; arm64: 637 `aese`, 64 `sha256h`, 169 `pmull` (aes 0.9, sha2 0.11) |
| Our FFmpeg audio library, x86_64 | no | built with `--disable-asm` (`scripts/build-android-ffmpeg.sh:83`), 0 ymm. Fallback behind the device's own decoders, audio only |
| HDR10 clip export tone mapping | CPU, float | `zscale` + `tonemap` chain (`web/src/transcode/clip-args.ts:56`); Dolby Vision already goes through libplacebo on the GPU |

Library by video codec (channel index, 29,086 sets): H.264 23,455; HEVC 4,101; MPEG-4 403; MPEG-2 199; VC-1 17; **AV1 11**. HDR10 396, Dolby Vision 37.

Verdict: no hand-written SIMD is called for. The one gap (AV1 inside Android) touches 11 titles. If AV1 grows: ship dav1d with its assembly in the app, or prefer a title's HEVC version on this machine. Not worth doing: `target-cpu` flags for the Rust binaries (I/O-bound), assembly in the audio fallback library.

Not verified: that the games' meshopt decoder picks its WASM SIMD variant in this WebView; the AV1 conclusion is from instruction counts, not a timed decode.

---

## A. Data sheet

### A1. CPU

| Item | Value | Tag | Source |
|---|---|---|---|
| Cores / threads | 4C / 4T, no SMT | secondary | https://www.cpu-monkey.com/en/cpu-amd_ryzen_3_4300u ; Notebookcheck snippet https://www.notebookcheck.net/AMD-Ryzen-3-4300U-Laptop-Processor-Benchmarks-and-Specs.449979.0.html |
| Base / boost | 2.7 / 3.7 GHz | secondary | TechPowerUp snippet https://techpowerup.com/cpu-specs/ryzen-3-4300u.c2286 |
| Microarchitecture / node | Zen 2 (Renoir APU), TSMC 7FF (7 nm) | secondary | https://en.wikipedia.org/wiki/Zen_2 |
| L2 | 512 KB per core | secondary | https://en.wikipedia.org/wiki/Zen_2 |
| L3 | 4 MB per Notebookcheck and HP support; cpu-monkey says 8 MB; Wikipedia lists 8 MB as the APU maximum. 4 MB is the better-supported value for 4C parts | secondary, conflict | https://support.hp.com/in-en/document/c06617237 ; https://en.wikipedia.org/wiki/Zen_2 |
| TDP | 15 W (Notebookcheck, laptopmedia, HP); 25 W (TechPowerUp). OEM configurable range exists but is not pinned | secondary, conflict | https://www.notebookcheck.net/AMD-Ryzen-3-4300U-Laptop-Processor-Benchmarks-and-Specs.449979.0.html ; https://support.hp.com/in-en/document/c06617237 |
| Tjmax | not found | unverified | none |
| Media-relevant ISA | AVX, AVX2, FMA3, AES, SHA listed | secondary | https://en.wikipedia.org/wiki/Zen_2 |
| AVX-512 | not listed for Zen 2 on the Wikipedia page; absence on a list is not proof | unverified | AMD primary not retrieved |

### A2. Memory

| Item | Value | Tag | Source |
|---|---|---|---|
| Types | DDR4-3200 and LPDDR4X-4266 | secondary | https://www.anandtech.com/show/15624 (search summary) |
| Channels | Two 64-bit controllers: 2 x 64 DDR4 or 4 x 32 LPDDR4X | secondary | Hot Chips slides https://www.slideshare.net/slideshow/hot-chips-amd-next-gen-7nm-ryzen-4000-apu-238080884/238080884 |
| Peak bandwidth | DDR4 dual 51.2 GB/s; single 25.6 GB/s (computed: 3200 MT/s x 8 B per channel) | secondary, computed | as above |
| Max capacity | 64 GB DDR4, 32 GB LPDDR4X | secondary | AnandTech summary |
| Which SKU board uses which | not verified for the 4300U laptop | unverified | check the OEM board |

Single- vs dual-channel: the iGPU has no separate VRAM, so both CPU and GPU traffic share these channels. Single-channel is the expected bottleneck for any texture-heavy or post-process-heavy path.

### A3. iGPU

| Item | Value | Tag | Source |
|---|---|---|---|
| Marketing name | Radeon Graphics ("Radeon RX Vega 5" in Notebookcheck) | secondary | https://www.notebookcheck.net/AMD-Ryzen-3-4300U-Laptop-Processor-Benchmarks-and-Specs.449979.0.html |
| Compute units | 5 CUs = 320 stream processors (TechPowerUp says 320SP, consistent with 5 x 64) | secondary, computed | https://techpowerup.com/cpu-specs/ryzen-3-4300u.c2286 |
| GPU clock | up to 1400 MHz | secondary | Notebookcheck snippet |
| Architecture | Vega, GFX9 | secondary | Mesa mailing list (see Finding 4) |
| Mesa driver | radeonsi (GL) and RADV (Vulkan) | unverified for this chip's exposed API levels | not fetched; Mesa radeonsi page 404 |
| GL / GLES / Vulkan levels | not retrieved | unverified | |
| DirectX feature level | not relevant to this box | n/a | |

### A4. Video engine (VCN) and codec matrix

Renoir VCN version conflicts: Wikipedia lists Renoir under VCN 2.2 ([secondary](https://en.wikipedia.org/wiki/Video_Core_Next)); an FFmpeg trac comment says 2.0 ([secondary](https://ffmpeg.org/pipermail/ffmpeg-trac/2024-January/068374.html)). AMD's AMF table has no Renoir row; its VCN 2.0 row is shown against Cezanne. Matrix below uses the VCN 2.0 row and is marked as such.

| Function | Renoir (4300U) per best source | Your measured | Tag |
|---|---|---|---|
| H.264 decode 8-bit | yes, up to 4K | yes | primary (VCN 2.0 row), mapping unverified |
| H.264 decode 10-bit | no (table: 8b only) | falls back to SW (agrees) | primary, mapping inferred |
| HEVC decode 8-bit | yes, up to 8K on VCN 2.0 row | yes | primary, mapping unverified |
| HEVC decode 10-bit (Main10) | yes, up to 8K | yes | primary, mapping unverified |
| VP9 decode 8/10-bit (profile 0/2) | yes, up to 8K | profile 2 works | primary, mapping unverified |
| AV1 decode | no (N/A) | no | primary; Wikipedia says AV1 decode arrived in VCN 3.0 ([secondary](https://en.wikipedia.org/wiki/Video_Core_Next)) |
| H.264 encode 8-bit | yes, up to 4K | not measured here | primary, mapping inferred |
| H.264 encode 10-bit | no | n/a | primary, inferred |
| HEVC encode 8/10-bit | yes, up to 4K | not measured | primary, mapping unverified |
| VP9 encode | N/A | n/a | primary |
| AV1 encode | N/A | n/a | primary |
| H.264 B-frames | listed as a VCN 3.0 feature ([secondary](https://en.wikipedia.org/wiki/Video_Core_Next)); assume none on VCN 2 | not measured | unverified for Renoir |
| HEVC B-frames | none in any VCN version per Wikipedia | n/a | secondary |
| Chroma | 4:2:0 only, no 4:2:2 or 4:4:4 | n/a | primary ("All codecs are 4:2:0") |

Confirm/refute from the brief: AV1 decode none (confirmed); VP9 8/10-bit decode yes (confirmed, mapping caveat); HEVC 8/10-bit decode and encode yes (confirmed, encode up to 4K); H.264 8-bit only (confirmed for decode and encode).

Mesa side: `vainfo` profile lists on Renoir are only seen in forum posts ([secondary, unverified command](https://forum.manjaro.org/t/hardware-acceleration-on-ryzen-5700g/137734)). Mesa env-var docs show `AMD_DEBUG` video tier and low-latency flags, not a codec list ([primary](https://docs.mesa3d.org/envvars.html)).

### A5. Display

| Item | Status | Tag |
|---|---|---|
| HDMI / DP versions and max mode for Renoir | not retrieved | unverified |
| HDR | not retrieved | unverified |
| Your TV: 3840x2160 at 120 Hz, no 23.976/24 Hz modes | given | measured by owner |

### A6. Shared memory, GTT and carve-out

Primary amdgpu parameter doc ([primary](https://docs.kernel.org/gpu/amdgpu/module-parameters.html)):
- `gttsize`: default -1, meaning the value TTM sets. The doc does not give the TTM default.
- `vramlimit`: default 0, meaning full VRAM.
- `sg_display`: APU-only; set to 0 if flicker under memory pressure.
- `aspm`: default auto.
- `ppfeaturemask`, `dcfeaturemask`: "current stable set" defaults; no explicit list.

Not verified:
- The GTT default size (commonly described as a share of system RAM). No primary source retrieved.
- `ttm.pages_limit` behaviour.
- BIOS UMA setting semantics.

When the 1 GiB carve-out limits anything: on an APU, most texture and surface memory can live in GTT (system RAM). The carve-out is the VRAM-domain allocation. Expect it to bite only when a VRAM-placed allocation exceeds 1 GiB. This is reasoning, not sourced. Method: compare the VRAM and GTT usage counters while the game and the player run.

---

## B. Linux platform knobs

### B1. CPU frequency driver

- amd-pstate (active, passive, guided) requires CPPC. If the CPU has the CPPC flag, it uses MSRs; otherwise shared memory. Initialization fails without ACPI `_CPC`, and the kernel falls back to acpi-cpufreq. Renoir is not named in the page; the page names Cezanne as the MSR case. [primary](https://docs.kernel.org/admin-guide/pm/amd-pstate.html)
- The page does not state which mode is the default. The kernel-parameters entry also does not name it. [primary](https://docs.kernel.org/admin-guide/kernel-parameters.html)
- EPP: 0x0 is the performance bias, 0xff the efficiency bias. Named profiles: default, custom, dynamic. [primary](https://docs.kernel.org/admin-guide/pm/amd-pstate.html)
- Renoir status on kernel 7.3: unverified. Check which scaling driver is active on the box.

### B2. Power limits

- RyzenAdj sets STAPM (sustained), fast PPT, slow PPT, Tctl limit and clock caps. It needs the ryzen_smu module or /dev/mem access. Settings can be overwritten by power management, so they must be reapplied. The repo links a Renoir tuning guide. [primary](https://github.com/FlyGoat/RyzenAdj). Renoir-specific values are not in the README; the tuning guide it links was not fetched.
- Semantics: STAPM is a sustained average; PPT fast is a short burst. Their exact meaning for combined CPU+iGPU load on Renoir is not sourced. [unverified]
- BIOS locks and ACPI overrides: not covered by the sources read. [unverified]
- A 15 W vs 25 W limit changes sustained clocks for the whole package. The iGPU shares that budget, so any CPU-side boost takes GPU headroom. Not sourced; measure.

### B3. amdgpu Renoir-specific

- PSR: the DC debug doc only shows PSR as a tracing mask; no Renoir statement. [primary](https://docs.kernel.org/gpu/amdgpu/display/dc-debug.html) Status on Renoir: unverified.
- HDMI 2.1 FRL, FreeSync and ALLM: upstream patches for these are described as aimed at Linux 7.4 (the box runs 7.3). Earlier users had to boot with a DC feature mask to get higher HDMI 2.1 modes. Which DCN generations these cover, including Renoir, is not in the sources read. [secondary, snippets] https://phoronix.com/news/Linux-7.4-AMDGPU-HDMI-2.1-Go and https://phoronix.com/news/amdgpu-frl-disabled-by-default. The mask parameter name differs between the two sources (dcfeaturemask in the docs, dc_feature_mask in the press text); verify on the box.
- KWin VRR with HDR: a KDE bug reports VRR works with AMS on, but breaks with AMS off while HDR is active. [primary, bug tracker] https://bugs.kde.org/show_bug.cgi?id=514454
- KWin VRR with low frame rate: another bug reports desktop rendering at low frame rate with VRR enabled. [primary, bug tracker] https://bugs.kde.org/show_bug.cgi?id=507002

---

## C. Video playback

### C1. VA-API post-processing

- deinterlace_vaapi exists. Its default mode is "none", and when left unset it picks the highest mode the hardware reports. Modes come from VAProcDeinterlacing. "Bob" appears in source; motion_adaptive is not confirmed. [primary source, ffmpeg doxygen] https://ffmpeg.org/doxygen/4.2/vf__deinterlace__vaapi_8c.html and https://ffmpeg.org/doxygen/4.4/vf__deinterlace__vaapi_8c_source.html
- tonemap_vaapi exists (ffmpeg filter section 14.2). Its option list and whether Mesa exposes tone mapping for Renoir are not retrieved. [primary TOC] https://ffmpeg.org/ffmpeg-filters.html
- Colour conversion and scaling: scale_vaapi is not in the TOC snippet read. Unverified.
- HDR tone mapping on an SDR path: the Linux HDR path in KWin is untested here; no source on hardware tone-map support for Renoir.
- Zero-copy DMA-BUF to KWin, direct scanout: not sourced. Measure whether the compositor is presenting the video plane without a copy.

Ranked for this box: (1) keep decode on hardware for H.264 8-bit, HEVC 8/10-bit and VP9 (confirmed); (2) let hardware deinterlace only if the source is interlaced; (3) tone mapping in software for 10-bit SDR outputs until hardware path is measured.

### C2. Software decode (AV1 and fallbacks)

- dav1d publishes no benchmark numbers on its site. It lists AVX2 assembly and "Make it fast on desktop" as goals. [primary] https://www.videolan.org/developers/dav1d.html
- dav1d uses both frame and tile threads (secondary blog). [secondary] Method only.
- Openbenchmarking has a dav1d test with Chimera 1080p, 1080p 10-bit, and Summer Nature clips. No Zen 2 4C figure was retrieved. [secondary] https://openbenchmarking.org/test/pts/dav1d
- No primary Zen 2 4C 1080p 10-bit AV1 figure. [unverified, gap]
- Method to get the number: run dav1d with the thread count matching the 4 hardware threads, on 1080p and 2160p 10-bit AV1 samples, record fps and CPU load, then repeat with the ffmpeg libdav1d wrapper and its thread options. Decide 1080p and 2160p limits from that.
- Expectation (not sourced): 1080p AV1 likely playable on 4 threads; 2160p AV1 likely not. Verify.

### C3. Film cadence without 24 Hz modes

Arithmetic (computed, not a source claim):
- 120 / 24 = 5 exactly. A 24p frame held 5 refreshes gives no judder, but plays at 24.000 fps.
- 23.976 content at 120 Hz plays at 24.000 fps, which is 0.1% fast. Over a 2-hour film that is about 7.2 s of drift unless audio is resampled.
- 119.88 / 23.976 = 5 exactly. A 119.88 Hz mode gives true cadence; the TV does not list it.

Options (ranked):
1. Keep 120 Hz and resample audio by the 0.1% factor. Costs a small amount of CPU. Needs no TV or compositor change. Trade-off: video is 0.1% faster than the original master.
2. Custom 119.88 Hz mode on KWin or DRM. Needs the TV EDID to accept a non-advertised mode; not sourced. Test on the box.
3. VRR with a 23.976 base. Needs TV VRR support and a working VRR path; KWin VRR bugs listed in B3 apply.

Method to measure: play a known 23.976 test clip and log frame presentation intervals and audio drift over 10 minutes.

### C4. VA-API encode for this VCN generation

- rc_mode values in ffmpeg: auto (default), CQP, CBR, VBR, ICQ, QVBR, AVBR. A driver may support only a subset. [primary] https://ffmpeg.org/ffmpeg-codecs.html
- low_power: a second encoder with a reduced feature set. Applies to all VAAPI encoders. [primary] same page.
- async_depth: maximum processing parallelism; needs vaSyncBuffer in the driver. Range not stated. [primary] same page.
- max_b_frames: option exists; no values given. Renoir B-frame support: not confirmed (see A4).
- quality / global_quality: higher means smaller and worse. Range is per encoder; h264_vaapi range not stated. [primary] same page.
- Max resolution: 4K for H.264 and HEVC encode on VCN 2.0 row. [primary, mapping unverified]

Mapping for low latency (LAN streaming): CBR or CQP, no B-frames, low async_depth, low_power only if the driver supports it. Measure each option's encode time and output bitrate stability. [method]

### C5. WayDroid presentation path

- Documented: software fallback via waydroid.cfg (gralloc default, EGL swiftshader), applied with an upgrade. QEMU with virtio-gpu is the only VM known to give 3D. [primary] https://docs.waydro.id/faq/get-waydroid-to-work-through-a-vm
- Not documented: gralloc/gbm path to host Mesa, hwcomposer behaviour, overlay use, HDR, refresh-rate switching, video buffer sharing. [gap]
- AOSP: HWC composites layers so the GPU does less work, output goes to a buffer read by consumers such as encoders or GPU. Whether WayDroid's HWC uses real hardware overlays is not stated. [primary] https://source.android.com/devices/graphics/implement-hwc
- Consequence: assume the Android app cannot get a hardware video plane or HDR path until measured. Method: inside the container, look at SurfaceFlinger layer composition types during playback, and at the host compositor's presentation path.

---

## D. Game rendering (three.js / WebGL2 in Chromium-based WebView)

### D1. Compressed textures and KTX2

Ground truth from three.js source ([primary](https://raw.githubusercontent.com/mrdoob/three.js/dev/examples/jsm/loaders/KTX2Loader.js)):
- 0.170 (shipped): one order for both inputs, by `priorityUASTC`: ASTC 4x4 (UASTC input only), BC7, ETC2, ETC1, BC1/BC3, PVRTC, RGBA32. ETC1S input therefore lands on BC7 when BPTC is present.
- dev: separate orders again (ETC1S: ETC2, ETC1, BC7, ...; UASTC: ASTC, BC7, ETC2, ...).
- Detection uses WEBGL_compressed_texture_astc, EXT_texture_compression_bptc, WEBGL_compressed_texture_s3tc, WEBGL_compressed_texture_etc. dev only: on Linux with a non-Android user agent, if ASTC, ETC2, BPTC and DXT are all reported, the loader disables ASTC, ETC1 and ETC2 as likely emulated ([primary](https://raw.githubusercontent.com/mrdoob/three.js/dev/examples/jsm/loaders/KTX2Loader.js), lines 267-282). 0.170 has no such rule ([primary](https://raw.githubusercontent.com/mrdoob/three.js/r170/examples/jsm/loaders/KTX2Loader.js)).

Hardware view for Renoir (Vega, GFX9):
- ETC2: hardware on GFX9, per Mesa's 2018 statement (secondary).
- ASTC: software decompress on gallium drivers without hardware (2018 series, secondary). Current radeonsi status not confirmed.
- BC1 to BC7: GCN-class hardware sampling is believed to support BC; no Mesa source retrieved for this chip. [unverified]

Result: ETC1S input gets BC7 on this box. UASTC input gets BC7 only if the WebView does not expose ASTC; that is unmeasured (see the measured section). Memory (computed):
- BC7: 8 bits per texel. A 2048x2048 texture is 4 MiB, 5.3 MiB with mips.
- RGBA8 uncompressed at the same size: 16 MiB, 21.3 MiB with mips.
- BC1: 4 bits per texel, half of BC7, but alpha-limited and lower quality. Only an option if the loader is told to prefer it, which it is not by default.

Ranking for this box: (1) BC7 for everything; (2) RGBA8 only for UI. ASTC and ETC2 are to be avoided unless shown to be native.

### D2. Precision qualifiers

- Mesa's GLSL precision lowering only applies to drivers that opt in; others ignore mediump. [secondary] https://fuchsia.googlesource.com/third_party/mesa/+/main/src/compiler/glsl/lower_precision.cpp
- radeonsi FP16 mediump for gfx9+ exists behind a `radeonsi_fp16` option; default not confirmed; the merge request is blocked from fetch. [secondary] https://gitlab.freedesktop.org/mesa/mesa/-/merge_requests/9051
- ANGLE translation to desktop GL may drop precision qualifiers. [unverified]
- Expectation: mediump gives little or no gain on this GPU by default. Vega's packed-FP16 hardware is a hypothesis, not sourced.

Method: build the same scene with all precision qualifiers highp, then mediump, and compare GPU frame time, not CPU frame time.

### D3. Backend choice

- Chromium on Linux: ANGLE on OpenGL in recent builds; a developer says Chrome prefers GL on Linux while Vulkan often works. [secondary] https://bbs.archlinux.org/viewtopic.php?id=311691
- Chromium WebGPU on Linux runs on Vulkan with rollout starting with Intel Gen12+; AMD is a tentative later step. [primary, Chrome team] https://developer.chrome.com/blog/new-in-webgpu-144.md.txt
- Android WebView WebGPU: not found. [gap]
- three.js WebGPURenderer: depends on WebGPU; not a realistic default here. Its fallback behaviour is not sourced.

Ranking: (1) WebGL2 with three.js WebGLRenderer on the current GL path; (2) WebGPU only as a measured experiment on the Linux desktop build; (3) do not plan around WebGPU in the WayDroid WebView.

### D4. Ranked techniques for an iGPU sharing DDR4 with the CPU

Ordered by likely impact on a bandwidth-bound iGPU. Tags show what is sourced.

1. Render below native and upscale with FSR 1 (EASU + RCAS; shader-only, GLSL/HLSL source provided). Largest single saving on fill and bandwidth. Needs a GLSL port for WebGL2 (not confirmed to exist). Measure: GPU frame time at 0.5x, 0.67x, 0.77x scale, plus visual check. [primary for FSR; cost figures are for other GPUs]
2. Cut full-screen post passes and keep them at 8-bit where the result allows. Each 4K RGBA8 pass costs about 66 MB read+write (computed). Half-float doubles it. Measure: remove one pass at a time. [computed; no source for three.js guidance retrieved]
3. Compressed textures (BC7 via KTX2). Cuts texture bandwidth and memory 4x versus RGBA8 (computed, see D1). [primary for loader choice]
4. Shadow map size and count. Fill and bandwidth for the shadow pass scale with map area. [unverified, reasoning]
5. MSAA vs post-process AA. MSAA multiplies colour and depth traffic; FXAA or SMAA on a reduced-scale frame is cheaper. [unverified, reasoning]
6. Overdraw and draw-call count. Instancing and merging static props cut CPU submission across 4 threads. Skinned characters cost vertex work and bone data reads. [unverified, reasoning]
7. Mediump (see D2). Low expectation on this GPU; test once.

Target-driven note: 120 Hz display; 60 fps gives 2 refreshes per frame and 30 fps gives 4 exactly (computed). The 30 fps floor is a pacing choice, not a display constraint.

### D5. ARM translation under WayDroid

- AOSP's native bridge exists so apps with only ARM native code can run on x86 with a translator. Translated libraries are loaded by a separate linker. [primary, snippet only] https://android.googlesource.com/platform/art/+/2da31d9d2b/libnativebridge/
- Expected scope (not sourced): only the app's ARM .so files are translated. The WebView provider is a system component and runs native x86 if the image ships an x86 WebView. [unverified]
- Consequence: the Rust library's hot loops run translated, the WebGL draw calls issued from WebView do not. JNI crossings between Java/Kotlin and the Rust library are a plausible cost. [unverified]

Method: time the native library's hot calls against an equivalent x86 build if one is available; measure JNI crossing count per frame.

---

## Unresolved questions

1. Renoir VCN version: 2.0 or 2.2? AMF has no Renoir row; Wikipedia and FFmpeg disagree. Affects every codec claim in A4.
2. Renoir H.264 B-frame and HEVC B-frame support on this VCN generation.
3. TDP and OEM power range (15 vs 25 W). L3 is settled: 4 MiB, measured.
4. Tjmax and full display-engine spec (HDMI/DP versions, HDR) for Renoir. Not retrieved.
5. Settled by measurement: acpi-cpufreq is active because the BIOS exposes no `_CPC`; the CPU has the `cppc` flag.
6. Default radeonsi_fp16 value and whether mediump has any effect on gfx9.
7. Which BC formats Mesa exposes as native for radeonsi on this chip, and whether ETC2 and ASTC are reported as emulated.
8. GTT is 7.3 GiB on the box (measured). Whether the full 1 GiB carve-out ever limits a workload is still reasoning only.
9. Whether HDMI 2.1 FRL, VRR and custom modes work on this Renoir DC generation on kernel 7.3, and the exact parameter name.
10. Settled by measurement: the monitor lists 3840x2160 at 119.88 and 100 Hz, no custom mode needed. Open: whether WayDroid follows a host refresh change.
11. Whether VA-API tone mapping and deinterlace modes beyond "bob" exist on Renoir Mesa.
12. KWin zero-copy or direct scanout for video planes.
13. dav1d 1080p and 2160p 10-bit AV1 figures for a 4C/4T Zen 2 class CPU. No primary number found.
14. WayDroid's gralloc, HWC, overlay, HDR and refresh-rate behaviour. Docs gap.
15. Android WebView backend (GL or Vulkan) and WebGPU status in that WebView.
16. Whether only the app's ARM .so files are translated under WayDroid's native bridge, and what the WebView ABI is in the image.
17. FSR 1 GLSL port status for WebGL2 and its real cost on a 5-CU iGPU.
18. Whether RyzenAdj's Renoir tuning guide and BIOS locks allow the 15 W and 25 W targets on this OEM board.
