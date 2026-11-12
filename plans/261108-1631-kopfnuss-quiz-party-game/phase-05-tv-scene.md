---
phase: 5
title: "TV scene"
status: completed
priority: P2
effort: "4h"
dependencies: [2, 3]
---

# Phase 05: TV scene

## Overview
`TvKopfnuss.tsx` draws every stage in 2D, in the existing quiz TV style. No 3D
staging in this build.

## Requirements
- **Pick:** 3 category cards, a countdown, and avatar chips of the players who are done. Votes and targets stay **secret** until Resolve.
- **Resolve:** the winning card grows, vote counts appear, and the trick events play in order: "Anna → Ben: Spiegel", or "abgeprallt!" for a bounce.
- **Question:** reuse the existing quiz TV component (`TvApp.tsx` `Quiz`) with the `QuizScene`. Next to it, a strip shows which players carry which tricks.
- **Climb:** a 2D ladder of 10 steps with avatars at their step and a 90 s countdown.
- **Fact:** the winner's avatar plus the fact text, large.
- Readable from the sofa: same type scale as the existing quiz TV.

## Architecture
- `client/src/tv/TvKopfnuss.tsx` dispatches on stage; sub-views go in `client/src/tv/kopfnuss/*.tsx` to keep files under 200 lines.
- `TvApp.tsx`: one more `view.scene.kind === "kopfnuss"` arm.
- Optional: one `kopfnuss-cues.ts` with a bun test that diffs events into one-shot animations (follow `race-sound-cues` if needed).

## Related Code Files
- Create: `client/src/tv/TvKopfnuss.tsx`, `client/src/tv/kopfnuss/{PickStage,ResolveStage,ClimbStage,FactStage}.tsx`, `client/src/tv/kopfnuss/kopfnuss.css`
- Modify: `client/src/tv/TvApp.tsx` (one arm; export `Quiz` if not exported)

## Tests first
- If a pure helper exists (event sequencing or ladder layout), give it a bun test first. The views themselves are checked by screenshot (UI rule).

## Implementation Steps
1. TvApp arm, then the stage components, then the CSS.
2. Run the host with 3 bot phones (extend `tools/smoke.mjs` with `--game kopfnuss`) and take screenshots of each stage.

## Success Criteria
- [ ] Each stage rendered and screenshotted in `reports/evidence/`
- [ ] Other games' TV views unchanged

## Risk Assessment
- A TV-box WebView performance hit is unlikely from 2D DOM/CSS. Check on the box in phase 06.
