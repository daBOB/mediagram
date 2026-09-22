//! Getting a set's bytes out of Telegram: resolving the document behind each
//! part, downloading the planned steps, and handing the bytes to whoever
//! asked — an HTTP response in the uploader, a read call on Android.

pub mod document;
pub mod documents;
pub mod fetch;
pub mod source;
pub mod stream;
