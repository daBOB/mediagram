//! `mediagram login`: phone → code → optional 2FA password; persists the session.

use anyhow::Result;

use crate::config::Config;
use crate::telegram::client;

pub async fn run(cfg: &Config) -> Result<()> {
    let (tg_client, handle, pool_task) = client::open_client(cfg).await?;
    let fresh_login = crate::telegram::login::ensure_login(&tg_client, cfg).await;

    // Stopped whether or not the login succeeded: a wrong code or password
    // must not return with the pool still connected.
    handle.quit();
    let _ = pool_task.await;
    let fresh_login = fresh_login?;

    if fresh_login {
        println!("Login successful; session saved.");
    } else {
        println!("Already authorized.");
    }
    Ok(())
}
