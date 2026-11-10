# Phase 2: the game in the room

Files:
- create `fluechtig.rs` (the `Game`: seating, the two-step pick, taps, absent
  movers, scores), `fluechtig_config.rs`, `fluechtig_views.rs` (phone sheets,
  TV scene), `protocol_fluechtig.rs` (wire types), `fluechtig_tests.rs`
- modify `protocol.rs` (`TvScene::Fluechtig`), `protocol_settings.rs`
  (`GameKind::Fluechtig`), `factory.rs`, `lib.rs`, `room_settings.rs` (+ tests: 2–6)

- [x] seating: one fugitive, 4 or 5 hunter pieces round-robin
- [x] pick: ticket → destination / Zurück / Doppelzug; follow-up opens `chain_ms` later; early taps ignored
- [x] absent mover plays after `auto_ms`
- [x] phones: fugitive sees the field, hunters never do; hunters see their pieces and tickets
- [x] TV scene never carries the fugitive's field before the end, except a reveal
- [x] lobby allows 2–6

Done when: flow tests green, including a leak test over a full played game.
