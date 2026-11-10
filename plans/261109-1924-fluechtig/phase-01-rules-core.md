# Phase 1: rules core

Port the prototype's rules module to Rust, pure (no room, no clock).

Files (create, `crates/mediagram-game/src/`):
- `fluechtig_map.rs`: the 48 fields (x, y in a 960×540 plan), links with their
  transport, adjacency, start fields. Same data as the prototype.
- `fluechtig_rules.rs`: `Chase` state, legal moves, a move, the double move,
  the forced steps (pass, stuck, last round), hideouts, the log. Winner and reason.
- `fluechtig_rules_tests.rs`: the prototype's eight walkthroughs as tests, plus
  "hideouts always hold the real field".

- [x] map + adjacency, connected, 77/14/4 links
- [x] rules: moves, play, double, settle, reveal, hideouts, inflow
- [x] eight walkthrough tests pass

Done when: `cargo test -p mediagram-game fluechtig_rules` is green and every
walkthrough ends as it did in the prototype.
