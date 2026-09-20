//! The prebuilt metadata package, from the reader's side: verifying a
//! downloaded, sealed archive against its pointer, decrypting it, and
//! unpacking the index it carries.
//!
//! `mlib_spec::package` defines the pointer and the authenticated-data rule
//! shared with the uploader that writes packages; this module is the half
//! that turns a sealed archive back into a `library.db` a catalog can query.

pub mod cipher;
mod reader;

pub use reader::{PackageError, SUPPORTED_SCHEMA, read_package};
