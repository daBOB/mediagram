//! Raw Telegram iterator IO used by the catalog workflows.

use grammers_client::client::{DialogIter, DownloadIter, SearchIter};
use grammers_client::message::Message;
use grammers_client::peer::Dialog;
use grammers_mtsender::InvocationError;

pub(super) trait Responses: Send {
    type Item;
    fn next_response(
        &mut self,
    ) -> impl Future<Output = Result<Option<Self::Item>, InvocationError>> + Send;
}

impl Responses for DialogIter {
    type Item = Dialog;
    async fn next_response(&mut self) -> Result<Option<Dialog>, InvocationError> {
        self.next().await
    }
}

impl Responses for SearchIter {
    type Item = Message;
    async fn next_response(&mut self) -> Result<Option<Message>, InvocationError> {
        self.next().await
    }
}

impl Responses for DownloadIter {
    type Item = Vec<u8>;
    async fn next_response(&mut self) -> Result<Option<Vec<u8>>, InvocationError> {
        self.next().await
    }
}

#[cfg(test)]
pub(super) struct Scripted<T>(pub std::vec::IntoIter<Result<T, InvocationError>>);

#[cfg(test)]
impl<T: Send> Responses for Scripted<T> {
    type Item = T;
    async fn next_response(&mut self) -> Result<Option<T>, InvocationError> {
        self.0.next().transpose()
    }
}
