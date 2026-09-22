//! `mediagram status`: what the library holds, and what is still going in.
//!
//! Read-only, and inferred from the index rather than from any record of a
//! running job: a bulk add is a sequence of `add` calls, and nothing durable
//! says how many are meant to follow. So this answers the questions the index
//! can answer honestly — what is unfinished, and how far each show has got —
//! which is enough to watch an `add-show` from another terminal.

pub mod text;

use anyhow::Result;

use crate::config::Config;
use crate::index::db;
use crate::index::progress;
use crate::upload::lock;
use crate::upload::progress as upload_progress;
use text::{count, episodes_of, heading, label, live_progress, progress_of};

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

