//! Getting a file into the channel: preparing and recording it, then streaming its
//! parts — a hashing byte-range reader, the Telegram transport that sends
//! it, and the resumable per-set pipeline.

pub mod adopt;
pub mod finish;
pub mod finish_set;
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
pub mod resume;
pub mod transport;

#[deprecated(
    since = "0.40.2",
    note = "Use prepare_set for preparation that records the set"
)]
pub use prepare_set as plan_set;
#[deprecated(
    since = "0.40.2",
    note = "Use record_document for document persistence"
)]
pub use record_document as plan_document;
