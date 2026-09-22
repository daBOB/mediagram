//! `mediagram login`: phone → code → optional 2FA password; persists the session.

use anyhow::Result;

use crate::config::Config;
use crate::telegram::client;

pub async fn run(cfg: &Config) -> Result<()> {
    let (tg_client, handle, pool_task) = client::open_client(cfg).await?;
    let fresh_login = crate::telegram::login::ensure_login(&tg_client, cfg).await?;

    handle.quit();
    let _ = pool_task.await;

    if fresh_login {
        println!("Login successful; session saved.");
    } else {
        println!("Already authorized.");
    }
    Ok(())
}
