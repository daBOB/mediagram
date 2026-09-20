//! `mediagram export-package`: assemble the prebuilt metadata package, then
//! archive and encrypt it. Publishing is opt-in and happens afterwards.
//!
//! The command never writes to `library.db`, including in `--dry-run`: it
//! copies the index through the side-effect-free path and reads everything
//! else from that copy.

use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use anyhow::{Context, Result, bail};
use mlib_spec::package::{PackageManifest, package_file_name};
use rusqlite::Connection;

use crate::config::Config;
use crate::export::budget::{Verdict, estimate_bytes, verdict_for};
use crate::export::encrypt::{parse_key, seal};
use mediagram_tmdb::posters::resolve_posters;
use crate::export::stage::Staging;
use crate::export::{archive, latest, pointer, publish};
use mediagram_tmdb::tmdb_client::TmdbClient;

pub async fn run(
    cfg: &Config,
    out: Option<PathBuf>,
    dry_run: bool,
    publish_it: bool,
) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    let key = package_key(cfg)?;
    // Checked before any work: finding the command missing after a full
    // export would waste the run.
    if publish_it && cfg.publish_cmd.as_ref().is_none_or(Vec::is_empty) {
        bail!(
            "--publish needs publish_cmd in config.toml, e.g. \
             publish_cmd = [\"rclone\", \"copy\", \"{{file}}\", \"r2:mediagram/\"]"
        );
    }

    // A plain connection, not `index::db::open`: opening through the helper
    // would replay migrations and rewrite the recorded schema version, and an
    // export must leave the index exactly as it found it.
    let live = data_dir.join("library.db");
    if !live.exists() {
        bail!("no library.db in {}; nothing to export", data_dir.display());
    }
    // Read-only at the SQLite level, not merely by convention: the export
    // must be incapable of writing to the index, not just careful not to.
    let conn = Connection::open_with_flags(
        &live,
        rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_URI,
    )
    .with_context(|| format!("opening {} read-only", live.display()))?;

    let staging = Staging::create(&data_dir, "export-staging")?;
    let index_bytes = staging.copy_index(&conn)?;
    drop(conn);

    let snapshot = Connection::open(staging.path().join(crate::export::stage::INDEX_FILE))
        .context("opening the snapshot")?;
    let titles = crate::export::titles::distinct_titles(&snapshot)?;
    let (sets, parts) = crate::export::titles::counts(&snapshot)?;

    // Refuse before downloading: discovering the limit afterwards would throw
    // away every poster fetched to get there.
    match verdict_for(estimate_bytes(index_bytes, titles.len() as u64)) {
        Verdict::TooLarge(bytes) => bail!(
            "estimated package is {bytes} bytes, over the limit; \
             a reader must hold the whole file in memory to verify it"
        ),
        Verdict::Large(bytes) => {
            println!("warning: estimated package is {bytes} bytes and getting large")
        }
        Verdict::Fine => {}
    }

    if dry_run {
        println!(
            "dry run: {sets} set(s), {parts} part(s), {} poster(s) to fetch, index {index_bytes} bytes",
            titles.len()
        );
        return Ok(());
    }

    let posters = fetch_posters(cfg, &data_dir, &staging, &titles).await?;
    let created_at = now_unix();
    let manifest = PackageManifest {
        format: mlib_spec::package::PACKAGE_FORMAT,
        created_at,
        schema: mlib_spec::schema::SCHEMA_VERSION,
        spec: mlib_spec::SPEC_VERSION,
        sets,
        parts,
        posters,
    };
    staging.write_manifest(&manifest)?;

    let packed = archive::pack_dir(staging.path())?;
    let pointer = pointer::draft(created_at, &key);
    let sealed = seal(
        &key,
        &packed,
        &mlib_spec::package::associated_data(&pointer),
    )
    .context("encrypting the package")?;

    // The estimate decided whether to start; this decides whether to ship.
    // A reader refuses a pointer over this limit, so producing one would mean
    // publishing a package nothing can open.
    let sealed_len = sealed.len() as u64;
    if sealed_len > mlib_spec::package::MAX_PACKAGE_BYTES {
        bail!(
            "package is {sealed_len} bytes, over the {} byte limit a reader enforces; \
             trim artwork or split the library",
            mlib_spec::package::MAX_PACKAGE_BYTES
        );
    }

    let dest_dir = out.unwrap_or_else(|| data_dir.join("export"));
    let written = write_package(&dest_dir, created_at, &sealed)?;
    // Written beside the package because these are the exact fields the
    // cipher authenticated. `latest::complete` fills in the rest at publish
    // time; recomputing `created_at` there would produce a package every
    // reader rejects with a tag failure that looks like an attack.
    let draft_path = written.with_extension("pointer.json");
    std::fs::write(&draft_path, serde_json::to_vec(&pointer)?)
        .with_context(|| format!("writing {}", draft_path.display()))?;
    println!(
        "wrote {} ({sealed_len} bytes, {} poster(s))",
        written.display(),
        manifest.posters.len()
    );
    if manifest.posters.is_empty() && !titles.is_empty() {
        println!(
            "warning: no posters for {} title(s); a player will show a catalog with no artwork",
            titles.len()
        );
    }

    if publish_it {
        publish_package(cfg, &pointer, &written, sealed_len, &sealed, dry_run).await?;
    }
    Ok(())
}

fn package_key(cfg: &Config) -> Result<[u8; 32]> {
    let configured = cfg.package_key.as_deref().context(
        "no package_key configured; generate one with `head -c 32 /dev/urandom | base64`",
    )?;
    Ok(parse_key(configured)?)
}

async fn fetch_posters(
    cfg: &Config,
    data_dir: &Path,
    staging: &Staging,
    titles: &[(mlib_spec::Kind, u64)],
) -> Result<Vec<mlib_spec::package::PosterEntry>> {
    // Works with no key at all when the cache is warm, which is the normal
    // case: `add` cached these payloads when it resolved each title.
    let api = TmdbClient::with_cache(
        cfg.tmdb_key.as_deref().unwrap_or(""),
        data_dir,
        &cfg.tmdb_language,
    );
    let refs = resolve_posters(&api, titles).await;
    let http = reqwest::Client::new();
    staging.fetch_posters(&http, &refs).await
}

fn write_package(dir: &Path, created_at: i64, sealed: &[u8]) -> Result<PathBuf> {
    std::fs::create_dir_all(dir).with_context(|| format!("creating {}", dir.display()))?;
    let digest = pointer::sha256(sealed);
    let dest = dir.join(package_file_name(created_at, &digest));
    std::fs::write(&dest, sealed).with_context(|| format!("writing {}", dest.display()))?;
    Ok(dest)
}

fn now_unix() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64
}

/// Publishes the archive, then the pointer that names it. The order is a
/// correctness property: a reader must never find a pointer to a file that is
/// not there yet. A reader arriving mid-publish sees the previous pointer and
/// the previous archive, which is still present.
async fn publish_package(
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
            println!("would run: {:?}", publish::substitute(&argv, file));
        }
        return Ok(());
    }

    publish::run_publish(&argv, package).await?;
    publish::run_publish(&argv, &pointer_path).await?;

    println!("published {}", complete.url);
    println!(
        "pointer at {}/latest.json is the URL the player needs",
        base_url.trim_end_matches('/')
    );
    Ok(())
}
