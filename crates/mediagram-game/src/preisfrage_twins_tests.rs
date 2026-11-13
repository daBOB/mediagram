use super::*;

fn article(asin: &str, title: &str) -> Article {
    Article { asin: asin.into(), name: title.into(), title: title.into(), cents: 9999, reviews: 0 }
}

// Titles as amazon.de listed them in a fetched list: three sellers' copper hot pots.
const CIRONI: &str = "CIRONI Hot Pot Kupferkohle Chinesischer Kochtopf Suppe Alt Großer traditioneller Koch Kochen für ungewöhnliches Geschenk, Kupfer. (Größe: 28 cm)";
const CHAFING: &str = "Chinesischer Hot Pot,Kupfer Hot Pot, Chafing Dishes, chinesischer traditioneller Alter Holzkohle-Kupfer-Hot Pot, Pot Shabu, Camping, Heimgebrauch, Kochen, Kochen, for ungewöhnliches Geschenk(28cm)";
const PEKING: &str = "Traditioneller alter chinesischer Peking-Hotpot aus Kupfer mit Holzkohle, Shabu-Topf, für den Heimgebrauch beim Camping, zum Kochen und für ungewöhnliche Geschenke (Farbe: Messing, Größe: 28 cm)";
// Each shares a word or two with a hot pot and is another thing.
const LIGHTER: &str = "Metall Retro Flint Kerosin Feuerzeug Ungewöhnliche Schleifscheibe Zigarre Kupfer Winddicht Kreative Persönlichkeit Messing Feuerzeug für Sammlung/Dekorativ/Geschenk";
const SPOON_REST: &str = "Löffelhalter Topf Silikon – Kochlöffel Halter & sichere Löffelablage für Pfanne, kleckerfrei kochen, hitzebeständig, spülmaschinenfest, Pfannenrand Halter holo® Küchengadget,Küchenhelfer,DeutscheMarke";
const SPOON_POT: &str = "Spoon Holder Pot, Adjustable Utensil Rest and Spill Stopper Cooking, 2.56x1.97 Inches, Funny Kitchen Gadget Organizer, Cute Non-Slip Accessory for Stove Top";

fn hot_pots_and_lookalikes() -> Vec<Article> {
    [CIRONI, CHAFING, PEKING, LIGHTER, SPOON_REST, SPOON_POT]
        .iter()
        .enumerate()
        .map(|(i, t)| article(&format!("B00000000{i}"), t))
        .collect()
}

#[test]
fn three_sellers_hot_pots_are_twins_of_each_other() {
    let list = hot_pots_and_lookalikes();
    let words = TitleWords::of(&list);
    for (a, b) in [(0, 1), (0, 2), (1, 2)] {
        assert!(words.twins(&list[a], &list[b]), "{} / {}", list[a].name, list[b].name);
    }
}

#[test]
fn lookalikes_sharing_a_few_words_are_not_twins() {
    let list = hot_pots_and_lookalikes();
    let words = TitleWords::of(&list);
    // The lighter is unusual copper and brass, the spoon rest a Topf, the spoon holder a Pot.
    for (a, b) in [(0, 3), (2, 3), (2, 4), (0, 4), (0, 5), (1, 5), (3, 4)] {
        assert!(!words.twins(&list[a], &list[b]), "{} / {}", list[a].name, list[b].name);
    }
}

#[test]
fn another_colour_of_a_short_title_is_a_twin() {
    let list = vec![article("B000000001", "Blumentopf EPOCCO 30x90 cm Braun"), article("B000000002", "Blumentopf EPOCCO 30x90 cm Sand")];
    let words = TitleWords::of(&list);
    assert!(words.twins(&list[0], &list[1]));
}

#[test]
fn words_most_of_the_list_uses_make_no_twins() {
    let paste = article("B000000001", "Zahnpastaspender und Halter für Zahnbürste und Zahnpasta");
    let sieve = article("B000000002", "Abflusssieb für Abflusssieb und Gadgets");
    let alone = vec![paste.clone(), sieve.clone()];
    assert!(TitleWords::of(&alone).twins(&alone[0], &alone[1]), "für and und alone would pair them");
    let mut list = alone;
    list.extend((0..COMMON).map(|i| article(&format!("B1000000{i:02}"), &format!("Ding {i} für Kinder und Erwachsene"))));
    assert!(!TitleWords::of(&list).twins(&paste, &sieve));
}

#[test]
fn an_article_from_another_list_is_nobodys_twin() {
    let list = hot_pots_and_lookalikes();
    let words = TitleWords::of(&list[..1]);
    assert!(!words.twins(&list[0], &list[1]));
}

#[test]
fn words_are_compared_by_their_first_six_letters() {
    let expected: HashSet<String> = ["kupfer", "gesche", "für", "den", "peking"].map(String::from).into();
    assert_eq!(title_words("Kupferkohle, 2 Geschenke für den Pekinger am 1.12."), expected);
}
