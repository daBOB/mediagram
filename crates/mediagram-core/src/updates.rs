//! Which channel updates are worth acting on, and how often.
//!
//! A port of `web/src/telegram/updates.ts`, pinned to it by the fixtures in
//! `web/test/fixtures/channel-updates/` (see
//! `tests/shared_channel_update_fixtures.rs`). The web is the reference: a
//! case this passes and the web does not is a bug here.
//!
//! An update is only a hint. Whoever acts on one still runs the ordinary sync
//! or refresh round, which reads the pin list itself, so nothing here decides
//! anything about data — only whether a round is worth starting.

use serde::Deserialize;

use mlib_spec::index_caption::PREFIX as INDEX_CAPTION_PREFIX;

/// What every watch-state caption begins with, space included:
/// `#mlib-state v=1 device=…`.
const STATE_PREFIX: &str = "#mlib-state ";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum UpdateKind {
    New,
    Edit,
    Pinned,
    Delete,
    Other,
}

/// One raw update, reduced to what classifying needs. The MTProto adapter
/// builds these; nothing here knows grammers' types.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
pub struct ChannelUpdate {
    pub kind: UpdateKind,
    /// Bare channel id (no -100 prefix).
    pub channel: i64,
    /// The message's text or caption, for `New` and `Edit`.
    #[serde(default)]
    pub caption: Option<String>,
    /// A service message, such as "pinned a message".
    #[serde(default)]
    pub service: bool,
    /// For `Pinned`: pinned, or unpinned.
    #[serde(default)]
    pub pinned: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, uniffi::Enum)]
#[serde(rename_all = "lowercase")]
pub enum LibraryEvent {
    State,
    Index,
}

/// Released in this order when both are due at once, so every port agrees.
const ORDER: [LibraryEvent; 2] = [LibraryEvent::State, LibraryEvent::Index];

pub fn classify(update: &ChannelUpdate, channel: i64, own_device: &str) -> Option<LibraryEvent> {
    if update.channel != channel {
        return None;
    }
    match update.kind {
        // An unpin arrives in the same burst as the pin of its replacement;
        // the pin alone says the library moved.
        UpdateKind::Pinned => return update.pinned.then_some(LibraryEvent::Index),
        UpdateKind::New | UpdateKind::Edit if !update.service => {}
        _ => return None,
    }
    let caption = update.caption.as_deref().unwrap_or("");
    if caption.starts_with(STATE_PREFIX) {
        // This device's own write comes back to it on some libraries; it is not news.
        return device_from_caption(caption)
            .filter(|device| *device != own_device)
            .map(|_| LibraryEvent::State);
    }
    // An index is published by sending a new one, never by editing an old one.
    (update.kind == UpdateKind::New && caption.starts_with(INDEX_CAPTION_PREFIX))
        .then_some(LibraryEvent::Index)
}

/// Whose state document a caption says this is: the web's
/// `deviceFromCaption`, which matches `/\bdevice=(\S+)/`.
pub fn device_from_caption(caption: &str) -> Option<&str> {
    if !caption.starts_with(STATE_PREFIX) {
        return None;
    }
    let is_word = |c: char| c.is_ascii_alphanumeric() || c == '_';
    let mut from = 0;
    while let Some(found) = caption[from..].find("device=") {
        let at = from + found;
        let boundary = caption[..at]
            .chars()
            .next_back()
            .is_none_or(|c| !is_word(c));
        let rest = &caption[at + "device=".len()..];
        let end = rest.find(char::is_whitespace).unwrap_or(rest.len());
        if boundary && end > 0 {
            return Some(&rest[..end]);
        }
        from = at + 1;
    }
    None
}

/// At most one event per kind per window, released at the window's end.
///
/// The window is timed from the first event, not reset by later ones: an
/// upload that pushes an index every few seconds for an hour would otherwise
/// never let one through.
#[derive(Debug)]
pub struct Debouncer {
    window_ms: u64,
    /// When each kind in [`ORDER`] falls due, if pending.
    due: [Option<u64>; 2],
}

impl Debouncer {
    pub fn new(window_ms: u64) -> Self {
        Self {
            window_ms,
            due: [None; 2],
        }
    }

    pub fn offer(&mut self, event: LibraryEvent, now_ms: u64) {
        let slot = &mut self.due[slot_of(event)];
        slot.get_or_insert(now_ms + self.window_ms);
    }

    /// Events whose window has ended, removed from the pending set.
    pub fn take(&mut self, now_ms: u64) -> Vec<LibraryEvent> {
        let mut ready = Vec::new();
        for event in ORDER {
            let slot = &mut self.due[slot_of(event)];
            if slot.is_some_and(|due| due <= now_ms) {
                *slot = None;
                ready.push(event);
            }
        }
        ready
    }

    /// When the next event falls due, for arming a timer; `None` when idle.
    pub fn next_due(&self) -> Option<u64> {
        self.due.iter().flatten().min().copied()
    }
}

fn slot_of(event: LibraryEvent) -> usize {
    match event {
        LibraryEvent::State => 0,
        LibraryEvent::Index => 1,
    }
}
