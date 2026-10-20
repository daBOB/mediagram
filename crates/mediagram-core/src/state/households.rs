//! Households: which one each profile is in, who runs each, and which one
//! this device shows. See [`rules`].

pub mod rules;

/// The first household. The same id on every device, so each can migrate
/// alone; never deleted.
pub const HOME: &str = "home";
