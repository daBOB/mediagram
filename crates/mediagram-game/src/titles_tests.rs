use super::*;

pub(crate) fn title(key: &str, kids_ok: bool) -> GameTitle {
    GameTitle {
        key: key.into(),
        kind: TitleKind::Film,
        title: key.into(),
        year: Some(2000),
        poster: None,
        still: None,
        directors: vec![],
        cast: vec![],
        kids_ok,
    }
}

fn mixed() -> Vec<GameTitle> {
    [("a", true), ("b", false), ("c", true), ("d", false), ("e", true)]
        .into_iter()
        .map(|(k, ok)| title(k, ok))
        .collect()
}

#[test]
fn locked_pool_drops_unsafe_titles() {
    let pool = TitlePool::new(mixed(), true);
    assert_eq!(pool.eligible(false).len(), 3);
}

#[test]
fn kids_only_eligible_is_the_safe_subset() {
    let pool = TitlePool::new(mixed(), false);
    assert_eq!(pool.eligible(false).len(), 5);
    let safe = pool.eligible(true);
    assert_eq!(safe, vec![0, 2, 4]);
    assert!(safe.iter().all(|&i| pool.get(i).kids_ok));
}

#[test]
fn image_urls_name_index_and_kind() {
    assert_eq!(image_url(3, ImageKind::Poster), "/img/3/poster");
    assert_eq!(image_url(0, ImageKind::Still), "/img/0/still");
}
