//! `mediagram add-audiobook`: upload MP3 audiobooks, given as files or as
//! folders of them, each as its own set with its embedded cover and genre.
//!
//! The file is never remuxed or transcoded: an MP3 is already what every
//! player reads. It is deleted only with `--delete-source`, once its upload
//! is complete.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail, ensure};
use mlib_spec::Kind;
use mlib_spec::package::title_art_key;

use super::args::AddAudiobookArgs;
use crate::config::Config;
use crate::edit::category;
use crate::index::{artwork, db};
use crate::media::audiobook_tags::{self, AudiobookTags};
use crate::upload::new_set::NewSet;
use crate::upload::session::link::TelegramLink;
use crate::upload::session::{Item, Outcome, Session, Set, Step};

pub async fn run(cfg: &Config, args: AddAudiobookArgs) -> Result<()> {
    let mut files = Vec::new();
    for path in &args.paths {
        files.extend(mp3_files(path)?);
    }
    ensure!(!files.is_empty(), "no .mp3 files in the paths given");
    let mut books = Vec::with_capacity(files.len());
    for file in files {
        let tags = audiobook_tags::read(&file)
            .await
            .with_context(|| format!("reading {}", file.display()))?;
        books.push((file, tags));
    }

    if args.dry_run {
        for (_, tags) in &books {
            let cover = if tags.cover.is_some() { "yes" } else { "no" };
            println!(
                "{} | {} | cover: {cover}",
                tags.title,
                tags.genre.as_deref().unwrap_or("-")
            );
        }
        println!("{} audiobooks", books.len());
        return Ok(());
    }

    // Written before the upload and for a held book too, so a re-run
    // refreshes the shelf and the cover.
    let conn = db::open(&cfg.data_dir()?)?;
    for (file, tags) in &books {
        record_shelf_and_cover(&conn, file, tags).await?;
    }
    drop(conn);

    let items = books.iter().map(|(file, tags)| Item {
        tag: tags.title.clone(),
        set: Set::File(NewSet {
            file: file.clone(),
            standalone: Some(Kind::Book),
            standalone_title: Some(tags.title.clone()),
            ..NewSet::default()
        }),
        delete_source: args.delete_source.then(|| file.clone()),
    });
    let mut session = Session::new(cfg, TelegramLink::new(cfg))?;
    let counts = session
        .upload(items, |title, step| match step {
            Step::Start => println!("uploading {title}"),
            Step::End(Outcome::AlreadyHeld) if args.delete_source => {
                println!("{title} already uploaded; file kept")
            }
            Step::End(Outcome::AlreadyHeld) => println!("{title} already uploaded"),
            Step::End(Outcome::Pending) => println!("{title} pending; run `mediagram resume`"),
            Step::End(Outcome::Failed(err) | Outcome::Blocked(err)) => {
                println!("  {title}: {err:#}");
            }
            Step::End(Outcome::Uploaded) => {}
        })
        .await;
    let failed = counts.failed + counts.blocked;
    println!(
        "\n{} uploaded, {} already held, {failed} failed",
        counts.uploaded, counts.held
    );
    if counts.pending > 0 {
        println!(
            "{} pending; run mediagram resume to finish them",
            counts.pending
        );
    }
    session.end(args.no_push).await?;
    ensure!(failed == 0, "{failed} audiobooks were not uploaded");
    Ok(())
}

/// The genre as the book's shelf and the embedded picture as its poster.
/// A genre the shelves refuse costs the book its shelf, not the upload.
async fn record_shelf_and_cover(
    conn: &rusqlite::Connection,
    file: &Path,
    tags: &AudiobookTags,
) -> Result<()> {
    if let Some(genre) = &tags.genre {
        match category::planned(Kind::Book, Some(&tags.title), genre) {
            Ok(planned) => category::write(conn, &planned)?,
            Err(err) => eprintln!("{}: genre {genre:?} not used: {err:#}", tags.title),
        }
    }
    if let (Some(cover), Some(key)) = (&tags.cover, title_art_key(&tags.title)) {
        let bytes = audiobook_tags::cover_bytes(file, cover).await?;
        artwork::put(conn, &key, cover.mime, &bytes)?;
    }
    Ok(())
}

/// `path` itself when it is a file, else every `.mp3` below it, sorted.
fn mp3_files(path: &Path) -> Result<Vec<PathBuf>> {
    if path.is_file() {
        ensure!(is_mp3(path), "{} is not an .mp3 file", path.display());
        return Ok(vec![path.to_path_buf()]);
    }
    if !path.is_dir() {
        bail!("{} is neither a file nor a directory", path.display());
    }
    let mut found = Vec::new();
    let mut pending = vec![path.to_path_buf()];
    while let Some(dir) = pending.pop() {
        for entry in
            std::fs::read_dir(&dir).with_context(|| format!("reading {}", dir.display()))?
        {
            let entry = entry?.path();
            if entry.is_dir() {
                pending.push(entry);
            } else if is_mp3(&entry) {
                found.push(entry);
            }
        }
    }
    found.sort();
    Ok(found)
}

fn is_mp3(path: &Path) -> bool {
    path.extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("mp3"))
}

#[cfg(test)]
#[path = "add_audiobook_tests.rs"]
mod tests;
