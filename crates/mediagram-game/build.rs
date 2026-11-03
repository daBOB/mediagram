fn main() {
    // The pages are embedded at compile time; rebuild when they change.
    println!("cargo:rerun-if-changed=client/dist");
}
