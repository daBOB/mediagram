//! Adoption scan: finds parts that were already posted to the channel but never
//! recorded locally (a crash between `send_message` and `mark_done`), so resume
//! never uploads a part twice.

use std::collections::HashMap;

use mlib_spec::CaptionError;

use crate::index::rescan::Seen;

/// An existing channel message already carrying one of our parts, found by
/// scanning recent history instead of re-uploading.
pub struct Adopted {
    pub message_id: i64,
    pub doc_id: i64,
    pub sha256: String,
}

/// What scanning recent channel history for `set_id`'s parts found.
pub struct AdoptionScan {
    pub adopted: HashMap<u32, Adopted>,
    /// A message in the scan window names this set but carries a caption
    /// version this build cannot read — one of its parts already reached
    /// the channel from a newer uploader. Resuming here would resend it.
    pub blocked_by_newer_caption: bool,
}

/// Matches recent channel messages against `set_id` by parsing their
/// caption, keyed by part index so each pending part can be looked up once.
///
/// A caption whose `#mlib v=N` this build cannot read is not simply skipped
/// the way a garbled one is: its `set` field alone is read leniently (every
/// version only adds fields), and if it names `set_id`, the whole scan is
/// flagged blocked rather than risk resending a part that is already there.
pub fn adoption_map(set_id: &str, recent: &[Seen]) -> AdoptionScan {
    let mut adopted = HashMap::new();
    let mut blocked_by_newer_caption = false;
    for seen in recent {
        if !mlib_spec::caption_codec::is_mlib(&seen.caption) {
            continue;
        }
        let caption = match mlib_spec::parse(&seen.caption) {
            Ok(caption) => caption,
            Err(CaptionError::UnsupportedVersion(_)) => {
                if mlib_spec::caption_codec::set_id_any_version(&seen.caption).as_deref()
                    == Some(set_id)
                {
                    blocked_by_newer_caption = true;
                }
                continue;
            }
            Err(_) => continue,
        };
        if caption.set != set_id {
            continue;
        }
        let Some(doc_id) = seen.doc_id else {
            continue;
        };
        adopted.entry(caption.part.i).or_insert(Adopted {
            message_id: seen.message_id,
            doc_id,
            sha256: caption.part.sha256,
        });
    }
    AdoptionScan {
        adopted,
        blocked_by_newer_caption,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn seen(message_id: i64, caption: &str) -> Seen {
        Seen {
            message_id,
            doc_id: Some(message_id),
            caption: caption.to_string(),
            sent_at: 0,
        }
    }

    /// A caption from a newer uploader that names this exact set blocks the
    /// scan, so `run_set` refuses to resume rather than resending a part
    /// that is already in the channel under a caption it cannot read.
    #[test]
    fn a_newer_caption_naming_this_set_blocks_the_scan() {
        let recent = vec![seen(1, "#mlib v=99\n{\"set\":\"S1\"}")];

        let scan = adoption_map("S1", &recent);

        assert!(scan.adopted.is_empty());
        assert!(scan.blocked_by_newer_caption);
    }

    /// Real part captions end in a human line after the JSON; the guard must
    /// still see the set id there, or resume resends a part already posted.
    #[test]
    fn a_newer_caption_with_a_human_line_still_blocks_the_scan() {
        let recent = vec![seen(1, "#mlib v=99\n{\"set\":\"S1\"}\nMovie human line")];

        let scan = adoption_map("S1", &recent);

        assert!(scan.adopted.is_empty());
        assert!(scan.blocked_by_newer_caption);
    }

    /// A caption from a newer uploader naming a *different* set does not
    /// block resuming this one — it is simply not this set's part.
    #[test]
    fn a_newer_caption_naming_another_set_does_not_block() {
        let recent = vec![seen(1, "#mlib v=99\n{\"set\":\"OTHER\"}")];

        let scan = adoption_map("S1", &recent);

        assert!(scan.adopted.is_empty());
        assert!(!scan.blocked_by_newer_caption);
    }
}
