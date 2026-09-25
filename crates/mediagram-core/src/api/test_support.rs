//! A `Core` builder for tests, split out of `mod.rs` to keep that file under
//! the line limit `code_standards.rs` enforces.

use std::sync::Arc;

use super::Core;

impl Core {
    /// A core over `dir` with placeholder credentials, for tests that never connect.
    pub(crate) fn at(dir: &std::path::Path) -> Arc<Self> {
        Core::new(dir.display().to_string(), 1, "test-hash".into(), "test-device".into())
    }
}
