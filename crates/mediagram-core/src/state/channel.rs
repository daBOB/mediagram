//! Whose watch-state document a caption names.
//!
//! A port of the caption half of `web/src/telegram/state-channel.ts`. State
//! documents are discovered from the channel's pin list, never by search —
//! Telegram parses `#mlib-state` as the hashtag `#mlib`, so a search for it
//! would also return every `#mlib v=…` index caption in the channel, and a
//! freshly pinned message is not found by search at all. The grammers half
//! that actually reads the pin list lives under `api::state_sync`, the only
//! place in this crate allowed to touch a live connection; nothing here
//! does, which is what makes this file testable without one.

/// What every state caption begins with, and what tells a reader a pinned
/// message is one of these rather than the library index (`#mlib-index`) or
/// anything else someone pinned by hand.
pub const STATE_MARKER: &str = "#mlib-state";
const STATE_VERSION: i64 = 1;

/// `#mlib-state v=1 device=…`, which is also how a reader knows whose it is.
pub fn state_caption(device: &str) -> String {
    format!("{STATE_MARKER} v={STATE_VERSION} device={device}")
}

/// Whose document a caption says this is, or `None` for anything that is
/// not one — including the index's own caption, which only mentions this
/// marker as a word rather than starting with it, the way a text search
/// would find it.
pub fn device_from_caption(caption: &str) -> Option<String> {
    let rest = caption.strip_prefix(STATE_MARKER)?.strip_prefix(' ')?;
    rest.split_whitespace()
        .find_map(|word| word.strip_prefix("device="))
        .filter(|device| !device.is_empty())
        .map(str::to_string)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_caption_this_player_wrote_names_the_device_and_reads_back_as_it() {
        let caption = state_caption("b398013d-986b");
        assert!(caption.starts_with(STATE_MARKER));
        assert_eq!(
            device_from_caption(&caption).as_deref(),
            Some("b398013d-986b")
        );
    }

    #[test]
    fn the_indexs_caption_is_not_a_state_document() {
        // A search matches on words, so the index's caption can come back
        // from a search for this marker. Reading it as a device called
        // nothing would attribute somebody's history to a file full of
        // tables.
        assert_eq!(device_from_caption("#mlib-index v=2 sets=566"), None);
    }

    #[test]
    fn a_marker_with_no_device_on_it_is_not_one_either() {
        assert_eq!(device_from_caption("#mlib-state v=1"), None);
    }

    #[test]
    fn a_caption_that_merely_mentions_the_marker_is_not_one() {
        assert_eq!(
            device_from_caption("talking about #mlib-state device=x"),
            None
        );
    }

    #[test]
    fn nothing_at_all_is_not_one() {
        for caption in ["", "   "] {
            assert_eq!(device_from_caption(caption), None);
        }
    }

    #[test]
    fn a_device_id_stops_at_whitespace_rather_than_swallowing_the_rest() {
        assert_eq!(
            device_from_caption("#mlib-state v=1 device=laptop and more").as_deref(),
            Some("laptop")
        );
    }

    #[test]
    fn a_device_id_is_found_wherever_it_sits_in_the_caption() {
        assert_eq!(
            device_from_caption("#mlib-state device=laptop v=1").as_deref(),
            Some("laptop")
        );
    }
}
