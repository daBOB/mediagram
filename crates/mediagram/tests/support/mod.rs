//! Fixtures shared by more than one integration test binary.
//!
//! Each binary that declares `mod support` compiles the whole module, so the
//! fixtures another binary needs would be reported unused here. That is what
//! the allow is for, and it is why single-use helpers stay in the test that
//! uses them.
#![allow(dead_code)]

pub mod export;
pub mod rescan;
pub mod tmdb;
pub mod upload;
