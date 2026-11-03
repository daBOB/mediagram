//! Built-in starter questions, used until a real pack is loaded.

use crate::quiz::Question;

fn q(category: &str, text: &str, correct: &str, wrong: [&str; 3]) -> Question {
    Question {
        category: category.into(),
        text: text.into(),
        correct: correct.into(),
        wrong: wrong.map(String::from),
        image: None,
    }
}

pub fn starter_questions() -> Vec<Question> {
    vec![
        q("Erdkunde", "Welcher Fluss fließt durch Dresden?", "Elbe", ["Spree", "Donau", "Main"]),
        q("Erdkunde", "Was ist die Hauptstadt von Australien?", "Canberra", ["Sydney", "Melbourne", "Perth"]),
        q("Erdkunde", "Wie viele Bundesländer hat Deutschland?", "16", ["15", "14", "17"]),
        q("Wissenschaft", "Welches Element hat das chemische Symbol Fe?", "Eisen", ["Fluor", "Phosphor", "Blei"]),
        q("Wissenschaft", "Welcher Planet ist der Sonne am nächsten?", "Merkur", ["Venus", "Mars", "Erde"]),
        q("Natur", "Wie viele Beine hat eine Spinne?", "8", ["6", "10", "12"]),
        q("Natur", "Welches ist das größte Tier der Erde?", "Blauwal", ["Afrikanischer Elefant", "Pottwal", "Walhai"]),
        q("Geschichte", "In welchem Jahr fiel die Berliner Mauer?", "1989", ["1987", "1990", "1991"]),
        q("Sport", "Wie viele Spieler einer Fußballmannschaft stehen auf dem Feld?", "11", ["10", "12", "9"]),
        q("Musik", "Wie viele Tasten hat ein modernes Klavier?", "88", ["76", "92", "64"]),
        q("Mathe", "Wie viel ist 7 × 8?", "56", ["54", "58", "64"]),
        q("Sprache", "Wie viele Buchstaben hat das Alphabet ohne Ä, Ö, Ü und ß?", "26", ["24", "28", "30"]),
        q("Technik", "Wofür steht das „L“ in WLAN?", "Local", ["Line", "Link", "Long"]),
        q("Essen", "Aus welcher Stadt stammt die Pizza Margherita?", "Neapel", ["Rom", "Mailand", "Venedig"]),
    ]
}
