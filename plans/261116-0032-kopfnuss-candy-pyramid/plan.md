# Kopfnuss: a candy pyramid for the Gipfelsturm

Decision (user, 2026-11-16): use the updated characters and change the endgame to a pyramid, close to
`~/Downloads/kopfnuss/pyramide.jpg`, `endgameEnd.jpg`, and `maxresdefault.jpg`. The earlier proposal for three
visual variants is dropped. Keep the complete game, its 10 climbing steps, scoring, clocks, and phone UI unchanged.

## Design

- Replace the rocky switchback mountain with ten brightly coloured, stacked candy tiers and a glowing star summit.
- Keep the existing pastel game-show language: white sugar clouds, a rainbow arch, gold-edged step markers, and
  restrained depth and shadow rather than photorealism.
- Put the existing full-body avatar figures on the steps. Keep the head token as a fallback when an avatar image
  cannot load, and keep the player name beside every figure.
- Energy: bright and celebratory. Rhythm: ten clear horizontal tiers with a single vertical climb. Motion: the
  existing smooth climber movement and a restrained summit-star pulse; global reduced-motion remains in force.
- Copy stays German and unchanged.

## Files

- `crates/mediagram-game/client/src/tv/kopfnuss/mountain-trail.ts`: pyramid station geometry.
- `crates/mediagram-game/client/src/tv/kopfnuss/KopfnussClimb.tsx`: pyramid, clouds, rainbow, star and full figures.
- `crates/mediagram-game/client/src/tv/kopfnuss/kopfnuss.css`: candy finish, typography, figures and responsive sizing.
- `crates/mediagram-game/client/tests/kopfnuss-mountain-trail.test.ts`: station and sharing regression checks.
- Bump 0.192.7 → 0.193.0 in the two Rust manifests, client package, Android `versionName`, and iOS marketing version.

No Rust game logic, protocol, scoring, question stages, character artwork, or new dependency changes.

## Verification

1. Run the focused trail test, the client test suite, TypeScript check and production build.
2. Render the real climb component with representative players at base, middle, tied and summit positions.
3. Check 960×540 and 1920×1080: all ten tiers visible, no clipped summit/figures/text, names readable, figures
   aligned to steps, reduced motion respected.
4. Check contrast for header/name/step-label colour pairs with the antislop contrast checker.
5. Inspect the finished screenshot against the supplied pyramid references and record any remaining visual gap.
