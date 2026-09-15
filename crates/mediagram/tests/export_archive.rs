//! The archive half of the package: a gzipped tar with the manifest first,
//! so a reader can learn what it holds without unpacking everything.

use mediagram::export::archive::{pack_dir, unpack_to};

fn staging(dir: &std::path::Path) {
    std::fs::write(dir.join("manifest.json"), br#"{"format":1}"#).unwrap();
    std::fs::write(dir.join("library.db"), b"sqlite bytes").unwrap();
    std::fs::create_dir_all(dir.join("posters")).unwrap();
    std::fs::write(dir.join("posters/tmdb-movie-1.jpg"), b"jpeg bytes").unwrap();
}

#[test]
fn an_archive_round_trips_every_file() {
    let src = tempfile::tempdir().unwrap();
    staging(src.path());

    let packed = pack_dir(src.path()).unwrap();
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).unwrap();

    assert_eq!(
        std::fs::read(out.path().join("library.db")).unwrap(),
        b"sqlite bytes"
    );
    assert_eq!(
        std::fs::read(out.path().join("posters/tmdb-movie-1.jpg")).unwrap(),
        b"jpeg bytes"
    );
    assert_eq!(
        std::fs::read(out.path().join("manifest.json")).unwrap(),
        br#"{"format":1}"#
    );
}

#[test]
fn the_manifest_is_the_first_member() {
    let src = tempfile::tempdir().unwrap();
    staging(src.path());

    let packed = pack_dir(src.path()).unwrap();

    let decoder = flate2::read::GzDecoder::new(&packed[..]);
    let mut archive = tar::Archive::new(decoder);
    let first = archive.entries().unwrap().next().unwrap().unwrap();
    assert_eq!(first.path().unwrap().to_str().unwrap(), "manifest.json");
}

#[test]
fn an_archive_is_gzip_and_smaller_than_its_input_for_compressible_data() {
    let src = tempfile::tempdir().unwrap();
    std::fs::write(src.path().join("manifest.json"), vec![b'a'; 100_000]).unwrap();

    let packed = pack_dir(src.path()).unwrap();

    assert_eq!(&packed[..2], &[0x1f, 0x8b], "gzip magic");
    assert!(packed.len() < 100_000);
}

/// A reader unpacking an archive must never write outside its target, so the
/// unpacker refuses anything that could climb out.
#[test]
fn unpacking_refuses_a_member_that_escapes_the_target() {
    // The tar crate refuses to *write* a `..` path, so the hostile member is
    // forged by writing the name into the raw header. A reader on another
    // platform has no such protection, which is why our unpacker needs its
    // own check.
    let mut tar_bytes = Vec::new();
    {
        let mut builder = tar::Builder::new(&mut tar_bytes);
        let payload = b"pwned";
        let mut header = tar::Header::new_old();
        header.set_size(payload.len() as u64);
        header.set_mode(0o644);
        let name = b"../escaped.txt";
        header.as_old_mut().name[..name.len()].copy_from_slice(name);
        header.set_cksum();
        builder.append(&header, &payload[..]).unwrap();
        builder.finish().unwrap();
    }
    let mut gz = Vec::new();
    {
        use std::io::Write;
        let mut enc = flate2::write::GzEncoder::new(&mut gz, flate2::Compression::default());
        enc.write_all(&tar_bytes).unwrap();
        enc.finish().unwrap();
    }

    let out = tempfile::tempdir().unwrap();
    assert!(unpack_to(&gz, out.path()).is_err());
    assert!(!out.path().parent().unwrap().join("escaped.txt").exists());
}

#[test]
fn an_empty_directory_still_produces_a_valid_archive() {
    let src = tempfile::tempdir().unwrap();
    let packed = pack_dir(src.path()).unwrap();
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).unwrap();
}
