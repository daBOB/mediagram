//! The episodes a series folder holds, read from its file names.

use std::path::{Path, PathBuf};

use anyhow::{Result, bail};

use crate::paths::file_name;

/// One file, and the episode it will be filed as.
pub struct Episode {
    pub path: PathBuf,
    pub season: u32,
    pub episode: u32,
}

/// Every video file under `dir` that names a season and an episode.
///
/// The numbering comes from the file name, which is the one part of these
/// names that release prefixes leave alone: `mlib_spec` reads S01E02 out of
/// them correctly even where it reads the title badly.
pub fn walk(dir: &Path) -> Result<Vec<Episode>> {
    if !dir.is_dir() {
        bail!("{} is not a directory", dir.display());
    }
    let files = crate::media::video_files::collect_videos(dir)?;

    let mut episodes = Vec::new();
    for path in files {
        let Some(guess) = mlib_spec::filename::parse_filename(&file_name(&path)) else {
            continue;
        };
        match (guess.season, guess.episode) {
            (Some(season), Some(episode)) => episodes.push(Episode {
                path,
                season,
                episode,
            }),
            // A file with no episode number is not an episode: a trailer or
            // an extra, and filing it under a guessed number would be worse
            // than leaving it out.
            _ => println!("skipping {} — no season/episode in the name", file_name(&path)),
        }
    }
    Ok(episodes)
}

/// The first episode number claimed by more than one file, with their names.
pub fn duplicate_episode(episodes: &[Episode]) -> Option<(u32, u32, Vec<String>)> {
    for (i, ep) in episodes.iter().enumerate() {
        let same: Vec<String> = episodes[i..]
            .iter()
            .filter(|other| other.season == ep.season && other.episode == ep.episode)
            .map(|other| file_name(&other.path))
            .collect();
        if same.len() > 1 {
            return Some((ep.season, ep.episode, same));
        }
    }
    None
}
