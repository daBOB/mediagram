---
title: "Game night 3D avatars made in Meshy (22, 33 clips)"
author: Andre
date: 2026-11-08
id: l-f9598b90
scope: repo
status: active
touches:
  - docs/superpowers/specs/2026-11-03-game-night-design.md
  - crates/mediagram-game/tools/slim-avatar-for-tv.mjs
  - crates/mediagram-game/tools/rebake-texture-atlas.mjs
---

## Decided
2026-11-03: the user pulled the 3D avatars (a non-goal in the game night spec,
`docs/superpowers/specs/2026-11-03-game-night-design.md` on main)
into the current work and wants them animated, so they are GLB models, not images.

User choices: nano-banana-2 for the concept image, meshy-7.1 for image-to-3d, one
avatar per seat colour, "movie-night animals". Meshy rigs only bipeds, so every
animal stands on two legs in a T-pose, hands empty, wearing a hoodie in its seat
colour, with its prop worn (not held). Cast: fox popcorn-bucket hat #ef476f
(done), frog 3D glasses #06d6a0, raccoon headphones #118ab2, bear cub usher cap
#ffd166, cat star sunglasses #9b5de5, bunny bow tie + ticket on ear #f15bb5, yeti
scarf #00bbf9, hedgehog clapperboard-striped cap #fb8500.

Pipeline per avatar: text-to-image (pose_mode t-pose, remove_background) →
image-to-3d (input_task_id, should_remesh, target_polycount 20000, triangle,
glb) → rig (height 1.0) → animate action_ids [0 Idle, 28 Big Wave Hello, 298
Cheer Both Hands Up, 391 Head Hold in Pain, 412 Victory] in one GLB.
6 + 30 + 5 + 15 = 56 credits. Fox: 20.6k tris, 24 joints, 3.7 MB, 2K JPEG texture.
Output in `assets/game-night/avatars/<animal>/` of the main checkout (moved there from ../mediagram-spieltisch 2026-11-07 evening), uncommitted — the only copy.

**Why:** the fox was a test so a style or rigging problem shows once, not 8 times.
User, on fox v1 (big chibi head, short arms): "the arms and hands goes into its
…

## Watch out
for the other 7 reuse the v3 proportion sentence verbatim:
"the head is round and only as wide as the shoulders, about 30 percent of the
total height; a short visible neck; a soft round belly; sturdy chunky legs; arms
long enough to reach the hips when hanging down", plus "Glossy vinyl toy figure
style … plain neutral light grey background", t-pose, remove_background.

2026-11-04: even v3's head is too wide for Meshy's human clips. User: "cheers with
both hands .. here the hands get inside the head" (I had called it "brushing the
…

_From Claude Code's memory of this repo, last updated Nov 6._
