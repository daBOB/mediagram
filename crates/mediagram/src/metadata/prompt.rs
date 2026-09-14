//! Interactive disambiguation UI. The trait lets `resolve` and its tests
//! script deterministic answers instead of driving a real terminal.
#![allow(dead_code)] // Consumed once `add` wires metadata resolution in.

use anyhow::{Context, Result};
use dialoguer::{Input, Select};
use mlib_spec::filename::Guess;
use mlib_spec::{Episode, Kind};

use super::resolve::ResolvedItem;

/// Abstraction over the interactive prompts `resolve` needs.
pub trait Prompter {
    /// Ask the user to pick one of `candidates`; returns the chosen index.
    fn select(&mut self, question: &str, candidates: &[String]) -> Result<usize>;
    /// Ask the user to hand-enter metadata, seeded from the filename guess.
    fn manual_entry(&mut self, seed: &Guess) -> Result<ResolvedItem>;
}

/// Real terminal prompts backed by `dialoguer`.
pub struct DialoguerPrompter;

impl Prompter for DialoguerPrompter {
    fn select(&mut self, question: &str, candidates: &[String]) -> Result<usize> {
        Select::new()
            .with_prompt(question)
            .items(candidates)
            .default(0)
            .interact()
            .context("interactive selection failed")
    }

    fn manual_entry(&mut self, seed: &Guess) -> Result<ResolvedItem> {
        let is_episode = ask_kind(seed)?;
        if is_episode {
            manual_episode(seed)
        } else {
            manual_movie(seed)
        }
    }
}

fn ask_kind(seed: &Guess) -> Result<bool> {
    let idx = Select::new()
        .with_prompt("Kind")
        .items(["Movie", "Episode"])
        .default(if seed.is_episode() { 1 } else { 0 })
        .interact()
        .context("interactive selection failed")?;
    Ok(idx == 1)
}

fn manual_episode(seed: &Guess) -> Result<ResolvedItem> {
    let show: String = Input::new()
        .with_prompt("Show title")
        .with_initial_text(seed.title.clone())
        .interact_text()
        .context("interactive input failed")?;
    let season: u32 = Input::new()
        .with_prompt("Season")
        .with_initial_text(seed.season.unwrap_or(1).to_string())
        .interact_text()
        .context("interactive input failed")?;
    let episode: u32 = Input::new()
        .with_prompt("Episode")
        .with_initial_text(seed.episode.unwrap_or(1).to_string())
        .interact_text()
        .context("interactive input failed")?;
    let title: String = Input::new()
        .with_prompt("Episode title (optional)")
        .allow_empty(true)
        .with_initial_text(seed.extra.clone().unwrap_or_default())
        .interact_text()
        .context("interactive input failed")?;

    Ok(ResolvedItem {
        kind: Kind::Ep,
        ids: Default::default(),
        title: (!title.is_empty()).then_some(title),
        show: Some(show),
        year: seed.year,
        season: Some(season),
        episode: Some(Episode::Single(episode)),
        abs: seed.abs,
    })
}

fn manual_movie(seed: &Guess) -> Result<ResolvedItem> {
    let title: String = Input::new()
        .with_prompt("Title")
        .with_initial_text(seed.title.clone())
        .interact_text()
        .context("interactive input failed")?;
    let year_text: String = Input::new()
        .with_prompt("Year (optional)")
        .allow_empty(true)
        .with_initial_text(seed.year.map(|y| y.to_string()).unwrap_or_default())
        .interact_text()
        .context("interactive input failed")?;

    Ok(ResolvedItem {
        kind: Kind::Movie,
        ids: Default::default(),
        title: Some(title),
        show: None,
        year: year_text.parse().ok(),
        season: None,
        episode: None,
        abs: None,
    })
}
