//! Which episode, or pair of episodes, a file holds.

use mlib_spec::Episode;
use mlib_spec::filename::Guess;

use super::resolve::ResolveInput;

/// Explicit `--episode` wins over the filename guess; a guessed end episode
/// only applies when the flag didn't override the start episode.
///
/// A flag naming the same start episode the file does is not an override.
/// `add-show` always passes the number it read from the name, so treating
/// that as one filed `S09E19E20` as E19 alone and left E20 reported missing.
pub(super) fn episode_value(input: &ResolveInput, guess: &Guess) -> Option<Episode> {
    let first = input.episode.or(guess.episode)?;
    match guess.episode_end {
        Some(end) if guess.episode == Some(first) && end > first => {
            Some(Episode::Range([first, end]))
        }
        _ => Some(Episode::Single(first)),
    }
}

#[cfg(test)]
#[path = "episode_value_tests.rs"]
mod tests;
