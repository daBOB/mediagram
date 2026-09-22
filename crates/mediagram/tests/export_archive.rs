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

#[test]
fn packing_preserves_non_ascii_filenames() {
    let src = tempfile::tempdir().unwrap();
    std::fs::write(src.path().join("файл.txt"), b"russian name").unwrap();
    std::fs::write(src.path().join("文件.txt"), b"chinese name").unwrap();
    std::fs::write(src.path().join("파일.txt"), b"korean name").unwrap();

    let packed = pack_dir(src.path()).unwrap();
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).unwrap();

    assert_eq!(
        std::fs::read(out.path().join("файл.txt")).unwrap(),
        b"russian name"
    );
    assert_eq!(
        std::fs::read(out.path().join("文件.txt")).unwrap(),
        b"chinese name"
    );
    assert_eq!(
        std::fs::read(out.path().join("파일.txt")).unwrap(),
        b"korean name"
    );
}

/// A name past the 100-byte field in the classic tar header must still
/// round-trip; the GNU long-name extension is what makes that true.
#[test]
fn packing_preserves_filenames_longer_than_the_classic_tar_header_field() {
    let src = tempfile::tempdir().unwrap();
    let long_name = "a".repeat(200);
    std::fs::write(src.path().join(&long_name), b"content").unwrap();

    let packed = pack_dir(src.path()).unwrap();
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).unwrap();

    assert_eq!(
        std::fs::read(out.path().join(&long_name)).unwrap(),
        b"content"
    );
}

#[test]
fn packing_preserves_deeply_nested_directories() {
    let src = tempfile::tempdir().unwrap();
    let mut path = src.path().to_path_buf();
    for i in 0..20 {
        path.push(format!("dir{}", i));
    }
    std::fs::create_dir_all(&path).unwrap();
    std::fs::write(path.join("file.txt"), b"deep content").unwrap();

    let packed = pack_dir(src.path()).unwrap();
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).unwrap();

    let mut out_path = out.path().to_path_buf();
    for i in 0..20 {
        out_path.push(format!("dir{}", i));
    }
    assert_eq!(
        std::fs::read(out_path.join("file.txt")).unwrap(),
        b"deep content"
    );
}

#[test]
fn a_zero_byte_file_round_trips() {
    let src = tempfile::tempdir().unwrap();
    std::fs::write(src.path().join("empty.txt"), b"").unwrap();

    let packed = pack_dir(src.path()).unwrap();
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).unwrap();

    assert_eq!(std::fs::read(out.path().join("empty.txt")).unwrap(), b"");
}

#[test]
fn a_couple_hundred_files_all_round_trip() {
    let src = tempfile::tempdir().unwrap();
    for i in 0..200 {
        std::fs::write(
            src.path().join(format!("file_{:03}.txt", i)),
            format!("content {}", i).as_bytes(),
        )
        .unwrap();
    }

    let packed = pack_dir(src.path()).unwrap();
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).unwrap();

    for i in 0..200 {
        let content = std::fs::read(out.path().join(format!("file_{:03}.txt", i))).unwrap();
        assert_eq!(content, format!("content {}", i).as_bytes());
    }
}

/// A member named `dir/` rather than `dir` is not a regular file; `unpack_to`
/// checks the entry type itself rather than trusting the header's name.
#[test]
fn unpacking_refuses_a_member_that_is_not_a_regular_file() {
    use std::io::Write;

    let mut tar_bytes = Vec::new();
    {
        let mut builder = tar::Builder::new(&mut tar_bytes);
        let payload = b"content";
        let mut header = tar::Header::new_gnu();
        header.set_size(payload.len() as u64);
        header.set_mode(0o644);
        header.set_mtime(0);
        header.set_uid(0);
        header.set_gid(0);
        header.set_cksum();
        let name = b"file.txt/";
        header.as_old_mut().name[..name.len()].copy_from_slice(name);
        header.set_cksum();
        builder.append(&header, &payload[..]).unwrap();
        builder.finish().unwrap();
    }
    let mut gz = Vec::new();
    {
        let mut enc = flate2::write::GzEncoder::new(&mut gz, flate2::Compression::default());
        enc.write_all(&tar_bytes).unwrap();
        enc.finish().unwrap();
    }

    let out = tempfile::tempdir().unwrap();
    assert!(unpack_to(&gz, out.path()).is_err());
}

/// `is_dir`/`read` follow symlinks, so a link inside staging could otherwise
/// pull a file from outside the staging directory into the package.
#[test]
fn a_symlink_inside_the_source_directory_is_not_packed() {
    let src = tempfile::tempdir().unwrap();
    std::fs::write(src.path().join("target.txt"), b"target content").unwrap();
    let outside = tempfile::tempdir().unwrap();
    std::fs::write(outside.path().join("secret.txt"), b"not for the package").unwrap();

    #[cfg(unix)]
    {
        use std::os::unix::fs as unix_fs;
        unix_fs::symlink(src.path().join("target.txt"), src.path().join("link.txt")).unwrap();
        unix_fs::symlink(
            outside.path().join("secret.txt"),
            src.path().join("escape.txt"),
        )
        .unwrap();
    }

    let packed = pack_dir(src.path()).unwrap();
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).unwrap();

    assert!(!out.path().join("link.txt").exists(), "symlink not packed");
    assert!(
        !out.path().join("escape.txt").exists(),
        "a link out of the source directory must not pull its target into the package"
    );
    assert_eq!(
        std::fs::read(out.path().join("target.txt")).unwrap(),
        b"target content",
        "the real file is still packed"
    );
}
