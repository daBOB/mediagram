//! Managing the household across the boundary: making its first profile, a
//! grown-up or a kid, removing one, entering one from the picker, claiming
//! the admin role, setting a PIN or a kid's limit. Split out of `state.rs` to
//! keep it under the line limit; every rule lives in
//! `crate::state::profiles::manage`, which refuses as the web does.
//!
//! Each call answers a `ProfileOutcome` and never throws: a store that
//! cannot be read or written is logged and answered `Invalid`, the same
//! "nothing here throws" `state.rs` keeps. Every call that compares a PIN
//! shares this core's wrong-PIN counts.

use std::sync::Arc;

use crate::state::profiles::ProfileOutcome;

use super::super::Core;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// The household's first profile, while this device knows no grown-up
    /// (kids alone do not count): a grown-up with `new_pin`, the admin from
    /// now. `NotAllowed` once a grown-up exists here — `claim_admin` is for
    /// a household with grown-ups but no admin.
    pub async fn create_first_admin(self: Arc<Self>, name: String, new_pin: String) -> ProfileOutcome {
        self.blocking(move |core| core.state_db.manage(|m| m.create_first(&name, &new_pin)))
            .await
    }

    /// The admin adds a grown-up, with its first PIN.
    pub async fn create_grown_up(
        self: Arc<Self>,
        actor_id: String,
        pin: String,
        name: String,
        new_pin: String,
    ) -> ProfileOutcome {
        self.blocking(move |core| {
            core.state_db
                .manage(|m| m.create_grown_up(&actor_id, &pin, &name, &new_pin))
        })
        .await
    }

    /// A grown-up adds a kid of its own, from 6 or from 12.
    pub async fn create_kid(
        self: Arc<Self>,
        actor_id: String,
        pin: String,
        name: String,
        kids_age: u8,
    ) -> ProfileOutcome {
        self.blocking(move |core| {
            core.state_db
                .manage(|m| m.create_kid(&actor_id, &pin, &name, kids_age))
        })
        .await
    }

    /// Removes a profile and everything that was theirs — a grown-up with
    /// the kids it is the parent of. The admin is never removed.
    /// `chosen_profile` clears itself when it named one removed: see
    /// `profiles::chosen`.
    pub async fn delete_profile(self: Arc<Self>, actor_id: String, pin: String, id: String) -> ProfileOutcome {
        self.blocking(move |core| core.state_db.manage(|m| m.remove(&actor_id, &pin, &id)))
            .await
    }

    /// Entering a profile from the picker: a kid's opens freely, a
    /// grown-up's with its PIN.
    pub async fn unlock_profile(self: Arc<Self>, id: String, pin: String) -> ProfileOutcome {
        self.blocking(move |core| core.state_db.manage(|m| m.unlock(&id, &pin)))
            .await
    }

    /// Makes `id` the household's admin while there is none. A grown-up
    /// with a PIN gives it; one without takes `pin` as its first.
    pub async fn claim_admin(self: Arc<Self>, id: String, pin: String) -> ProfileOutcome {
        self.blocking(move |core| core.state_db.manage(|m| m.claim_admin(&id, &pin)))
            .await
    }

    /// A grown-up's own PIN, or — for the admin — another grown-up's. A
    /// grown-up from before PINs sets its first with any `pin`, `""`
    /// included.
    pub async fn set_pin(
        self: Arc<Self>,
        actor_id: String,
        pin: String,
        id: String,
        new_pin: String,
    ) -> ProfileOutcome {
        self.blocking(move |core| {
            core.state_db
                .manage(|m| m.set_pin(&actor_id, &pin, &id, &new_pin))
        })
        .await
    }

    /// A parent sets its kid's limit: 6 or 12.
    pub async fn set_kids_age(
        self: Arc<Self>,
        actor_id: String,
        pin: String,
        id: String,
        kids_age: u8,
    ) -> ProfileOutcome {
        self.blocking(move |core| {
            core.state_db
                .manage(|m| m.set_kids_age(&actor_id, &pin, &id, kids_age))
        })
        .await
    }
}
