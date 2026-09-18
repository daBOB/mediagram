//! `mediagram status`: what the library holds, and what is still going in.
//!
//! Read-only, and inferred from the index rather than from any record of a
//! running job: a bulk add is a sequence of `add` calls, and nothing durable
//! says how many are meant to follow. So this answers the questions the index
//! can answer honestly — what is unfinished, and how far each show has got —
//! which is enough to watch an `add-show` from another terminal.

use std::time::{SystemTime, UNIX_EPOCH};

use anyhow::{Context, Result, bail};

use crate::config::Config;
use crate::index::progress::{self, ShowProgress, Unfinished};
use crate::upload::progress::{self as upload_progress, Progress};

pub async fn run(cfg: &Config) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    let live = data_dir.join("library.db");
    if !live.exists() {
        bail!("no library.db in {}; nothing to report", data_dir.display());
    }
    // Read-only at the SQLite level: reporting on a library must not migrate
    // it, and least of all while an upload is writing to it.
    let conn = rusqlite::Connection::open_with_flags(
        &live,
        rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_URI,
    )
    .with_context(|| format!("opening {} read-only", live.display()))?;

    let (sets, bytes) = progress::library(&conn)?;
    let unfinished = progress::unfinished(&conn)?;
    let films = progress::films(&conn)?;
    let shows = progress::shows(&conn)?;
    let courses = progress::courses(&conn)?;
    let now = now_unix();

    println!(
        "library        {} · {:.1} GB in the channel",
        count(sets, "set"),
        bytes as f64 / 1e9
    );

    if !films.is_empty() {
        println!();
        for (n, film) in films.iter().enumerate() {
            let heading = if n == 0 { "films" } else { "" };
            match film.year {
                Some(year) => println!("{heading:<14} {}  {year}", film.title),
                None => println!("{heading:<14} {}", film.title),
            }
        }
    }

    if !shows.is_empty() {
        println!();
        let width = shows
            .iter()
            .map(|s| s.show.chars().count())
            .max()
            .unwrap_or(0);
        for (n, show) in shows.iter().enumerate() {
            let heading = if n == 0 { "shows" } else { "" };
            println!("{heading:<14} {:<width$}  {}", show.show, episodes_of(show));
        }
    }

    if !courses.is_empty() {
        println!();
        let width = courses
            .iter()
            .map(|c| c.name.chars().count())
            .max()
            .unwrap_or(0);
        for (n, course) in courses.iter().enumerate() {
            let heading = if n == 0 { "courses" } else { "" };
            println!(
                "{heading:<14} {:<width$}  {}",
                course.name,
                count(u64::from(course.lessons), "lesson")
            );
        }
    }

    // Every unfinished set, said the same way. Splitting them by whether a
    // part had finished put an upload three minutes into its first part —
    // a 3.5 GiB part takes about seven — under a heading that said none of
    // it had been sent, which reads as idle when it is working hardest.
    // The uploader leaves a note saying how far it has got inside the part
    // it is on, which the index cannot know: a part is recorded when it
    // lands, and a 3.5 GiB part takes minutes to land.
    let live = upload_progress::read(&data_dir).filter(|p| p.is_fresh(now));
    for set in &unfinished {
        println!();
        println!("uploading      {}", label(set));
        match live.as_ref().filter(|p| p.set_id == set.set_id) {
            Some(live) => {
                println!("               {}", live_progress(live, set, now));
            }
            None => println!("               {}", progress_of(set, now)),
        }
        println!("               {}", set.set_id);
    }

    if !unfinished.is_empty() {
        println!();
        println!(
            "               `mediagram resume` uploads these, `mediagram remove <id>` discards one"
        );
    }
    Ok(())
}

/// What the uploader itself says, which moves while you watch.
pub fn live_progress(live: &Progress, set: &Unfinished, now: i64) -> String {
    let sent = live.set_bytes_sent();
    let percent = if live.set_bytes > 0 {
        sent as f64 / live.set_bytes as f64 * 100.0
    } else {
        0.0
    };
    format!(
        "part {} of {} · {:.2} of {:.2} GB sent ({percent:.0}%) · started {}",
        live.part + 1,
        live.parts,
        sent as f64 / 1e9,
        live.set_bytes as f64 / 1e9,
        ago(now.saturating_sub(set.created_at))
    )
}

/// `1 set`, `2 sets` — the noun agreeing with the number beside it.
pub fn count(n: u64, noun: &str) -> String {
    if n == 1 {
        format!("{n} {noun}")
    } else {
        format!("{n} {noun}s")
    }
}

/// `6 of 8 episodes`, or just the count when nobody has said how many exist.
pub fn episodes_of(show: &ShowProgress) -> String {
    // The noun agrees with the number beside it, which in `1 of 8` is the
    // eight.
    let noun = |n: u32| if n == 1 { "episode" } else { "episodes" };
    match show.total {
        // Holding more than the provider counted is ordinary — specials and
        // double episodes — and is not a shortfall worth reporting.
        Some(total) if total > show.held => {
            format!("{} of {total} {}", show.held, noun(total))
        }
        _ => format!("{} {}", show.held, noun(show.held)),
    }
}

/// What a set is, in the words the shelf uses.
pub fn label(set: &Unfinished) -> String {
    let mut parts = Vec::new();
    if let Some(show) = &set.show {
        parts.push(show.clone());
    }
    if let (Some(season), Some(episode)) = (set.season, set.episode.as_deref()) {
        parts.push(format!("S{season:02}E{:0>2}", episode.trim_matches('"')));
    }
    if let Some(title) = &set.title {
        parts.push(title.clone());
    }
    if parts.is_empty() {
        parts.push(set.set_id.clone());
    }
    parts.join("  ")
}

/// `part 2 of 2 · 3.50 of 6.28 GB sent · started 23 min ago`.
///
/// Named by the part being worked on rather than by the parts finished: for
/// most of a part's life nothing has finished, and "0 of 2 parts" says the
/// upload has done nothing when it is most of the way through the first.
pub fn progress_of(set: &Unfinished, now: i64) -> String {
    let in_flight = (set.parts_done + 1).min(set.parts_total.max(1));
    format!(
        "part {in_flight} of {} · {:.2} of {:.2} GB sent · started {}",
        set.parts_total,
        set.bytes_done as f64 / 1e9,
        set.bytes_total as f64 / 1e9,
        ago(now.saturating_sub(set.created_at))
    )
}

/// How long ago, in the largest unit that still says something.
pub fn ago(seconds: i64) -> String {
    match seconds {
        s if s < 0 => "just now".to_string(),
        s if s < 90 => format!("{s} sec ago"),
        s if s < 5400 => format!("{} min ago", (s + 30) / 60),
        s if s < 172_800 => format!("{} hours ago", (s + 1800) / 3600),
        s => format!("{} days ago", (s + 43_200) / 86_400),
    }
}

fn now_unix() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}
