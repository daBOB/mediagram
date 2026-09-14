---
name: mlib-spec-review-context
description: Context for reviewing the mediagram Rust workspace (mlib-spec crate); Telegram caption/UTF-16 budget nuance and where locked decisions live
metadata:
  type: project
---

Phase-1 review (2026-09-14) of `crates/mlib-spec` found two debug-mode panics (`then_some(i-1)` on empty slice; `CAPTION_BUDGET - used - 1` underflow at exactly 1024) and that the caption budget was counted in `chars()` while Telegram counts UTF-16 code units.

**Why:** the plan's locked decision says "line 2 JSON is plain ASCII" but `serde_json` emits non-ASCII raw, so budget counting must be UTF-16 regardless. Locked decisions live in `plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md` ("Locked decisions") and must not be proposed for reversal.

**How to apply:** in later phases (uploader, Android/UniFFI), re-check that any caption length checks use `encode_utf16().count()`, and verify `part_for_offset`/`Episode::Range([u32;2])`/`usize` returns were changed before UniFFI export. Review reports go to the plan's `reports/` dir with the hook-injected name, even when the task prompt names `plans/reports/`.
