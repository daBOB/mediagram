use super::*;

#[test]
fn the_ring_is_closed_and_every_field_borders_at_most_one_plot() {
    let b = board();
    assert_eq!(b.tiles.len(), usize::from(FIELDS));
    let mut seen = vec![0; b.tiles.len()];
    for t in &b.tiles {
        let next = &b.tiles[usize::from(t.exits[0])];
        assert_eq!(t.x.abs_diff(next.x) + t.y.abs_diff(next.y), 1, "field {} is not next to its exit", t.id);
    }
    for p in &b.plots {
        assert!((2..=4).contains(&p.fields.len()));
        for &f in &p.fields {
            seen[usize::from(f)] += 1;
            let t = &b.tiles[usize::from(f)];
            let near = (p.x..p.x + p.w).any(|x| (p.y..p.y + p.h).any(|y| x.abs_diff(t.x) + y.abs_diff(t.y) == 1));
            assert!(near, "field {f} does not border {}", p.name);
        }
    }
    assert!(seen.iter().all(|&n| n <= 1));
}

#[test]
fn prices_stay_in_the_designed_range() {
    for p in board().plots {
        let build: i32 = p.levels.iter().map(|l| l.cost).sum();
        assert!((1000..=3500).contains(&p.land));
        assert!((3000..=12000).contains(&build), "{} costs {build}", p.name);
        assert!((300..=1500).contains(&p.levels.last().unwrap().rate));
        assert!((3..=6).contains(&p.levels.len()));
    }
}
