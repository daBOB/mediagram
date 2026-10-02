//! What `aapt2 dump badging` says about an APK: the package, versionCode and
//! versionName Android itself will read when it installs it.

use std::path::Path;

use anyhow::{Context, Result, bail};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Badging {
    pub package: String,
    pub version_code: i64,
    pub version_name: String,
}

/// Runs `aapt2 dump badging` on `apk`. `aapt2` comes from the Android SDK's
/// build-tools; `scripts/release-android.sh` puts them on `PATH`.
pub fn read(apk: &Path) -> Result<Badging> {
    let output = std::process::Command::new("aapt2")
        .args(["dump", "badging"])
        .arg(apk)
        .output()
        .context("running aapt2 (put the Android SDK's build-tools on PATH)")?;
    if !output.status.success() {
        bail!("aapt2 could not read {}: {}", apk.display(), String::from_utf8_lossy(&output.stderr));
    }
    parse(&String::from_utf8_lossy(&output.stdout))
}

/// The first `package:` line of aapt2's output. Keys are matched with a
/// leading space, so `versionName` never matches inside
/// `platformBuildVersionName`, nor `name` inside `compileSdkVersionCodename`.
pub fn parse(output: &str) -> Result<Badging> {
    let line = output
        .lines()
        .find(|line| line.starts_with("package:"))
        .context("aapt2 printed no package line")?;
    let field = |key: &str| -> Result<String> {
        let marker = format!(" {key}='");
        let start = line.find(&marker).with_context(|| format!("no {key} in: {line}"))? + marker.len();
        let end = line[start..].find('\'').with_context(|| format!("unterminated {key} in: {line}"))?;
        Ok(line[start..start + end].to_string())
    };
    Ok(Badging {
        package: field("name")?,
        version_code: field("versionCode")?.parse().context("versionCode is not a number")?,
        version_name: field("versionName")?,
    })
}

#[cfg(test)]
#[path = "badging_tests.rs"]
mod tests;
