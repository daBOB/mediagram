//! Line-for-line port of `web/test/search-normalize.test.ts`'s cases: the
//! same claims about `fold`, `spell_out`, `variants` and `terms`, checked
//! against this port instead of the original.

use super::*;

#[test]
fn case_goes() {
    assert_eq!(fold(Some("Überblick")), fold(Some("überblick")));
    assert_eq!(fold(Some("BROKER")), "broker");
}

#[test]
fn german_diacritics_fold_to_their_base_letters() {
    assert_eq!(fold(Some("Überblick")), "uberblick");
    assert_eq!(fold(Some("Qualität")), "qualitat");
    assert_eq!(fold(Some("Glaubenssätze")), "glaubenssatze");
    assert_eq!(fold(Some("Börse")), "borse");
}

#[test]
fn the_sharp_s_folds_the_way_it_is_typed() {
    assert_eq!(fold(Some("Straße")), "strasse");
    assert_eq!(fold(Some("Strasse")), fold(Some("Straße")));
}

#[test]
fn other_alphabets_fold_too() {
    assert_eq!(fold(Some("Café")), "cafe");
    assert_eq!(fold(Some("naïve")), "naive");
    assert_eq!(fold(Some("Señor")), "senor");
}

#[test]
fn punctuation_and_spacing_are_flattened_not_dropped_silently() {
    assert_eq!(fold(Some("Produkte ⁄ Instrumente")), "produkte instrumente");
    assert_eq!(fold(Some("Zeitebenen – Teil 1")), "zeitebenen teil 1");
    assert_eq!(fold(Some("  spaced   out  ")), "spaced out");
}

#[test]
fn an_empty_or_absent_string_folds_to_nothing() {
    assert_eq!(fold(Some("")), "");
    assert_eq!(fold(None), "");
}

#[test]
fn a_diacritic_that_is_not_a_combining_mark_is_dropped_like_any_other() {
    // U+00B4, a standalone acute accent rather than one NFD ever produces —
    // dropping only NFD's combining marks would leave it behind as a
    // spurious word break ("geht s").
    assert_eq!(fold(Some("Geht´s")), "gehts");
    // U+02BC, the apostrophe-shaped modifier letter Diacritic=Yes also
    // covers, despite reading as a letter rather than a mark.
    assert_eq!(fold(Some("ʼn")), "n");
}

#[test]
fn a_symbol_that_only_looks_like_a_letter_is_not_one() {
    // "Ⓐ" is General_Category=So (Symbol), not Letter — `\p{L}` does not
    // count it, so it collapses to nothing (a lone space, trimmed away).
    assert_eq!(fold(Some("Ⓐ")), "");
}

#[test]
fn a_combining_mark_with_diacritic_no_still_splits_the_word_it_sits_in() {
    // U+0363, used in medieval manuscript abbreviations: not Letter or
    // Number either, and — because it is one of the marks `\p{Diacritic}`
    // does not cover — it survives the first pass and is only removed by
    // the second, as a word-splitting space. A faithful port reproduces
    // this rather than smoothing it over.
    assert_eq!(fold(Some("eͣx")), "e x");
}

#[test]
fn umlauts_become_the_two_letters_people_type_instead() {
    assert_eq!(spell_out(Some("Überblick")), "ueberblick");
    assert_eq!(spell_out(Some("Qualität")), "qualitaet");
    assert_eq!(spell_out(Some("Börse")), "boerse");
}

#[test]
fn the_sharp_s_spells_out_the_same_way_it_folds() {
    assert_eq!(spell_out(Some("Straße")), "strasse");
}

#[test]
fn it_agrees_with_fold_on_text_that_has_nothing_to_spell_out() {
    assert_eq!(spell_out(Some("Broker Vergleich")), fold(Some("Broker Vergleich")));
    assert_eq!(spell_out(Some("Produkte ⁄ Instrumente")), "produkte instrumente");
}

#[test]
fn a_diacritic_that_is_not_a_german_umlaut_folds_rather_than_spelling_out() {
    assert_eq!(spell_out(Some("Café")), "cafe");
    assert_eq!(spell_out(Some("Señor")), "senor");
}

#[test]
fn an_empty_or_absent_string_spells_out_to_nothing() {
    assert_eq!(spell_out(Some("")), "");
    assert_eq!(spell_out(None), "");
}

#[test]
fn text_with_an_umlaut_can_be_typed_two_ways() {
    assert_eq!(variants(Some("Überblick")), vec!["uberblick", "ueberblick"]);
}

#[test]
fn text_without_one_has_a_single_form_not_a_duplicate_pair() {
    assert_eq!(variants(Some("Broker Vergleich")), vec!["broker vergleich"]);
    assert_eq!(variants(Some("Straße")), vec!["strasse"]);
}

#[test]
fn nothing_at_all_has_one_empty_form() {
    assert_eq!(variants(None), vec![""]);
}

#[test]
fn words_become_terms() {
    assert_eq!(terms(Some("grundlagen borse")), vec!["grundlagen", "borse"]);
}

#[test]
fn terms_are_folded_like_everything_else() {
    assert_eq!(terms(Some("Grundlagen Börse")), vec!["grundlagen", "borse"]);
}

#[test]
fn extra_spacing_and_punctuation_do_not_make_empty_terms() {
    assert_eq!(terms(Some("  trading ,  journal ")), vec!["trading", "journal"]);
}

#[test]
fn an_empty_query_has_no_terms() {
    assert!(terms(Some("")).is_empty());
    assert!(terms(Some("   ")).is_empty());
    assert!(terms(Some("!!!")).is_empty());
}
