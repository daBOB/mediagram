//! Finding the index the uploader pinned, and saying plainly when there
//! isn't exactly one.
//!
//! `mediagram push-index` pins each snapshot of `library.db` and unpins the
//! one it replaces, so a healthy channel holds exactly one. The CLI already
//! warns when it finds more, which means more than one is a real state and
//! not an impossible one — every shape below is something a channel can
//! actually be in, and each says something different to whoever has to fix
//! it.

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

pub(super) fn several_pinned(count: usize) -> String {
    format!(
        "That channel has {count} index snapshots pinned, so there is no way \
         to tell which one is current. The next `mediagram push-index` unpins \
         the extras; run it and try again.",
    )
}

/// Which of the pinned captions is the index, or why none of them is.
///
/// Pure, and given only the captions: the decision is the part that has to be
/// right, and it is the part that cannot be exercised against a live channel
/// in a test suite.
pub(super) fn pick_index(captions: &[&str]) -> Result<usize, CoreError> {
    let found: Vec<usize> = captions
        .iter()
        .enumerate()
        .filter(|(_, text)| text.starts_with(INDEX_CAPTION_PREFIX))
        .map(|(idx, _)| idx)
        .collect();

    match found.as_slice() {
        [only] => Ok(*only),
        [] if captions.is_empty() => Err(CoreError::Library(NOTHING_PINNED.into())),
        [] => Err(CoreError::Library(NOT_AN_INDEX.into())),
        several => Err(CoreError::Library(several_pinned(several.len()))),
    }
}

/// When the snapshot behind `caption` was pushed, for naming the catalogue
/// version it installs. A caption this build cannot parse is not a reason to
/// refuse a readable index, so the fallback is simply "now".
pub(super) fn pushed_at(caption: &str, now: i64) -> i64 {
    caption
        .split_once('\n')
        .and_then(|(_, json)| serde_json::from_str::<IndexCaption>(json.trim()).ok())
        .map(|parsed| parsed.pushed_at)
        .filter(|pushed| *pushed > 0)
        .unwrap_or(now)
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

    fn message(text: &str) -> CoreError {
        pick_index(&[text]).unwrap_err()
    }

    fn reason(err: &CoreError) -> String {
        err.to_string()
    }

    #[test]
    fn one_pinned_index_is_the_one_chosen() {
        assert_eq!(pick_index(&["a note", INDEX, "another note"]).unwrap(), 1);
    }

    /// The prefix, not the exact marker: a snapshot written by a later
    /// uploader is still the index this channel holds.
    #[test]
    fn a_later_snapshot_version_is_still_recognised_as_the_index() {
        assert_eq!(pick_index(&["#mlib-index v=3\n{}"]).unwrap(), 0);
    }

    #[test]
    fn an_empty_pin_list_says_nothing_is_pinned() {
        assert_eq!(reason(&pick_index(&[]).unwrap_err()), format!("library error: {NOTHING_PINNED}"));
    }

    #[test]
    fn pins_that_are_not_an_index_say_so_rather_than_nothing_is_pinned() {
        let err = message("welcome to the channel");
        assert_eq!(reason(&err), format!("library error: {NOT_AN_INDEX}"));
    }

    /// `push-index` resolves this and the CLI warns about it, so it is a real
    /// state a real channel reaches — and the one place where guessing would
    /// silently hand back an out-of-date library.
    #[test]
    fn more_than_one_pinned_index_is_refused_rather_than_guessed_between() {
        let err = pick_index(&[INDEX, INDEX]).unwrap_err();
        assert_eq!(reason(&err), format!("library error: {}", several_pinned(2)));
        assert!(reason(&err).contains("push-index"), "say how to fix it");
    }

    /// Four channel states, four sentences. A person reading one has to be
    /// able to tell which of them they are in.
    #[test]
    fn every_named_channel_failure_reads_differently() {
        let messages = [
            reason(&pick_index(&[]).unwrap_err()),
            reason(&message("welcome")),
            reason(&pick_index(&[INDEX, INDEX]).unwrap_err()),
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
        // The count is the only number a person needs here, and it is a
        // count of pins, not an identifier of anything.
        assert_eq!(digits(&several_pinned(2)), 1);
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
