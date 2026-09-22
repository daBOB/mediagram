//! `mediagram status`: what the library holds, and what is still going in.
//!
//! Read-only, and inferred from the index rather than from any record of a
//! running job: a bulk add is a sequence of `add` calls, and nothing durable
//! says how many are meant to follow. So this answers the questions the index
//! can answer honestly — what is unfinished, and how far each show has got —
//! which is enough to watch an `add-show` from another terminal.

use anyhow::Result;

use crate::config::Config;
use crate::index::db;
use crate::index::label::Named;
use crate::index::progress::{self, ShowProgress, Unfinished};
use crate::upload::lock;
use crate::upload::progress::{self as upload_progress, Progress};

pub async fn run(cfg: &Config) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    // Read-only at the SQLite level: reporting on a library must not migrate
    // it, and least of all while an upload is writing to it.
    let conn = db::open_read_only(&data_dir, "report")?;

    let (sets, bytes) = progress::library(&conn)?;
    let unfinished = progress::unfinished(&conn)?;
    let films = progress::films(&conn)?;
    let shows = progress::shows(&conn)?;
    let courses = progress::courses(&conn)?;
    let now = crate::clock::now_unix();

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

    // Every unfinished set, each under the heading that is true of it. Only
    // one of them can be going up — uploads take turns on `upload.lock` —
    // and calling all of them "uploading" said three files were sharing the
    // line when two were queued behind the first, which is the opposite of
    // what the lock is for.
    //
    // The one that is moving is the one the uploader left a fresh note
    // about: a part is recorded in the index when it lands, and a 3.5 GiB
    // part takes minutes to land, so the index alone cannot tell a set
    // three minutes into its first part from one that has not begun.
    let live = upload_progress::read(&data_dir).filter(|p| p.is_fresh(now));
    // A hint, and only about now: with nobody holding the lock, nothing is
    // uploading and nothing is queued — what is left is waiting for someone
    // to run `resume`.
    let running = lock::is_held(&data_dir);
    for set in &unfinished {
        let moving = live.as_ref().filter(|p| p.set_id == set.set_id);
        println!();
        println!("{:<14} {}", heading(moving.is_some(), running), label(set));
        match moving {
            Some(live) => println!("               {}", live_progress(live, set, now)),
            None => println!("               {}", progress_of(set, now)),
        }
        println!("               {}", set.set_id);
    }

    if !unfinished.is_empty() {
        println!();
        if running {
            println!(
                "               the rest go up as the one ahead finishes, `mediagram remove <id>` discards one"
            );
        } else {
            println!(
                "               `mediagram resume` uploads these, `mediagram remove <id>` discards one"
            );
        }
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
    Named {
        set_id: &set.set_id,
        kind: &set.kind,
        show: set.show.as_deref(),
        title: set.title.as_deref(),
        season: set.season,
        episode: set.episode.as_deref(),
    }
    .label()
}

/// What a set that is not moving has behind it: `2 of 2 parts sent · 3.50
/// of 6.28 GB · added 23 min ago`.
///
/// Counted in parts that landed, not in the part being worked on, because
/// nothing is being worked on here — a set only reaches this line when the
/// uploader's note is about some other set, or there is no uploader. Saying
/// "part 1 of 3" of a queued set claimed a part was in flight that no
/// process had picked up.
///
/// The age is when the set was added, which is all the index records; the
/// set that is actually going up is described by [`live_progress`] instead.
pub fn progress_of(set: &Unfinished, now: i64) -> String {
    let age = ago(now.saturating_sub(set.created_at));
    if set.parts_done == 0 {
        return format!(
            "nothing sent yet · {:.2} GB in {} · added {age}",
            set.bytes_total as f64 / 1e9,
            count(u64::from(set.parts_total), "part")
        );
    }
    format!(
        "{} of {} parts sent · {:.2} of {:.2} GB · added {age}",
        set.parts_done,
        set.parts_total,
        set.bytes_done as f64 / 1e9,
        set.bytes_total as f64 / 1e9,
    )
}

/// The word for what is happening to a set: the one holding the line is
/// uploading, anything else is waiting its turn while an upload runs, and
/// with none running the rest are simply unfinished.
pub fn heading(moving: bool, running: bool) -> &'static str {
    match (moving, running) {
        (true, _) => "uploading",
        (false, true) => "waiting",
        (false, false) => "unfinished",
    }
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

