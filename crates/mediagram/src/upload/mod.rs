//! Getting a file into the channel: preparing and recording it, then streaming its
//! parts — a hashing byte-range reader, the Telegram transport that sends
//! it, and the resumable per-set pipeline — and the upload session every
//! uploading command walks its items through (`session`).

pub mod adopt;
pub mod finish;
pub mod lock;
pub mod new_set;
pub mod part_reader;
mod part_upload;
pub mod pipeline;
pub mod plan;
pub mod prepare_set;
pub mod progress;
pub mod progress_line;
pub mod record_document;
pub mod session;
pub mod transport;
