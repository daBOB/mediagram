use super::*;
use rand::SeedableRng;

fn q(category: &str, n: usize) -> Question {
    Question {
        category: category.into(),
        text: format!("{category} {n}?"),
        correct: "ja".into(),
        wrong: ["a".into(), "b".into(), "c".into()],
        image: None,
    }
}

/// `per` questions in each of `cats`.
fn pack(cats: &[&str], per: usize) -> Arc<Vec<Question>> {
    Arc::new(cats.iter().flat_map(|c| (0..per).map(move |n| q(c, n))).collect())
}

fn used() -> Arc<Mutex<HashSet<String>>> {
    Arc::new(Mutex::new(HashSet::new()))
}

fn rng() -> StdRng {
    StdRng::seed_from_u64(3)
}

#[test]
fn offers_distinct_categories_with_enough_left() {
    let s = QuestionSupply::new(pack(&["A", "B", "C", "D"], 5), vec![], used(), 0);
    let offer = s.offer(&mut rng(), 3, 3);
    assert_eq!(offer.len(), 3);
    let mut sorted = offer.clone();
    sorted.sort();
    sorted.dedup();
    assert_eq!(sorted.len(), 3, "distinct");
    assert!(offer.iter().all(|c| s.left(c) >= 3));
}

#[test]
fn take_never_repeats_and_marks_asked() {
    let shared = used();
    let mut s = QuestionSupply::new(pack(&["A"], 6), vec![], shared.clone(), 0);
    let first = s.take("A", 3, &mut rng());
    let second = s.take("A", 3, &mut rng());
    assert_eq!((first.len(), second.len()), (3, 3));
    assert!(first.iter().all(|f| second.iter().all(|g| g.text != f.text)));
    assert_eq!(s.left("A"), 0);
    assert_eq!(shared.lock().unwrap().len(), 6, "the night remembers them");
}

#[test]
fn a_drained_category_is_no_longer_offered() {
    let mut s = QuestionSupply::new(pack(&["A", "B"], 4), vec![], used(), 0);
    s.take("A", 3, &mut rng());
    assert_eq!(s.offer(&mut rng(), 3, 3), vec!["B".to_string()]);
}

#[test]
fn film_questions_land_in_their_own_category() {
    let films = vec![q("Regie", 0), q("Filmszene", 1), q("Besetzung", 2)];
    let mut s = QuestionSupply::new(pack(&[], 0), films, used(), 0);
    assert_eq!(s.offer(&mut rng(), 3, 3), vec![FILMS.to_string()]);
    let taken = s.take(FILMS, 3, &mut rng());
    assert!(taken.iter().all(|t| t.category == FILMS));
    assert!(s.offer(&mut rng(), 3, 3).is_empty(), "empty supply offers nothing");
}

#[test]
fn a_spent_night_starts_the_general_pack_over() {
    let shared = used();
    let general = pack(&["A"], 4);
    shared.lock().unwrap().extend(general.iter().map(|g| g.text.clone()));
    shared.lock().unwrap().insert("year:film-key".into());
    let s = QuestionSupply::new(general, vec![], shared.clone(), 3);
    assert_eq!(s.left("A"), 4);
    assert!(shared.lock().unwrap().contains("year:film-key"), "film history is kept");
}

#[test]
fn deck_draws_from_any_category_once() {
    let mut s = QuestionSupply::new(pack(&["A", "B"], 3), vec![], used(), 0);
    let deck = s.deck(5, &mut rng());
    assert_eq!(deck.len(), 5);
    assert_eq!(s.left("A") + s.left("B"), 1);
}

#[test]
fn fact_pack_parses_with_text() {
    let facts = crate::kopfnuss_facts::fact_pack();
    assert!(facts.len() >= 50, "{}", facts.len());
    assert!(facts.iter().all(|f| !f.text.trim().is_empty()));
    assert!(!crate::kopfnuss_facts::pick_fact(&mut rng()).is_empty());
}
