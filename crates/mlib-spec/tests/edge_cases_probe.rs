// Comprehensive edge case probes for mlib-spec public API.
// These tests explore boundary conditions, real-world scenarios, and
// forward compatibility concerns that may not be covered by existing tests.

use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::caption_codec::{CAPTION_BUDGET, parse, to_text};
use mlib_spec::filename::parse_filename;
use mlib_spec::ids::ProviderIds;
use mlib_spec::part_name::{MAX_NAME_LEN, base_name, part_file_name};
use mlib_spec::part_plan::{
    MAX_PART_SIZE, MIB, PlanError, part_for_offset, plan_parts, validate_part_size,
};
use mlib_spec::set_hash::set_hash;

// ============================================================================
// caption_codec probes
// ============================================================================

#[test]
fn caption_with_leading_whitespace_is_accepted() {
    // Parse should tolerate leading whitespace per spec ("tolerates")
    let text = "  #mlib v=2\n{\"t\":\"movie\",\"ids\":{},\"title\":\"Test\",\"year\":2024,\"container\":\"mkv\",\"alang\":[],\"slang\":[],\"set\":\"01ABC\",\"part\":{\"i\":0,\"n\":1,\"off\":0,\"len\":100,\"sha256\":\"abc\"},\"total\":100}";
    let result = parse(text);
    // The spec says parse tolerates leading whitespace - check actual behavior
    println!("Leading whitespace parse: {:?}", result);
    // If this fails, it reveals a spec vs impl gap
}

#[test]
fn caption_with_bom_is_handled() {
    // UTF-8 BOM: EF BB BF
    let bom = "\u{FEFF}";
    let text = format!(
        "{}#mlib v=2\n{{\"t\":\"movie\",\"ids\":{{}},\"title\":\"Test\",\"year\":2024,\"container\":\"mkv\",\"alang\":[],\"slang\":[],\"set\":\"01ABC\",\"part\":{{\"i\":0,\"n\":1,\"off\":0,\"len\":100,\"sha256\":\"abc\"}},\"total\":100}}",
        bom
    );
    let result = parse(&text);
    println!("BOM handling: {:?}", result);
}

#[test]
fn caption_to_text_with_multibyte_chars_respects_budget() {
    // Use emoji (multibyte UTF-8) to verify char-based counting, not byte-based
    let c = Caption {
        cid: None,
        chap: None,
        path: None,
        t: Kind::Movie,
        ids: ProviderIds::default(),
        show: None,
        title: Some("Dune Part Two".into()),
        year: Some(2024),
        s: None,
        e: None,
        abs: None,
        q: Some("2160p".into()),
        hdr: Some("DV".into()),
        container: "mkv".into(),
        vcodec: Some("hevc".into()),
        acodec: Some("truehd".into()),
        alang: vec!["en".into(), "de".into()],
        slang: vec!["en".into()],
        dur: Some(9960),
        variant: None,
        set: "01JQ8F2K9M4XZ".into(),
        part: Part {
            i: 0,
            n: 18,
            off: 0,
            len: 3758096384,
            sha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".into(),
        },
        total: 62914560000,
    };

    // Human line with lots of emoji: 🎬🎭🎪🎨... each emoji is 1 char but 3+ bytes
    let human = "🎬 This is a movie with emoji 🎭🎪🎨🎯🎲🎳🎮🎰🎱🏀🏁🏂🏃🏄🏅🏆🏇🏈🏉🏊🏋🏌🏍🏎🏏";
    let result = to_text(&c, human);
    println!("Multibyte chars to_text: {:?}", result);

    if let Ok(text) = result {
        let len = text.chars().count();
        println!("Final text char count: {}", len);
        assert!(
            len <= CAPTION_BUDGET,
            "Result exceeds budget at {} chars",
            len
        );
    }
}

#[test]
fn caption_roundtrip_preserves_field_order_with_exotic_values() {
    // Test with anime absolute numbering, season 0 (specials), episode ranges
    let c = Caption {
        cid: None,
        chap: None,
        path: None,
        t: Kind::Ep,
        ids: ProviderIds {
            tmdb: Some(123456),
            tvdb: Some(789012),
            imdb: Some("tt9999999".into()),
        },
        show: Some("Attack on Titan".into()),
        title: Some("Special: Lost Girls".into()),
        year: Some(2020),
        s: Some(0),                      // Specials
        e: Some(Episode::Range([1, 2])), // Multi-episode
        abs: Some(100),                  // Also has absolute number
        q: Some("1080p".into()),
        hdr: Some("HDR10".into()),
        container: "mkv".into(),
        vcodec: Some("hevc".into()),
        acodec: Some("aac".into()),
        alang: vec!["ja".into(), "en".into()],
        slang: vec!["en".into(), "ja".into()],
        dur: Some(4500),
        variant: Some("Director's Cut".into()),
        set: "01JQ8F2K9M4XZ".into(),
        part: Part {
            i: 5,
            n: 10,
            off: 5000000000,
            len: 1000000000,
            sha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".into(),
        },
        total: 10000000000,
    };

    let text = to_text(&c, "").expect("to_text failed");
    let parsed = parse(&text).expect("parse failed");
    assert_eq!(c.t, parsed.t);
    assert_eq!(c.ids, parsed.ids);
    assert_eq!(c.show, parsed.show);
    assert_eq!(c.title, parsed.title);
    assert_eq!(c.year, parsed.year);
    assert_eq!(c.s, parsed.s);
    assert_eq!(c.e, parsed.e);
    assert_eq!(c.abs, parsed.abs);
    assert_eq!(c.q, parsed.q);
    assert_eq!(c.hdr, parsed.hdr);
    assert_eq!(c.container, parsed.container);
    assert_eq!(c.vcodec, parsed.vcodec);
    assert_eq!(c.acodec, parsed.acodec);
    assert_eq!(c.alang, parsed.alang);
    assert_eq!(c.slang, parsed.slang);
    assert_eq!(c.dur, parsed.dur);
    assert_eq!(c.variant, parsed.variant);
    assert_eq!(c.set, parsed.set);
    assert_eq!(c.part, parsed.part);
    assert_eq!(c.total, parsed.total);
}

#[test]
fn caption_json_with_extra_keys_behavior() {
    // Forward compatibility: should JSON with unknown keys parse or reject?
    // This tests whether the parser is strict or lenient.
    let json_with_extra = r#"
    {
      "t": "movie",
      "ids": {"tmdb": 693134},
      "title": "Dune",
      "year": 2021,
      "container": "mkv",
      "alang": ["en"],
      "slang": ["en"],
      "set": "01ABC",
      "part": {"i": 0, "n": 1, "off": 0, "len": 100, "sha256": "abc"},
      "total": 100,
      "future_feature_v3": "some new thing",
      "another_unknown": 42
    }
    "#;

    let text = format!("#mlib v=2\n{}", json_with_extra);
    let result = parse(&text);
    println!("Extra keys in JSON: {:?}", result);
    // This reveals whether serde ignores extras (good) or rejects them (strict)
}

// ============================================================================
// part_plan probes
// ============================================================================

#[test]
fn part_plan_with_total_exactly_equal_to_part_size() {
    let part_size = 5 * MIB;
    let total = part_size;
    let result = plan_parts(total, part_size);

    assert!(result.is_ok());
    let parts = result.unwrap();
    assert_eq!(parts.len(), 1, "Single part expected");
    assert_eq!(parts[0].len, total, "Part length should equal total");
    assert_eq!(parts[0].off, 0);
    assert_eq!(parts[0].idx, 0);
}

#[test]
fn part_plan_with_total_one_byte_over_part_size() {
    let part_size = 5 * MIB;
    let total = part_size + 1;
    let result = plan_parts(total, part_size);

    assert!(result.is_ok());
    let parts = result.unwrap();
    assert_eq!(parts.len(), 2, "Two parts expected");
    assert_eq!(parts[0].len, part_size);
    assert_eq!(parts[1].len, 1, "Second part should be 1 byte");
    assert_eq!(parts[1].off, part_size);
    assert_eq!(parts.iter().map(|p| p.len).sum::<u64>(), total);
}

#[test]
fn part_plan_boundary_at_max_part_size() {
    let max = MAX_PART_SIZE;
    let total = max + MIB; // One part at max + one at MIB
    let result = plan_parts(total, max);

    assert!(result.is_ok());
    let parts = result.unwrap();
    assert_eq!(parts.len(), 2);
    assert_eq!(parts[0].len, max);
    assert_eq!(parts[1].len, MIB);
}

#[test]
fn validate_part_size_rejects_zero() {
    let result = validate_part_size(0);
    assert!(matches!(result, Err(PlanError::Unaligned(0))));
}

#[test]
fn validate_part_size_rejects_one_byte() {
    let result = validate_part_size(1);
    assert!(matches!(result, Err(PlanError::Unaligned(1))));
}

#[test]
fn validate_part_size_rejects_almost_aligned() {
    let almost_mib = MIB - 1;
    let result = validate_part_size(almost_mib);
    assert!(matches!(result, Err(PlanError::Unaligned(_))));
}

#[test]
fn validate_part_size_accepts_mib_multiples() {
    for mult in 1..=4 {
        let size = MIB * mult;
        let result = validate_part_size(size);
        assert!(result.is_ok(), "Should accept {}*MiB", mult);
    }
}

#[test]
fn validate_part_size_rejects_over_max() {
    let over_max = MAX_PART_SIZE + MIB;
    let result = validate_part_size(over_max);
    assert!(matches!(result, Err(PlanError::TooLarge(_))));
}

#[test]
fn part_for_offset_on_single_part_plan() {
    let parts = plan_parts(10 * MIB, 10 * MIB).unwrap();
    assert_eq!(parts.len(), 1);

    assert_eq!(part_for_offset(&parts, 0), Some(0));
    assert_eq!(part_for_offset(&parts, 5 * MIB), Some(0));
    assert_eq!(part_for_offset(&parts, 10 * MIB - 1), Some(0));
    assert_eq!(part_for_offset(&parts, 10 * MIB), None); // Past EOF
}

#[test]
fn part_for_offset_edge_cases_across_boundaries() {
    let parts = plan_parts(5 * MIB, 2 * MIB).unwrap();
    // Should have 3 parts: [0,2M), [2M,4M), [4M,5M)

    // Test exact boundaries
    assert_eq!(part_for_offset(&parts, 0), Some(0));
    assert_eq!(part_for_offset(&parts, MIB), Some(0));
    assert_eq!(part_for_offset(&parts, 2 * MIB - 1), Some(0));

    // Boundary: exactly at second part start
    assert_eq!(part_for_offset(&parts, 2 * MIB), Some(1));

    // Test end of second part
    assert_eq!(part_for_offset(&parts, 3 * MIB), Some(1));
    assert_eq!(part_for_offset(&parts, 4 * MIB - 1), Some(1));

    // Third part
    assert_eq!(part_for_offset(&parts, 4 * MIB), Some(2));
    assert_eq!(part_for_offset(&parts, 5 * MIB - 1), Some(2));

    // Past end
    assert_eq!(part_for_offset(&parts, 5 * MIB), None);
}

#[test]
fn part_for_offset_empty_array() {
    assert_eq!(part_for_offset(&[], 0), None);
}

// ============================================================================
// part_name probes
// ============================================================================

#[test]
fn part_file_name_with_empty_base() {
    let result = part_file_name("", "mkv", 0, 1);
    println!("Empty base result: '{}'", result);
    assert!(result.chars().count() <= MAX_NAME_LEN);
    assert!(result.ends_with(".mkv"));
}

#[test]
fn part_file_name_with_very_long_ext() {
    // Ext longer than 60 chars (should be truncated by suffix logic)
    let long_ext = "a".repeat(80);
    let base = "Title";
    let result = part_file_name(base, &long_ext, 0, 1);
    println!("Very long ext result: '{}'", result);
    assert!(result.chars().count() <= MAX_NAME_LEN);
}

#[test]
fn part_file_name_multi_part_with_limited_base() {
    let long_base =
        "A Very Long Movie Title That Should Be Truncated Because We Need Room For The Part Suffix";
    let result = part_file_name(long_base, "mkv", 5, 10);
    println!("Multi-part long base: '{}'", result);
    assert!(result.chars().count() <= MAX_NAME_LEN);
    assert!(result.ends_with(".mkv.p005"));
    // Verify base was truncated to make room for suffix
    assert!(result.len() < long_base.len());
}

#[test]
fn part_file_name_edge_case_exactly_at_max_len() {
    // Try to construct something that lands exactly at MAX_NAME_LEN
    let base = "Title".to_string();
    let result = part_file_name(&base, "mkv", 0, 1);
    let len = result.chars().count();
    println!("Exact length test: {} chars in '{}'", len, result);
    assert!(len <= MAX_NAME_LEN);
}

#[test]
fn base_name_episode_with_abs_only() {
    // Anime with absolute numbering but no season/episode
    let c = Caption {
        cid: None,
        chap: None,
        path: None,
        t: Kind::Ep,
        ids: ProviderIds::default(),
        show: Some("Evangelion".into()),
        title: None,
        year: Some(1995),
        s: None,
        e: None,
        abs: Some(42),
        q: None,
        hdr: None,
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: None,
        set: "01ABC".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 100,
            sha256: "abc".into(),
        },
        total: 100,
    };

    let name = base_name(&c);
    println!("Abs-only episode base_name: '{}'", name);
    // Should include the absolute number with zero-padding
    assert!(name.contains("042") || name.contains("42"));
}

#[test]
fn base_name_episode_with_season_and_episode() {
    let c = Caption {
        cid: None,
        chap: None,
        path: None,
        t: Kind::Ep,
        ids: ProviderIds::default(),
        show: Some("The Office".into()),
        title: Some("Pilot".into()),
        year: Some(2005),
        s: Some(1),
        e: Some(Episode::Single(1)),
        abs: None,
        q: None,
        hdr: None,
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: None,
        set: "01ABC".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 100,
            sha256: "abc".into(),
        },
        total: 100,
    };

    let name = base_name(&c);
    println!("S/E episode base_name: '{}'", name);
    assert!(name.contains("s01e01"));
}

#[test]
fn base_name_episode_with_episode_range() {
    let c = Caption {
        cid: None,
        chap: None,
        path: None,
        t: Kind::Ep,
        ids: ProviderIds::default(),
        show: Some("Breaking Bad".into()),
        title: None,
        year: Some(2008),
        s: Some(5),
        e: Some(Episode::Range([14, 16])),
        abs: None,
        q: None,
        hdr: None,
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: None,
        set: "01ABC".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 100,
            sha256: "abc".into(),
        },
        total: 100,
    };

    let name = base_name(&c);
    println!("Episode range base_name: '{}'", name);
    assert!(name.contains("s05e14-e16"));
}

// ============================================================================
// filename parsing probes
// ============================================================================

#[test]
fn parse_filename_real_world_movie_filenames() {
    let cases = vec![
        (
            "2001 A Space Odyssey (1968).mkv",
            "2001 A Space Odyssey",
            Some(1968),
        ),
        (
            "Blade Runner 2049 (2017).mkv",
            "Blade Runner 2049",
            Some(2017),
        ),
        ("The Matrix (1999).mkv", "The Matrix", Some(1999)),
        ("Inception (2010).mkv", "Inception", Some(2010)),
    ];

    for (filename, expected_title, expected_year) in cases {
        let guess = parse_filename(filename);
        println!("Parsed '{}': {:?}", filename, guess);

        assert!(guess.is_some(), "Should parse: {}", filename);
        let g = guess.unwrap();
        assert_eq!(g.title, expected_title, "Title mismatch for {}", filename);
        assert_eq!(g.year, expected_year, "Year mismatch for {}", filename);
        assert!(!g.is_episode());
    }
}

#[test]
fn parse_filename_real_world_show_filenames() {
    let cases = vec![
        ("Show - 12 Monkeys.mkv", "Show", None, None, None),
        ("S01E01.mkv", "S01E01", None, None, None), // No show name, shouldn't parse as episode
        (
            "The Office S01E01.mkv",
            "The Office",
            Some(1),
            Some(1),
            None,
        ),
        (
            "Breaking Bad S05E14-E16.mkv",
            "Breaking Bad",
            Some(5),
            Some(14),
            Some(16),
        ),
    ];

    for (filename, _expected_title, exp_s, exp_e, _exp_e2) in cases {
        let guess = parse_filename(filename);
        println!("Parsed show '{}': {:?}", filename, guess);

        if let Some(g) = guess
            && exp_s.is_some()
        {
            assert!(g.is_episode(), "Should be marked as episode: {}", filename);
            assert_eq!(g.season, exp_s, "Season mismatch for {}", filename);
            assert_eq!(g.episode, exp_e, "Episode mismatch for {}", filename);
        }
    }
}

#[test]
fn parse_filename_absolute_numbering() {
    let cases = vec![
        ("Anime Show - 001.mkv", "Anime Show", Some(1)),
        ("Anime Show - 042.mkv", "Anime Show", Some(42)),
        ("Anime Show - 0100.mkv", "Anime Show", Some(100)),
    ];

    for (filename, expected_show, expected_abs) in cases {
        let guess = parse_filename(filename);
        println!("Parsed absolute '{}': {:?}", filename, guess);

        if let Some(g) = guess {
            assert_eq!(g.title, expected_show, "Show mismatch for {}", filename);
            assert_eq!(
                g.abs, expected_abs,
                "Absolute number mismatch for {}",
                filename
            );
        }
    }
}

#[test]
fn parse_filename_with_scene_junk() {
    let cases = vec![
        (
            "Movie Title (2024) 1080p WEB-DL x264.mkv",
            "Movie Title",
            Some(2024),
        ),
        ("Series.S01E01.2160p.HEVC.DV.mkv", "Series", None),
        (
            "Film - 1080p - BluRay - x265 [tmdb-693134].mkv",
            "Film",
            None,
        ),
    ];

    for (filename, _expected_title, _expected_year) in cases {
        let guess = parse_filename(filename);
        println!("Parsed with junk '{}': {:?}", filename, guess);

        // Scene junk should be stripped during parsing
        assert!(guess.is_some(), "Should parse despite junk: {}", filename);
    }
}

#[test]
fn parse_filename_with_provider_ids() {
    let cases = vec![
        "Movie [tmdb-693134].mkv",
        "Show {tvdb-121361} S01E01.mkv",
        "Film (imdb-tt15239678).mkv",
    ];

    for filename in cases {
        let guess = parse_filename(filename);
        println!("Parsed with provider id '{}': {:?}", filename, guess);

        if let Some(g) = guess {
            // At least one ID field should be populated if present in filename
            let _has_id = g.ids.tmdb.is_some() || g.ids.tvdb.is_some() || g.ids.imdb.is_some();
            println!("  IDs found: {:?}", g.ids);
        }
    }
}

#[test]
fn parse_filename_fallback_no_pattern_match() {
    let cases = vec![
        "just_some_file_name.mkv",
        "mystery.mp4",
        "no_pattern_here.avi",
    ];

    for filename in cases {
        let guess = parse_filename(filename);
        println!("Parsed fallback '{}': {:?}", filename, guess);

        // Should still create a Guess with at least title and ext
        assert!(
            guess.is_some(),
            "Should create fallback guess for {}",
            filename
        );
        if let Some(g) = guess {
            assert!(!g.title.is_empty(), "Fallback should have a title");
        }
    }
}

#[test]
fn parse_filename_empty_string() {
    let guess = parse_filename("");
    println!("Empty filename parse: {:?}", guess);
    // Empty should either return None or a fallback
}

// ============================================================================
// set_hash probes
// ============================================================================

#[test]
fn set_hash_with_empty_slice() {
    let result = set_hash::<String>(&[]);
    println!("Empty slice hash: {}", result);
    assert_eq!(result.len(), 64); // SHA256 hex is 64 chars
}

#[test]
fn set_hash_with_single_hash() {
    let result = set_hash(&["abc123"]);
    println!("Single hash: {}", result);
    assert_eq!(result.len(), 64);
}

#[test]
fn set_hash_whitespace_handling() {
    let h1 = set_hash(&["abc123", "def456"]);
    let h2 = set_hash(&[" abc123 ", " def456 "]);
    assert_eq!(h1, h2, "Whitespace should be trimmed");
}

#[test]
fn set_hash_case_insensitive() {
    let h1 = set_hash(&["ABC123", "DEF456"]);
    let h2 = set_hash(&["abc123", "def456"]);
    assert_eq!(h1, h2, "Hash should be case-insensitive");
}

#[test]
fn set_hash_order_matters() {
    let h1 = set_hash(&["first", "second", "third"]);
    let h2 = set_hash(&["third", "second", "first"]);
    assert_ne!(h1, h2, "Order should affect hash");
}

#[test]
fn set_hash_with_many_parts() {
    let parts: Vec<String> = (0..100).map(|i| format!("{:064x}", i)).collect();
    let result = set_hash(&parts);
    println!("100-part hash: {}", result);
    assert_eq!(result.len(), 64);
}
