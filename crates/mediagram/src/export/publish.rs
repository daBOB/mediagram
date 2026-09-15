//! Handing a finished file to the operator's own upload tool.
//!
//! The command is an argv array, run directly. Never a shell: a POSIX shell
//! reports the exit status of the last element of a pipeline, and `pipefail`
//! is off by default, so `rclone copy … | tee log` would report success for a
//! failed upload and the pointer would be published for an archive that is
//! not there.
//!
//! The child does not inherit `MEDIAGRAM_*` variables. An upload tool needs
//! its own storage credentials, not this process's Telegram hash or TMDB key.

use std::path::Path;

use anyhow::{Context, Result, bail};
use tokio::process::Command;

/// The token replaced with the file being published.
const FILE_TOKEN: &str = "{file}";

/// Replaces `{file}` in every argument. Paths stay single arguments, so
/// spaces and quotes need no escaping and cannot split a command.
pub fn substitute(argv: &[String], file: &Path) -> Vec<String> {
    let path = file.display().to_string();
    argv.iter()
        .map(|arg| arg.replace(FILE_TOKEN, &path))
        .collect()
}

/// Runs the publish command for one file, failing the run if it does.
pub async fn run_publish(argv: &[String], file: &Path) -> Result<()> {
    let args = substitute(argv, file);
    let Some((program, rest)) = args.split_first() else {
        bail!(
            "publish_cmd is empty; it must name a program, e.g. [\"rclone\", \"copy\", \"{{file}}\", \"r2:bucket/\"]"
        );
    };

    let mut command = Command::new(program);
    command.args(rest);
    for (key, _) in std::env::vars() {
        if key.starts_with("MEDIAGRAM_") {
            command.env_remove(key);
        }
    }

    let status = command
        .status()
        .await
        .with_context(|| format!("running publish command `{program}`"))?;
    if !status.success() {
        bail!("publish command `{program}` exited with {status}");
    }
    Ok(())
}
