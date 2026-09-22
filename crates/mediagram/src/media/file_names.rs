//! What a file's name says: whether it is a video or a document, and the
//! number and title a person wrote into it.
//!
//! Pure string rules, shared by everything that walks a folder — a course, a
//! show, a single file handed to `add` — so they read one name one way.

/// A leading integer, and whatever readable text follows it.
///
/// `01 Getting Started` is chapter 1 titled "Getting Started"; `Appendix` has
/// no number and is titled "Appendix"; `01` is a number with no title.
pub fn split_number_and_title(name: &str) -> (Option<u32>, Option<String>) {
    let trimmed = name.trim();
    let digits: String = trimmed.chars().take_while(char::is_ascii_digit).collect();
    let rest = &trimmed[digits.len()..];
    let number = digits.parse::<u32>().ok();
    let title = clean_title(rest);
    (number, title)
}

/// Strips separators an encoder or a download tool left behind, so a title
/// reads as a person would write it.
fn clean_title(raw: &str) -> Option<String> {
    let replaced: String = raw
        .chars()
        .map(|c| if matches!(c, '_' | '.' | '-') { ' ' } else { c })
        .collect();
    let collapsed = replaced.split_whitespace().collect::<Vec<_>>().join(" ");
    (!collapsed.is_empty()).then_some(collapsed)
}

/// A file name without its extension, which is what carries the number and
/// title. Leaving the extension on would make `01.mp4` look like a title.
pub fn stem(file_name: &str) -> &str {
    file_name.rsplit_once('.').map_or(file_name, |(s, _)| s)
}

/// Extensions treated as lesson video. Anything that is neither this nor a
/// document is subtitles, artwork or a transcriber's working file, and is
/// not uploaded.
pub const VIDEO_EXTENSIONS: &[&str] = &["mkv", "mp4", "m4v", "webm", "mov", "avi", "ts"];

/// Marker the faststart remux puts in its output name. Those files sit beside
/// the source and a crashed run leaves them behind, so a walk that counted
/// them would upload the same video twice and shift every later lesson
/// number — and a lesson number is half of its identity.
pub const REMUX_MARKER: &str = ".faststart.";

pub fn is_video(name: &str) -> bool {
    has_extension(name, VIDEO_EXTENSIONS)
}

/// Extensions uploaded as a course document rather than ignored.
///
/// PDF alone, deliberately. A course folder also holds subtitles, artwork and
/// a transcriber's working files, none of which anyone wants as a row in a
/// player, and every format added here is a format the player then has to
/// know how to offer.
pub const DOC_EXTENSIONS: &[&str] = &["pdf"];

pub fn is_document(name: &str) -> bool {
    has_extension(name, DOC_EXTENSIONS)
}

fn has_extension(name: &str, extensions: &[&str]) -> bool {
    if name.contains(REMUX_MARKER) {
        return false;
    }
    match name.rsplit_once('.') {
        Some((_, ext)) => extensions.contains(&ext.to_ascii_lowercase().as_str()),
        None => false,
    }
}
