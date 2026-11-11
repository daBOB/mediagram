//! Generates the Kotlin bindings from the compiled library; scripts/generate-spieltisch-bindings.sh runs it.

fn main() {
    uniffi::uniffi_bindgen_main();
}
