//! Watch-together session messages: one unpinned text message per session in
//! the library channel, edited in place (`docs/mlib-spec.md` §7b). Only I/O
//! lives here. The rules of a session are in Kotlin
//! (`android/core/model/.../together/`), ported from the web
//! (`web/public/lib/together/`) and held to its fixtures.

use std::sync::PoisonError;

use super::Core;

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TogetherMessage {
    pub message_id: i32,
    pub text: String,
}

#[uniffi::export]
impl Core {
    /// The newest watch-together message the listener heard. Read it after a `LibraryEvent::Together`.
    pub fn together_heard(&self) -> Option<TogetherMessage> {
        self.together_heard
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .clone()
    }
}
