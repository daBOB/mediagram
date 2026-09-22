//! Running this binary again, detached, so an upload outlives the terminal
//! that started it.
//!
//! `add` does everything that can ask a question or refuse — inspecting,
//! resolving, planning, writing the index rows — in the foreground, and only
//! then hands the bytes over. So what is backgrounded is the part that can
//! take an hour and has nothing left to say.

use std::ffi::OsString;
use std::fs::OpenOptions;
use std::os::unix::process::CommandExt;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use anyhow::{Context, Result};

use crate::config::Config;

/// Where a detached upload's output goes, inside the data directory.
const LOG_NAME: &str = "background.log";

/// What was started, for the line that tells the user where it went.
pub struct Started {
    pub pid: u32,
    pub log: PathBuf,
}

/// Starts `finish-set` for `set_id` in a session of its own.
pub fn spawn_finish_set(
    cfg: &Config,
    set_id: &str,
    delete: Option<&Path>,
    no_push: bool,
) -> Result<Started> {
    let data_dir = cfg.data_dir()?;
    let log_path = data_dir.join(LOG_NAME);
    let log = OpenOptions::new()
        .create(true)
        .append(true)
        .open(&log_path)
        .with_context(|| format!("opening {}", log_path.display()))?;
    let errors = log.try_clone().context("duplicating the log handle")?;

    let exe = std::env::current_exe().context("finding this binary")?;
    let mut command = Command::new(exe);
    command
        .args(child_args(
            cfg.loaded_from.as_deref(),
            set_id,
            delete,
            no_push,
        ))
        .stdin(Stdio::null())
        .stdout(log)
        .stderr(errors);
    // A session of its own. Closing the terminal sends SIGHUP to the
    // foreground process group, and an upload that dies with the window it
    // was started from is not a background upload.
    // SAFETY: the closure runs in the forked child before exec, where only
    // async-signal-safe calls are allowed; `setsid` is one, and the closure
    // touches no memory the parent's other threads could be holding.
    unsafe {
        command.pre_exec(|| match libc::setsid() {
            -1 => Err(std::io::Error::last_os_error()),
            _ => Ok(()),
        });
    }

    let child = command
        .spawn()
        .context("starting the background upload")?;
    Ok(Started {
        pid: child.id(),
        log: log_path,
    })
}

/// The child's argv. Pure, so what the background process is told to do can
/// be checked without starting one.
///
/// `--config` is passed on explicitly when this process was given one:
/// `MEDIAGRAM_*` overrides reach the child through the environment it
/// inherits, but the path to the file does not.
fn child_args(
    config: Option<&Path>,
    set_id: &str,
    delete: Option<&Path>,
    no_push: bool,
) -> Vec<OsString> {
    let mut args: Vec<OsString> = Vec::new();
    if let Some(path) = config {
        args.push("--config".into());
        args.push(path.as_os_str().to_owned());
    }
    args.push("finish-set".into());
    args.push(set_id.into());
    if let Some(path) = delete {
        args.push("--delete".into());
        args.push(path.as_os_str().to_owned());
    }
    if no_push {
        args.push("--no-push".into());
    }
    args
}

/// The path a caller can point the user at, without starting anything.
pub fn log_path(data_dir: &Path) -> PathBuf {
    data_dir.join(LOG_NAME)
}

#[cfg(test)]
mod tests {
    use std::ffi::OsStr;

    use super::*;

    fn strings(args: &[OsString]) -> Vec<&OsStr> {
        args.iter().map(OsString::as_os_str).collect()
    }

    #[test]
    fn the_child_is_told_which_set_to_finish() {
        let args = child_args(None, "01ABC", None, false);
        assert_eq!(strings(&args), ["finish-set", "01ABC"]);
    }

    #[test]
    fn the_config_path_and_the_flags_are_carried_over() {
        let args = child_args(
            Some(Path::new("/etc/mediagram.toml")),
            "01ABC",
            Some(Path::new("/films/A Film.mkv")),
            true,
        );
        assert_eq!(
            strings(&args),
            [
                "--config",
                "/etc/mediagram.toml",
                "finish-set",
                "01ABC",
                "--delete",
                "/films/A Film.mkv",
                "--no-push",
            ]
        );
    }

    #[test]
    fn a_path_stays_one_argument_however_it_is_spelled() {
        // argv, not a shell string: spaces and quotes in a file name reach
        // the child exactly as they are.
        let odd = Path::new(r#"/films/Andre's "cut" 1993.mkv"#);
        let args = child_args(None, "01ABC", Some(odd), false);
        assert_eq!(args.len(), 4);
        assert_eq!(args[3], odd.as_os_str());
    }
}
