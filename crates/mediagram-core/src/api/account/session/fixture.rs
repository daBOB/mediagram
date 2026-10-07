//! Real local sessions for error-boundary tests; no RPC is sent.

use super::*;
use grammers_mtsender::{InvocationError, RpcError};

pub(in crate::api) struct Fixture {
    _dir: tempfile::TempDir,
    pub core: Arc<Core>,
    pub owner: SenderPoolFatHandle,
}

impl Fixture {
    pub async fn new() -> Self {
        let dir = tempfile::tempdir().unwrap();
        store_auth_key(dir.path(), 2, &[7; 256]).unwrap();
        let core = Core::at(dir.path());
        let owner = connection(&core).await.1;
        Self {
            _dir: dir,
            core,
            owner,
        }
    }

    pub async fn replace(&self) -> SenderPoolFatHandle {
        let mut state = self.core.state.lock().await;
        *state = crate::api::State::default();
        store_auth_key(&self.core.data_dir, 2, &[9; 256]).unwrap();
        let replacement = connect(&self.core);
        let handle = replacement.handle.clone();
        state.client = Some(replacement);
        handle
    }

    pub async fn assert_kept(&self, owner: &SenderPoolFatHandle, key: u8) {
        assert!(is_current(&self.core, owner).await);
        assert_eq!(load_auth_key(&self.core.data_dir), Some((2, [key; 256])));
    }

    pub async fn assert_revoked(&self) {
        assert!(self.core.state.lock().await.client.is_none());
        assert!(!self.core.is_authorized());
    }
}

pub(in crate::api) fn rpc(code: i32) -> InvocationError {
    InvocationError::Rpc(RpcError::from(grammers_tl_types::types::RpcError {
        error_code: code,
        error_message: if code == 401 {
            "SESSION_REVOKED"
        } else {
            "INTERNAL"
        }
        .into(),
    }))
}
