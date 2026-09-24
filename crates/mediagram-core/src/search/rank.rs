//! Finding a title among five hundred. A port of `web/src/search/index.ts`'s
//! `SearchIndex`; see that file for why every term must hit some field but
//! not all in the same one, and why a title match outranks a folder match,
//! which outranks a summary match.
//!
//! The tie-break within a field is the web's `localeCompare("de")`, not an
//! approximation of it: `icu_collator`, ICU4X's pure-Rust collator, is built
//! from the same CLDR German tailoring Bun's ICU build reads, so the two
//! agree rather than one substituting for the other.
//!
//! Folding every set's title, show, chapter, path and summary costs enough
//! (tens of milliseconds over the real library) that doing it on every
//! keystroke would visibly lag a query — [`Corpus::build`] is the one-time
//! cost, cached by `api::search` per catalog version the way the web's
//! `SearchIndex` is folded once at process startup over a catalog that
//! never changes under it.

use icu_collator::options::CollatorOptions;
use icu_collator::{Collator, CollatorBorrowed};
use icu_locale_core::locale;

use crate::catalog::SearchableSet;

use super::excerpt::excerpt;
use super::normalize::{terms, variants};

/// Which field earned a hit. Ordered: earlier is a stronger match, the same
/// order the web's `FIELDS` lists them in.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Field {
    Title,
    Show,
    Chap,
    Path,
    Summary,
}

const FIELDS: [Field; 5] = [Field::Title, Field::Show, Field::Chap, Field::Path, Field::Summary];

impl Field {
    /// The word the web API answers this field as, over the wire.
    pub fn as_str(self) -> &'static str {
        match self {
            Field::Title => "title",
            Field::Show => "show",
            Field::Chap => "chap",
            Field::Path => "path",
            Field::Summary => "summary",
        }
    }
}

/// More than a screenful is not a result list, it is the library again.
pub const MAX_HITS: usize = 50;

/// One set's folded search text, computed once per catalog version rather
/// than once per query — see the module doc.
struct CorpusEntry {
    set: SearchableSet,
    /// Each field's searchable forms, in `FIELDS` order.
    forms: [Vec<String>; 5],
}

/// The whole catalog, folded and ready to rank against any number of
/// queries. Owns its rows rather than borrowing the caller's `Vec`, so it
/// can outlive the read that built it and be kept as a cache entry.
pub struct Corpus {
    entries: Vec<CorpusEntry>,
}

impl Corpus {
    pub fn build(sets: &[SearchableSet]) -> Self {
        Corpus {
            entries: sets
                .iter()
                .map(|set| CorpusEntry {
                    set: set.clone(),
                    forms: [
                        variants(set.title.as_deref()),
                        variants(set.show.as_deref()),
                        variants(set.chap.as_deref()),
                        variants(set.path.as_deref()),
                        variants(set.summary.as_deref()),
                    ],
                })
                .collect(),
        }
    }
}

/// Why a set matched, and which set: only `set_id`, never title or path — a
/// caller already holds the full row and joins this back onto it.
#[derive(Debug, Clone, PartialEq)]
pub struct Hit {
    pub set_id: String,
    pub matched: Field,
    /// The words around a summary match, or `None` when the hit speaks for
    /// itself.
    pub excerpt: Option<String>,
}

/// A German collator at the web's default strength — `Intl.Collator`'s
/// `sensitivity: "variant"`, which is what an unqualified `localeCompare`
/// uses. Cheap to build (`compiled_data` only assembles references into
/// data baked into this binary, no I/O), but still a call worth making once
/// per [`search`] rather than once per comparison inside the sort.
pub fn collator() -> CollatorBorrowed<'static> {
    Collator::try_new(locale!("de").into(), CollatorOptions::default())
        .expect("German collation data is compiled into this binary")
}

/// The sets matching every term of `query`, best first, cut to
/// [`MAX_HITS`]. Empty for an empty query — every term has to match
/// *somewhere*, so a query with no terms cannot honestly return the whole
/// corpus.
pub fn search(corpus: &Corpus, query: &str, collator: &CollatorBorrowed) -> Vec<Hit> {
    let wanted = terms(Some(query));
    if wanted.is_empty() {
        return Vec::new();
    }

    struct Scored<'a> {
        set: &'a SearchableSet,
        /// The strongest field any term matched, as an index into `FIELDS`.
        rank: usize,
    }
    let mut hits: Vec<Scored> = Vec::new();
    for entry in &corpus.entries {
        let mut best = FIELDS.len();
        let mut matched_all = true;
        for term in &wanted {
            match entry.forms.iter().position(|forms| forms.iter().any(|text| text.contains(term.as_str()))) {
                Some(at) => best = best.min(at),
                None => {
                    matched_all = false;
                    break;
                }
            }
        }
        if matched_all {
            hits.push(Scored { set: &entry.set, rank: best });
        }
    }

    // Field first; then title, culturally ordered — a course's lessons tie
    // on field constantly (they share a folder), and the tie-break is what
    // a viewer actually reads as "the order search answers in".
    hits.sort_by(|a, b| {
        a.rank
            .cmp(&b.rank)
            .then_with(|| collator.compare(a.set.title.as_deref().unwrap_or(""), b.set.title.as_deref().unwrap_or("")))
    });
    hits.truncate(MAX_HITS);

    hits.into_iter()
        .map(|scored| {
            let field = FIELDS[scored.rank];
            let excerpt = if field == Field::Summary { excerpt(scored.set.summary.as_deref(), &wanted) } else { None };
            Hit { set_id: scored.set.set_id.clone(), matched: field, excerpt }
        })
        .collect()
}

#[cfg(test)]
#[path = "rank_tests.rs"]
mod tests;
