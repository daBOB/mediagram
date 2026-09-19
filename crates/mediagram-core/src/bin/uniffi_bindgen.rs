//! Entry point for generating foreign-language bindings from this crate's
//! compiled library, e.g.
//! `cargo run -p mediagram-core --features cli --bin uniffi-bindgen -- \
//!   generate --library target/debug/libmediagram_core.so --language kotlin --out-dir <dir>`.

fn main() {
    uniffi::uniffi_bindgen_main();
}
