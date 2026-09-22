//! Finding the newest index a channel holds, and saying plainly when it
//! holds none.
//!
//! `mediagram push-index` pins each snapshot of `library.db` and unpins the
//! one it replaces, so a healthy channel holds exactly one pinned. But a pin
//! is a separate Telegram operation: a publish whose unpin failed, a second
//! machine publishing to the same channel, or a snapshot pinned by hand all
//! leave the pin pointing somewhere other than the newest snapshot. The
//! timestamp in the caption is a fact about the snapshot itself, so that is
//! what decides, and the pin is only one of the places worth looking.

use grammers_mtsender::InvocationError;
use serde::Deserialize;

use super::CoreError;

/// The marker `mediagram push-index` writes on the index snapshot's caption.
///
/// Matched on the version-less prefix, the way the uploader's own `rescan`
/// matches it, so a future `v=3` snapshot is still recognised as an index.
/// Duplicated from the uploader rather than shared with it: this is a wire
/// contract between two programs, like the caption format and the schema,
/// and this crate cannot depend on the CLI.
pub(super) const INDEX_CAPTION_PREFIX: &str = "#mlib-index";

/// The JSON line under the marker. Only the timestamp is read: it names the
/// snapshot, so two devices refreshing the same one land on the same
/// catalogue version rather than one each.
#[derive(Deserialize)]
struct IndexCaption {
    pushed_at: i64,
}

pub(super) const NOTHING_PINNED: &str = concat!(
    "That channel has nothing pinned. The uploader pins its index after ",
    "every completed set — run `mediagram push-index` on the machine that ",
    "uploads to it, then try again.",
);

pub(super) const NOT_AN_INDEX: &str = concat!(
    "What is pinned in that channel is not a library index. Choose the ",
    "channel `mediagram` uploads to, or run `mediagram push-index` there.",
);

pub(super) const UNREADABLE: &str = concat!(
    "The index pinned in that channel could not be read as a library. It ",
    "may still be uploading; a fresh `mediagram push-index` replaces it.",
);

pub(super) const NO_LONGER_VISIBLE: &str = concat!(
    "This account can no longer open that channel. Choose another library ",
    "from the list.",
);

/// Which candidate is the newest index, or why none of them is.
///
/// Newest by the `pushed_at` its caption carries, not by whichever one a pin
/// happens to point at. A pin can be left behind by a publish whose unpin
/// failed or moved by a second machine publishing to the same channel, and
/// then it names an older library than the channel actually holds — which is
/// indistinguishable, to a reader, from the library having shrunk. The
/// timestamp cannot drift that way because it travels with the snapshot.
///
/// A caption whose timestamp cannot be read loses to any that can, and the
/// higher message id breaks a tie, so the choice is total and the same on
/// every device.
///
/// Pure, and given only the captions and their ids: the decision is the part
/// that has to be right, and it is the part that cannot be exercised against
/// a live channel in a test suite.
pub(super) fn pick_index(candidates: &[(&str, i64)]) -> Result<usize, CoreError> {
    let newest = candidates
        .iter()
        .enumerate()
        .filter(|(_, (text, _))| text.starts_with(INDEX_CAPTION_PREFIX))
        .max_by_key(|(_, (text, id))| (pushed_at_of(text), *id));

    match newest {
        Some((idx, _)) => Ok(idx),
        None if candidates.is_empty() => Err(CoreError::Library(NOTHING_PINNED.into())),
        None => Err(CoreError::Library(NOT_AN_INDEX.into())),
    }
}

/// The timestamp a caption carries, or nothing when it carries none this
/// build can read. Kept apart from [`pushed_at`] because choosing between
/// snapshots must not treat an unreadable timestamp as any particular time.
fn pushed_at_of(caption: &str) -> Option<i64> {
    caption
        .split_once('\n')
        .and_then(|(_, json)| serde_json::from_str::<IndexCaption>(json.trim()).ok())
        .map(|parsed| parsed.pushed_at)
        .filter(|pushed| *pushed > 0)
}

/// When the snapshot behind `caption` was pushed, for naming the catalogue
/// version it installs. A caption this build cannot parse is not a reason to
/// refuse a readable index, so the fallback is simply "now".
pub(super) fn pushed_at(caption: &str, now: i64) -> i64 {
    pushed_at_of(caption).unwrap_or(now)
}

/// Telegram refusing a request about a channel, told apart from the line
/// going down.
///
/// A client error means this account cannot address that channel any more —
/// removed from it, or the channel deleted — and no amount of retrying
/// changes that, so it must not be reported as a network blip the person is
/// invited to wait out.
pub(super) fn channel_error(err: &InvocationError) -> CoreError {
    match err {
        InvocationError::Rpc(rpc) if rpc.code == 400 || rpc.code == 403 => {
            CoreError::NotAuthorized(NO_LONGER_VISIBLE.into())
        }
        _ => CoreError::Network("the channel could not be read just now".into()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const INDEX: &str = "#mlib-index v=2\n{\"pushed_at\":1781568000,\"sets\":538,\"schema\":6}";

    /// An index caption pushed at `at`, as `push-index` writes one.
    fn pushed(at: i64) -> String {
        format!("#mlib-index v=2\n{{\"pushed_at\":{at},\"sets\":538,\"schema\":6}}")
    }

    /// Captions with ascending ids, which is the order a channel hands them
    /// back in; the ids only ever break a tie.
    fn found<'a>(texts: &[&'a str]) -> Vec<(&'a str, i64)> {
        texts.iter().enumerate().map(|(i, text)| (*text, i as i64)).collect()
    }

    fn message(text: &str) -> CoreError {
        pick_index(&found(&[text])).unwrap_err()
    }

    fn reason(err: &CoreError) -> String {
        err.to_string()
    }

    #[test]
    fn the_one_index_among_other_messages_is_the_one_chosen() {
        assert_eq!(pick_index(&found(&["a note", INDEX, "another note"])).unwrap(), 1);
    }

    /// The prefix, not the exact marker: a snapshot written by a later
    /// uploader is still the index this channel holds.
    #[test]
    fn a_later_snapshot_version_is_still_recognised_as_the_index() {
        assert_eq!(pick_index(&found(&["#mlib-index v=3\n{}"])).unwrap(), 0);
    }

    #[test]
    fn a_channel_holding_no_snapshot_says_so() {
        assert_eq!(reason(&pick_index(&[]).unwrap_err()), format!("library error: {NOTHING_PINNED}"));
    }

    #[test]
    fn pins_that_are_not_an_index_say_so_rather_than_nothing_is_pinned() {
        let err = message("welcome to the channel");
        assert_eq!(reason(&err), format!("library error: {NOT_AN_INDEX}"));
    }

    /// Two machines publishing to one channel is an ordinary state, not an
    /// impasse: the later snapshot is the library, and refusing to choose
    /// between them is how a reader ends up on the older one.
    #[test]
    fn the_later_snapshot_wins_however_the_pins_fell() {
        let (old, new) = (pushed(1_781_568_000), pushed(1_789_946_371));
        assert_eq!(pick_index(&found(&[&old, &new])).unwrap(), 1);
        assert_eq!(pick_index(&found(&[&new, &old])).unwrap(), 0);
    }

    /// A snapshot whose timestamp this build cannot read is still an index,
    /// but it cannot outrank one that says when it was made.
    #[test]
    fn a_dated_snapshot_outranks_an_undated_one() {
        let dated = pushed(1_781_568_000);
        assert_eq!(pick_index(&found(&["#mlib-index v=9", &dated])).unwrap(), 1);
        assert_eq!(pick_index(&found(&[&dated, "#mlib-index v=9"])).unwrap(), 0);
    }

    /// Two snapshots pushed in the same second still resolve the same way on
    /// every device, or two phones disagree about what the library is.
    #[test]
    fn snapshots_of_one_second_are_broken_by_the_later_message() {
        let same = pushed(1_789_946_371);
        assert_eq!(pick_index(&[(&same, 10), (&same, 11)]).unwrap(), 1);
        assert_eq!(pick_index(&[(&same, 11), (&same, 10)]).unwrap(), 0);
    }

    /// Every named channel state, one sentence each. A person reading one has to be
    /// able to tell which of them they are in.
    #[test]
    fn every_named_channel_failure_reads_differently() {
        let messages = [
            reason(&pick_index(&[]).unwrap_err()),
            reason(&message("welcome")),
            reason(&CoreError::Library(UNREADABLE.into())),
            reason(&CoreError::NotAuthorized(NO_LONGER_VISIBLE.into())),
        ];
        for (i, one) in messages.iter().enumerate() {
            for other in &messages[i + 1..] {
                assert_ne!(one, other);
            }
        }
    }

    /// Whatever these sentences say, they may not say it with an id: they
    /// travel to the caller verbatim.
    #[test]
    fn no_channel_failure_message_carries_an_id() {
        let digits = |text: &str| text.chars().filter(char::is_ascii_digit).count();
        assert_eq!(digits(NOTHING_PINNED), 0);
        assert_eq!(digits(NOT_AN_INDEX), 0);
        assert_eq!(digits(UNREADABLE), 0);
        assert_eq!(digits(NO_LONGER_VISIBLE), 0);
    }

    #[test]
    fn a_snapshots_own_timestamp_names_the_version_it_installs() {
        assert_eq!(pushed_at(INDEX, 99), 1_781_568_000);
    }

    #[test]
    fn a_caption_this_build_cannot_parse_still_installs_its_index() {
        assert_eq!(pushed_at("#mlib-index v=9", 99), 99);
        assert_eq!(pushed_at("#mlib-index v=9\nnot json", 99), 99);
        assert_eq!(pushed_at("#mlib-index v=9\n{\"pushed_at\":0}", 99), 99);
    }

    fn rpc(code: i32, name: &str) -> InvocationError {
        InvocationError::Rpc(grammers_mtsender::RpcError {
            code,
            name: name.into(),
            value: None,
            caused_by: None,
        })
    }

    #[test]
    fn a_channel_this_account_cannot_address_is_not_reported_as_a_network_blip() {
        assert_eq!(
            reason(&channel_error(&rpc(400, "CHANNEL_INVALID"))),
            format!("not authorized: {NO_LONGER_VISIBLE}"),
        );
        assert_eq!(
            reason(&channel_error(&rpc(403, "CHANNEL_PRIVATE"))),
            format!("not authorized: {NO_LONGER_VISIBLE}"),
        );
    }

    /// The other half: a server-side wobble must not tell someone they have
    /// lost access to their own channel.
    #[test]
    fn a_server_side_failure_is_reported_as_one() {
        assert!(reason(&channel_error(&rpc(500, "INTERNAL"))).starts_with("network error"));
    }
}
