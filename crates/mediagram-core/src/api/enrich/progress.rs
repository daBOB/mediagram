//! How far a fetch run has got, for a screen that would otherwise show only
//! that one is running. Kotlin polls [`Core::fetch_progress`] while its
//! `fetch_missing` call is out; the run writes here as it walks.

use std::sync::Mutex;
use std::time::Instant;

use super::super::Core;
use crate::dto::{FetchProgress, FetchStep};

/// The step under way and how many of its items are behind it. Each step
/// counts its own items from zero: the image count is only known once every
/// title has been looked up, so one count across the run could not be exact.
#[derive(Default)]
pub struct FetchMeter(Mutex<Option<Running>>);

struct Running {
    step: FetchStep,
    done: u32,
    total: u32,
    started: Instant,
}

impl FetchMeter {
    /// Begins `step` over `total` items; the guard clears the meter when
    /// the run ends, including a run Kotlin cancels mid-walk, so the next
    /// run never starts by showing where the last one stopped.
    pub fn run(&self) -> MeterGuard<'_> {
        MeterGuard(self)
    }

    pub fn start(&self, step: FetchStep, total: usize) {
        self.set(Some(Running {
            step,
            done: 0,
            total: count(total),
            started: Instant::now(),
        }));
    }

    pub fn set_done(&self, done: usize) {
        if let Some(running) = self.lock().as_mut() {
            running.done = count(done).min(running.total);
        }
    }

    pub fn snapshot(&self) -> Option<FetchProgress> {
        self.lock().as_ref().map(|running| FetchProgress {
            step: running.step,
            done: running.done,
            total: running.total,
            step_elapsed_ms: running.started.elapsed().as_millis() as u64,
        })
    }

    fn set(&self, running: Option<Running>) {
        *self.lock() = running;
    }

    // A poisoned meter still holds a readable count; progress is never worth a panic.
    fn lock(&self) -> std::sync::MutexGuard<'_, Option<Running>> {
        self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
    }
}

fn count(n: usize) -> u32 {
    u32::try_from(n).unwrap_or(u32::MAX)
}

/// Clears its meter when dropped; see [`FetchMeter::run`].
pub struct MeterGuard<'a>(&'a FetchMeter);

impl Drop for MeterGuard<'_> {
    fn drop(&mut self) {
        self.0.set(None);
    }
}

#[uniffi::export]
impl Core {
    /// How far the fetch under way has got, or `None` while none runs.
    pub fn fetch_progress(&self) -> Option<FetchProgress> {
        self.fetch_meter.snapshot()
    }
}

#[cfg(test)]
#[path = "progress_tests.rs"]
mod tests;
