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

use std::ffi::OsString;
use std::path::Path;

use anyhow::{Context, Result, bail};
use tokio::process::Command;

use crate::config::Config;
use crate::export::{latest, pointer};

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

/// The environment the publish command runs with: the one given, minus every
/// `MEDIAGRAM_*` variable.
pub fn child_env(
    vars: impl IntoIterator<Item = (OsString, OsString)>,
) -> Vec<(OsString, OsString)> {
    vars.into_iter()
        .filter(|(key, _)| !key.to_string_lossy().starts_with("MEDIAGRAM_"))
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
    command
        .args(rest)
        .env_clear()
        .envs(child_env(std::env::vars_os()));

    let status = command
        .status()
        .await
        .with_context(|| format!("running publish command `{program}`"))?;
    if !status.success() {
        bail!("publish command `{program}` exited with {status}");
    }
    Ok(())
}

/// Publishes the archive, then the pointer that names it. The order is a
/// correctness property: a reader must never find a pointer to a file that is
/// not there yet. A reader arriving mid-publish sees the previous pointer and
/// the previous archive, which is still present.
pub async fn publish_package(
    cfg: &Config,
    draft: &mlib_spec::package::LatestPointer,
    package: &Path,
    bytes: u64,
    sealed: &[u8],
    dry_run: bool,
) -> Result<()> {
    let argv = cfg.publish_cmd.clone().unwrap_or_default();
    let base_url = cfg.publish_base_url.as_deref().unwrap_or("");
    let file_name = package
        .file_name()
        .and_then(|n| n.to_str())
        .context("package has no usable file name")?;

    let digest = hex::encode(pointer::sha256(sealed));
    let complete = latest::complete(draft, file_name, base_url, bytes, &digest);
    let pointer_path = package.with_file_name("latest.json");
    std::fs::write(&pointer_path, serde_json::to_vec(&complete)?)
        .with_context(|| format!("writing {}", pointer_path.display()))?;

    if dry_run {
        for file in [package, pointer_path.as_path()] {
            println!("would run: {:?}", substitute(&argv, file));
        }
        return Ok(());
    }

    run_publish(&argv, package).await?;
    run_publish(&argv, &pointer_path).await?;

    println!("published {}", complete.url);
    println!(
        "pointer at {}/latest.json is the URL the player needs",
        base_url.trim_end_matches('/')
    );
    Ok(())
}
