//! The prebuilt metadata package: verifying a downloaded, sealed archive
//! against its pointer, decrypting it, and unpacking the index it carries.
//! [`cipher`] is shared with the writer too: the uploader's `export-package`
//! seals a package with it.
//!
//! `mlib_spec::package` defines the pointer and the authenticated-data rule
//! both sides follow; the reader here turns a sealed archive back into a
//! `library.db` a catalog can query.

pub mod cipher;
mod reader;

pub use reader::{PackageError, read_package};
