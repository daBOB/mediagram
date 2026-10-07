//! A documentary collection, e.g. "Terra X": every video becomes a
//! `Kind::Docu` episode, numbered and grouped exactly as `add-course` numbers
//! and groups a course's lessons, because it is `add-course`'s own
//! [`run_collection`] that walks and uploads it.

use anyhow::Result;
use mlib_spec::Kind;

use super::AddDocuArgs;
use crate::commands::add_course::run_collection;
use crate::commands::args::AddCourseArgs;
use crate::config::Config;

pub(super) async fn run(cfg: &Config, args: AddDocuArgs) -> Result<()> {
    let AddDocuArgs {
        path,
        title,
        cid,
        dry_run,
        no_push,
        variant,
        no_remux,
        category,
    } = args;
    let args = AddCourseArgs {
        dir: path,
        course: title,
        cid,
        dry_run,
        no_push,
        variant,
        no_remux,
        category,
    };
    run_collection(cfg, args, Kind::Docu).await
}

#[cfg(test)]
#[path = "collection_tests.rs"]
mod tests;
