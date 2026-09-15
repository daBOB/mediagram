//! The archive half of the package: a gzipped tar of the staging directory.
//!
//! The manifest is written first so a reader can identify a package without
//! unpacking the rest, and unpacking refuses any member that would land
//! outside the target directory.

use std::io::Write;
use std::path::{Component, Path, PathBuf};

use anyhow::{Context, Result, bail};
use flate2::Compression;
use flate2::write::GzEncoder;

const MANIFEST: &str = "manifest.json";

/// Packs every file under `dir` into a gzipped tar, manifest first.
pub fn pack_dir(dir: &Path) -> Result<Vec<u8>> {
    let mut files = collect_files(dir, dir)?;
    // Manifest first, then a stable order so two packs of one tree agree.
    files.sort_by(|a, b| {
        let rank = |p: &PathBuf| u8::from(p.to_str() != Some(MANIFEST));
        rank(a).cmp(&rank(b)).then_with(|| a.cmp(b))
    });

    let encoder = GzEncoder::new(Vec::new(), Compression::default());
    let mut builder = tar::Builder::new(encoder);
    for relative in files {
        let full = dir.join(&relative);
        let data = std::fs::read(&full)
            .with_context(|| format!("reading {} for the archive", full.display()))?;
        let mut header = tar::Header::new_gnu();
        header.set_size(data.len() as u64);
        header.set_mode(0o644);
        header.set_mtime(0);
        header.set_uid(0);
        header.set_gid(0);
        header.set_cksum();
        builder
            .append_data(&mut header, &relative, &data[..])
            .with_context(|| format!("adding {} to the archive", relative.display()))?;
    }
    let encoder = builder.into_inner().context("finishing the tar")?;
    encoder.finish().context("finishing the gzip stream")
}

/// Unpacks an archive, refusing any member whose path is absolute, contains
/// `..`, or is a link. A package is decrypted before it reaches here, so the
/// bytes are authentic, but authentic is not the same as well-formed.
pub fn unpack_to(packed: &[u8], dest: &Path) -> Result<()> {
    let decoder = flate2::read::GzDecoder::new(packed);
    let mut archive = tar::Archive::new(decoder);
    for entry in archive.entries().context("reading the archive")? {
        let mut entry = entry.context("reading an archive member")?;
        let path = entry
            .path()
            .context("member has no usable path")?
            .to_path_buf();
        check_member_path(&path)?;
        if !entry.header().entry_type().is_file() {
            bail!("archive member {} is not a regular file", path.display());
        }
        let target = dest.join(&path);
        if let Some(parent) = target.parent() {
            std::fs::create_dir_all(parent)
                .with_context(|| format!("creating {}", parent.display()))?;
        }
        let mut out = std::fs::File::create(&target)
            .with_context(|| format!("creating {}", target.display()))?;
        std::io::copy(&mut entry, &mut out)
            .with_context(|| format!("writing {}", target.display()))?;
        out.flush().ok();
    }
    Ok(())
}

fn check_member_path(path: &Path) -> Result<()> {
    for component in path.components() {
        match component {
            Component::Normal(_) => {}
            _ => bail!(
                "archive member {} escapes the target directory",
                path.display()
            ),
        }
    }
    Ok(())
}

fn collect_files(root: &Path, dir: &Path) -> Result<Vec<PathBuf>> {
    let mut out = Vec::new();
    let entries = std::fs::read_dir(dir).with_context(|| format!("listing {}", dir.display()))?;
    for entry in entries {
        let entry = entry.context("reading a directory entry")?;
        let path = entry.path();
        if path.is_dir() {
            out.extend(collect_files(root, &path)?);
        } else {
            let relative = path
                .strip_prefix(root)
                .context("staging path outside the staging directory")?;
            out.push(relative.to_path_buf());
        }
    }
    Ok(out)
}
