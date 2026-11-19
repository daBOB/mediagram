# Lebensweg: the fields

Status: draft for the user's review · 2026-11-14 · Phase 3

Every field keeps the effect and amount the bots balanced (phase 2). This document decides what a field says and
how it looks. Tone (user, 2026-11-14): **warm, set on the mountain**. Everyday moments, and where it fits naturally the
story happens at the place on the Lebensberg where the field lies.

## Rules for the texts

- **Title** up to 3 words; the TV shows it large when a car lands. **Story**: one sentence, at most 56 characters.
- No numbers in the texts: the TV and phone add the effect line from the rules ("−5.000 · Autoversicherung zahlt").
- Fields whose title is a rule keep that title on every copy, so players learn them: Zahltag, Steuern,
  Gehalt tauschen?, Abendschule, Nochmal drehen. Only their story changes.
- A doubled field (² in the tables) gets a story of its own, never the original's again.
- Partners and children are never named as Mama or Papa: any avatar can drive any car.
- Our own texts throughout; none are the original game's wording.

## The places on the mountain

The road climbs the terraces from a spring meadow to the snow. The waterfall's stream crosses every terrace under the
road and ends in a pond at the foot.

| Terrace | Stretches | Ground | Places the texts use |
|---|---|---|---|
| 0 Jugend | Start, Studium, Ausbildung, Berufswahl | spring meadow | school, pond at the waterfall's foot, uni campus, workshop, the valley below |
| 1 Beruf & Heim | Beruf, Hochzeit, Erste Wohnung, Hauskauf | early summer | office district (bank, shops, market square), waterfall pool, chapel |
| 2 Familie | Kinder, Familienweg, Abenteuerweg | summer | family square, house with garden, campsite, canoe water below the fall |
| 3 Lebensmitte | Lebensmitte, Kreuzung, Riskanter / Sicherer Weg | late summer | village and village hall, mine with tunnels and a gravel track |
| 4 Goldene Jahre | Goldene Jahre | golden autumn | alpine pasture, mountain hut, ridge, autumn storms |
| 5 Ruhestand naht | Ruhestand naht, Villa am See, Landhaus | snow | summit cross, frozen lake, gorge, spa house; villa by the lake, farm |

## Tiles (proposal; a rendered sheet follows for the pick)

192 fields in 9 tile kinds. A car passing a field sees only the tile; the title and story show when it lands.

| Tile | Effects | Fields | Proposal |
|---|---|---|---|
| Zahltag | payday, also when passing | 27 | green, banknote; the only tile with a raised rim, so it reads as "passing pays" |
| Lebensmarke | life token | 38 | gold, star |
| Kind | baby, twins | 11 | cream, pram (two for twins) |
| Geld bekommen | collect | 19 | white, green coin with plus |
| Geld zahlen | pay, taxes | 12 | white, red coin with minus (taxes: a stamped letter) |
| Berufsfeld | money to the player in a career | 38 | the career's own colour and symbol: the tile shows who collects |
| Schaden | car or house damage, insurance pays | 11 | orange, car or house with a crack |
| Sonderfeld | swap salary 9, spin again 8, stocks 4, night school 3, new job 3, miss a turn 3 | 30 | blue, one symbol each |
| STOP | Start, Berufswahl, Hochzeit, Hauskauf, two homes | 6 | large disc, red and white |

The game code knows only 7 kinds today (career fields, taxes and damage count as "pay"); the tiles need career,
damage and special as their own kinds.

## STOPs and forks

| Where | Age | Place | What happens |
|---|---|---|---|
| Start | 18 | schoolyard at the foot, by the pond | First choice: **Studium** (uni, the fees of 52,000 as loans, the degree opens Buchhaltung and Medizin) or **Ausbildung** (workshop, career and salary drawn at once). |
| Berufswahl | 22 | job fair at the end of the uni street | Three career cards, keep one; three salary cards, keep one. Cars from the Ausbildung already have a career and drive through. |
| Hochzeit | 27 | chapel | The partner gets into the car; a life token. |
| Hauskauf | 31 | building plot | A house card is drawn and paid; the player decides on home insurance. Where the house then stands is part of the cards phase. |
| Fork after Kinder | 37 | family square | **Familienweg** (house with garden, more children, damage to the house) or **Abenteuerweg** (campsite, outdoors, a stock tip). |
| Fork after Kreuzung | — | village edge | **Riskanter Weg** (the mine: big wins, big costs) or **Sicherer Weg** (the village: calm, steady). |
| Villa am See / Landhaus | 67 | summit | **Villa am See**: once everyone has retired, the richest player at the villa takes the set-aside life tokens. **Landhaus**: one more life token now, nothing to win or lose later. |

## Open points

1. **Every copy sits right next to its original.** The 44 doubled fields always follow the space they repeat, so the
   board has 6 places with two paydays in a row (#29–30, 56–57, 102–103, 141–142, 151–152, 161–162), two "Nochmal
   drehen" twice, and four life tokens in a row on the Abenteuerweg. Proposal: put each copy in the largest gap
   between fields of its kind on that stretch, then rerun the bots (the balance should hold; the mix per stretch
   stays the same).
2. **Props beside fields** (small scenes where the story is visible from the overview), candidates: #11 rock on the
   pass road, #27 colleagues in the waterfall pool, #62 the stream over its bank, #69 fallen apple tree, #74 campfire
   with smoke, #116 mine cart with gold, #120 open chest in a garden, #131 alpine hut, #134 landslide, #153 open-air
   stage, #166 snowman, #172 hole in the lake ice, #175 dog sled. Each is a Blender model with asset QA.
3. **Tile looks**: rendered sheet of the 9 tiles on the board, overview and focus, for the pick.

## All fields

² = the second copy of a space on the stretched board. "Space" is the original's space number.

### Studium

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 1 | 1 | +20.000 | Stipendium | Die Uni am Hang glaubt an dich. |
| 2 | 2 | −5.000 | Bücher und Skripte | Die Bibliothek hat nur ein Exemplar. |
| 3 | 2² | −5.000 | Semesterbeitrag | Mit Busticket bis hoch zur Passstraße. |
| 4 | 3 | Lebensmarke | Neue Freunde | Aus der Lerngruppe auf der Wiese wird ein Stammtisch. |
| 5 | 4 | +5.000 | Nebenjob im Café | Milchschaum für den ganzen Campus. |
| 6 | 4² | +5.000 | Nachhilfe | Mathe für die Kinder unten im Tal. |
| 7 | 5 | Einmal aussetzen | Prüfungswoche | Kein Schritt vor die Tür, bis alles sitzt. |
| 8 | 6 | Lebensmarke | Auslandssemester | Ein Jahr fort, und der Berg sieht danach anders aus. |
| 9 | 7 | −5.000 | Ferien am Meer | Einmal raus aus den Bergen: zwei Wochen Strand. |
| 10 | 8 | Lebensmarke | Bestnote | Die Professorin hängt deine Arbeit ans schwarze Brett. |
| 11 | 9 | Auto −5.000, versichert 0 | Steinschlag | Auf der Passstraße zur Uni trifft ein Stein die Scheibe. |
| 12 | 10 | Lebensmarke | Abschlussfeier | Hüte fliegen über die Campuswiese. |
| 13 | 10² | Lebensmarke | Das Zeugnis | Die Eltern sind extra den Berg hochgefahren. |

### Ausbildung

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 14 | 12 | Zahltag | Zahltag | Der erste Lohn aus der Werkstatt. |
| 15 | 13 | −5.000 | Die erste Miete | Ein Zimmer über der Werkstatt, mit Blick ins Tal. |
| 16 | 14 | +10.000 | Gesellenpreis | Dein Werkstück gewinnt den Preis der Innung. |
| 17 | 14² | +10.000 | Kleine Erbschaft | Tante Hilde vermacht dir ihr Sparbuch. |

### Beruf

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 19 | 15 | Zahltag | Zahltag | Das erste Gehalt aus dem Büroviertel. |
| 20 | 16 | Lebensmarke | Ein Haustier | Ein Kater aus dem Tierheim regiert ab jetzt die Wohnung. |
| 21 | 17 | Einmal aussetzen | Verfahren | Falsch abgebogen und mitten im Feierabendstau. |
| 22 | 17² | Einmal aussetzen | Grippe | Eine Woche Sofa, Tee und Taschentücher. |
| 23 | 18 | Lebensmarke | Geburtstagsfeier | Die Kollegen schmücken den ganzen Flur. |
| 24 | 19 | 5.000 an Medizin | Skiunfall | Der erste Schnee am Hang, der erste Gips. |
| 25 | 20 | +10.000 | Firmenlauf gewonnen | Einmal rund ums Büroviertel, und du bist vorn. |
| 26 | 21 | Lebensmarke | Museumsbesuch | Mittagspause zwischen alten Meistern. |
| 27 | 21² | Lebensmarke | Feierabend am Wasserfall | Das ganze Büro springt ins Becken. |
| 28 | 22 | Lebensmarke | Mit dem Rad | Bergab ein Traum, bergauf ein Training. |
| 29 | 23 | Zahltag | Zahltag | Die Bank im Büroviertel meldet: Gehalt ist da. |
| 30 | 23² | Zahltag | Zahltag | Und obendrauf die Quartalsprämie. |
| 31 | 24 | Auto −15.000, versichert 0 | Auto rollt weg | Handbremse vergessen, und die Straße ist steil. |

### Erste Wohnung

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 33 | 26 | −10.000 | Hochzeitsfest | Die Kapelle war umsonst, das Buffet nicht. |
| 34 | 27 | Lebensmarke | Flitterwochen | Eine Woche Hütte, ihr zwei und der Gipfel. |
| 35 | 28 | 10.000 an Vertrieb | Neuer Computer | Gekauft im Laden am Marktplatz, gut beraten. |
| 36 | 29 | Auto −10.000, versichert 0 | Auffahrunfall | Ampel rot, Gedanken woanders. |
| 37 | 29² | Auto −10.000, versichert 0 | Hagelsturm | Hagel wie Murmeln, und das Auto steht draußen. |
| 38 | 30 | 10.000 an IT-Beratung | Technik-Seminar | Zwei Tage WLAN, Kabel und Kaffee. |
| 39 | 30² | 10.000 an IT-Beratung | WLAN streikt | Die IT-Beratung rettet den Filmabend. |
| 40 | 31 | Darf für 20.000 Beruf und Gehalt neu ziehen | Abendschule | Unten im Tal gibt es Kurse nach Feierabend. |
| 41 | 32 | Zahltag | Zahltag | Das Konto atmet auf. |
| 42 | 33 | Steuern an Buchhaltung | Steuern | Der Brief vom Finanzamt liegt im Kasten. |
| 43 | 34 | +50.000 | Sechs Richtige | Die Zahlen von Omas Geburtstag. |
| 44 | 35 | Lebensmarke | Schwiegereltern-Besuch | Kuchen, Fotoalben und ein Spaziergang zum Bach. |
| 45 | 35² | Lebensmarke | Einweihungsparty | Die Wohnung ist zu klein, die Stimmung zu gut. |

### Kinder

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 47 | 37 | Zahltag | Zahltag | Windeln, Brei und endlich Gehalt. |
| 48 | 38 | Neuer Beruf und neues Gehalt | Job verloren | Die Firma zieht weg. Zeit für etwas Neues. |
| 49 | 39 | Kind | Ein Junge | Er schreit lauter als der Wasserfall. |
| 50 | 39² | Kind | Ein Junge | Mit deiner Nase und einem eigenen Kopf. |
| 51 | 40 | 5.000 an Vertrieb | Kinderzimmer | Bettchen, Wickeltisch und ein Mobile mit Bergen. |
| 52 | 40² | 5.000 an Vertrieb | Kinderwagen | Geländetauglich, für die steilen Wege am Berg. |
| 53 | 41 | Kind | Ein Mädchen | Ihr erstes Wort: Auto. |
| 54 | 42 | +10.000 | Talentshow gewonnen | Dein Jodler hallt bis ins Tal. |
| 55 | 42² | +10.000 | Flohmarkt | Der Dachboden war voller Schätze. |
| 56 | 43 | Zahltag | Zahltag | Die Spardose der Kinder bekommt auch etwas. |
| 57 | 43² | Zahltag | Zahltag | Gerade rechtzeitig vor dem Kindergeburtstag. |
| 58 | 44 | Zwillinge | Zwillinge | Doppelt so laut, doppelt so schön. |
| 59 | 45 | 20.000 an Profisport | Karten fürs Endspiel | Plätze direkt hinter dem Tor. |
| 60 | 46 | Kind | Ein Mädchen | Geboren in der Nacht des ersten Gewitters. |
| 61 | 47 | 5.000 an Bühne | Weihnachtsmärchen | Erste Reihe im Theater, mit großen Augen. |
| 62 | 48 | Haus −40.000, versichert 0 | Wasser im Keller | Der Bach vom Wasserfall nimmt eine Abkürzung. |

### Familienweg

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 63 | 49 | 5.000 an Medizin | Vorsorge | Alle einmal zur Kinderärztin. |
| 64 | 50 | Darf Gehalt mit jemandem tauschen | Gehalt tauschen? | Am Gartenzaun vergleicht man so einiges. |
| 65 | 51 | Kind | Ein Junge | Er kommt, als gerade alle schlafen. |
| 66 | 51² | Kind | Ein Junge | Das Haus wird voll und laut. |
| 67 | 52 | Zahltag | Zahltag | Genug für ein neues Klettergerüst. |
| 68 | 53 | Kind | Ein Mädchen | Die Geschwister streiten, wer sie halten darf. |
| 69 | 54 | Haus −15.000, versichert 0 | Baum aufs Dach | Der alte Apfelbaum übersteht den Sturm nicht. |
| 70 | 54² | Haus −15.000, versichert 0 | Rohrbruch | Das Bad steht unter Wasser. |
| 71 | 55 | Lebensmarke | Ehrlicher Finder | Du bringst eine volle Geldbörse zurück. |

### Abenteuerweg

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 72 | 56 | 5.000 an Vertrieb | Neues Zelt | Sturmfest, für vier und den Hund. |
| 73 | 57 | Eine Aktie gratis | Börsentipp | Am Lagerfeuer verrät jemand einen Tipp. |
| 74 | 58 | Lebensmarke | Lagerfeuer | Stockbrot, Sterne und Geschichten. |
| 75 | 58² | Lebensmarke | Familienpicknick | Decke auf der Bergwiese, Murmeltiere schauen zu. |
| 76 | 59 | Lebensmarke | Gipfeltour | Alle zusammen bis zum Kreuz. |
| 77 | 59² | Lebensmarke | Kanutour | Unten am Wasserfall: alle nass, alle glücklich. |
| 78 | 60 | Zahltag | Zahltag | Auch Abenteurer bekommen Gehalt. |
| 79 | 61 | Auto −15.000, versichert 0 | Auto gestohlen | Der Parkplatz am Wanderweg ist leer. |

### Lebensmitte

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 80 | 62 | Darf Gehalt mit jemandem tauschen | Gehalt tauschen? | Beim Klassentreffen geht es ums Gehalt. |
| 81 | 62² | Darf Gehalt mit jemandem tauschen | Gehalt tauschen? | Ein Kollege bietet dir einen Tausch an. |
| 82 | 63 | Lebensmarke | Gemeinderat | Du kandidierst für den Rat am Berg. |
| 83 | 64 | Lebensmarke | Wählen gegangen | Mit der ganzen Familie ins Wahllokal. |
| 84 | 65 | Kind | Nachzügler | Mit dem hat keiner mehr gerechnet. |
| 85 | 66 | Zahltag | Zahltag | Die Raten fürs Haus sind gedeckt. |
| 86 | 67 | 25.000 an IT-Beratung | Neues Heimnetz | Kabel durchs ganze Haus, endlich Empfang im Keller. |
| 87 | 68 | Darf für 20.000 Beruf und Gehalt neu ziehen | Abendschule | Noch einmal die Schulbank drücken. |
| 88 | 68² | Darf für 20.000 Beruf und Gehalt neu ziehen | Abendschule | Ein Kurs, der alles ändern könnte. |
| 89 | 69 | Lebensmarke | Erste-Hilfe-Kurs | Für den Ernstfall am Berg. |
| 90 | 70 | 20.000 an Kunst | Kunstauktion | Ein Bild vom Wasserfall, ersteigert. |
| 91 | 71 | Zahltag | Zahltag | Mitten im Leben, mitten im Monat. |
| 92 | 72 | Lebensmarke | Benefizlauf | Du hilfst an der Wasserstation. |
| 93 | 73 | +10.000 | Fotowettbewerb | Dein Foto vom Nebel über dem Tal gewinnt. |
| 94 | 73² | +10.000 | Alte Briefmarken | Opas Album ist mehr wert als gedacht. |
| 95 | 74 | Nochmal drehen, außer allein vorn | Nochmal drehen | Rückenwind aus dem Tal schiebt dich an. |
| 96 | 75 | Darf Gehalt mit jemandem tauschen | Gehalt tauschen? | Die Firma nebenan sucht Leute. |
| 97 | 76 | Steuern an Buchhaltung | Steuern | Steuererklärung bis spät in die Nacht. |
| 98 | 76² | Steuern an Buchhaltung | Steuern | Und die Nachzahlung kommt doch noch. |
| 99 | 77 | 25.000 an Profisport | Tenniscamp | Eine Woche Sand, Schweiß und Aufschlag. |
| 100 | 77² | 25.000 an Profisport | Skipass | Für die ganze Familie, die ganze Saison. |
| 101 | 78 | 25.000 an IT-Beratung | Computer gespendet | Für die Dorfschule unten im Tal. |
| 102 | 79 | Zahltag | Zahltag | Das Gehalt kommt, der Herbst auch. |
| 103 | 79² | Zahltag | Zahltag | Und schon ist wieder Monatsende. |
| 104 | 80 | Eine Aktie zurück an die Bank | Börsencrash | Die Kurse fallen schneller als der Wasserfall. |

### Kreuzung

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 105 | 82 | 5.000 an Lehramt | Kita-Beitrag | Für den Kindergarten unten im Dorf. |
| 106 | 83 | +80.000 | Bestseller | Dein Buch über den Berg liegt in jedem Fenster. |
| 107 | 84 | Zwillinge | Zwillinge adoptiert | Zwei kleine Neue, die sofort dazugehören. |
| 108 | 84² | Zwillinge | Zwei Pflegekinder | Geschwister finden bei euch ein Zuhause. |

### Riskanter Weg

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 109 | 85 | 15.000 an Bühne | Musical-Investition | Ein Musical über Bergleute. Was soll schiefgehen? |
| 110 | 86 | Zahltag | Zahltag | Lohn aus der Grube, bar auf die Hand. |
| 111 | 87 | Lebensmarke | Sportverein | Du trainierst die Jugend am Hang. |
| 112 | 87² | Lebensmarke | Grubenwehr | Du holst einen Kumpel aus dem Stollen. |
| 113 | 88 | 35.000 an Kunst | Familienporträt | In Öl, mit allen, auch dem Hund. |
| 114 | 89 | Darf Gehalt mit jemandem tauschen | Gehalt tauschen? | Ein Geschäftspartner macht ein Angebot. |
| 115 | 90 | 25.000 an Vertrieb | Geländewagen | Für die Schotterpiste zur Mine. |
| 116 | 91 | +75.000 | Goldader | Tief im Stollen glänzt es. |
| 117 | 91² | +75.000 | Steuererstattung | Das Finanzamt hat sich verrechnet. Für dich. |

### Sicherer Weg

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 118 | 92 | 15.000 an Polizei | Polizeiball | Ihr richtet den Ball im Dorfsaal aus. |
| 119 | 93 | Zahltag | Zahltag | Pünktlich wie immer. |
| 120 | 94 | +80.000 | Schatz im Garten | Beim Umgraben klirrt eine alte Kiste. |
| 121 | 94² | +80.000 | Bausparvertrag | Nach all den Jahren endlich fällig. |
| 122 | 95 | Steuern an Buchhaltung | Steuern | Auch der sichere Weg führt am Finanzamt vorbei. |

### Goldene Jahre

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 123 | 96 | Zahltag | Zahltag | Goldener Herbst, volles Konto. |
| 124 | 97 | 25.000 an Kunst | Kunstspende | Für die Ausstellung über den Berg. |
| 125 | 97² | 25.000 an Kunst | Gartenskulptur | Ein Steinbock aus Bronze, lebensgroß. |
| 126 | 98 | Lebensmarke | Wanderweg aufgeräumt | Drei Säcke Müll vom Pfad am Wasserfall. |
| 127 | 99 | +95.000 | Quizshow gewonnen | Die letzte Frage war über Berge. |
| 128 | 100 | 5.000 an Lehramt | Ferienkurs | Die Enkel lernen schwimmen. |
| 129 | 101 | Lebensmarke | Spieleabend | Die ganze Familie am großen Tisch. |
| 130 | 102 | Lebensmarke | Gebärdensprache | Gelernt für die Nachbarin, die nicht hört. |
| 131 | 103 | −90.000 | Almhütte gekauft | Mit Blick über alle Terrassen. |
| 132 | 104 | Zahltag | Zahltag | Die Jahre im Beruf zahlen sich aus. |
| 133 | 105 | Haus −50.000, versichert 0 | Einbruch | Im Urlaub fehlt plötzlich das Silber. |
| 134 | 105² | Haus −50.000, versichert 0 | Erdrutsch | Der Hang rutscht bis an die Terrasse. |
| 135 | 106 | +10.000 | Zivilcourage | Du stellst dich vor jemanden, der Hilfe braucht. |
| 136 | 107 | Darf Gehalt mit jemandem tauschen | Gehalt tauschen? | Ein Angebot kurz vor dem Ruhestand. |
| 137 | 108 | 30.000 an Profisport | Fitnessraum | Ein Laufband mit Blick auf den Gipfel. |
| 138 | 109 | Eine Aktie zurück an die Bank | Börsencrash | Herbststurm an der Börse. |
| 139 | 109² | Eine Aktie zurück an die Bank | Firma pleite | Deine Aktie ist nur noch Papier. |
| 140 | 110 | Haus −125.000, versichert 0 | Herbststurm | Er deckt das halbe Dach ab. |
| 141 | 111 | Zahltag | Zahltag | Laub fegen kann warten, erst zur Bank. |
| 142 | 111² | Zahltag | Zahltag | Mit Zulage für die treuen Jahre. |
| 143 | 112 | 25.000 an Medizin | Neues Knie | Der Weg nach oben ist wieder drin. |
| 144 | 113 | Steuern an Buchhaltung | Steuern | Das Finanzamt hat auch im Herbst geöffnet. |
| 145 | 114 | 30.000 an Vertrieb | Segelboot | Für den See ganz oben. |
| 146 | 114² | 30.000 an Vertrieb | Wohnmobil | Endlich die Berge der anderen sehen. |
| 147 | 115 | 35.000 an Profisport | Golfturnier | Achtzehn Löcher am Hang, dein Name auf dem Banner. |
| 148 | 116 | Neuer Beruf und neues Gehalt | Midlife-Crisis | Noch einmal ganz von vorn. |
| 149 | 116² | Neuer Beruf und neues Gehalt | Firma verkauft | Die neuen Besitzer bringen eigene Leute mit. |
| 150 | 117 | Nochmal drehen, außer allein vorn | Nochmal drehen | Ein Aufwind am Grat. |
| 151 | 118 | Zahltag | Zahltag | Das Konto wird golden wie die Wiesen. |
| 152 | 118² | Zahltag | Zahltag | Noch ein Gehalt, bevor der Schnee kommt. |
| 153 | 119 | 100.000 an Bühne | Open Air | Ein Konzert auf der Almwiese, du veranstaltest. |
| 154 | 120 | Darf Gehalt mit jemandem tauschen | Gehalt tauschen? | Am Stammtisch wird verhandelt. |
| 155 | 120² | Darf Gehalt mit jemandem tauschen | Gehalt tauschen? | Ein alter Freund schlägt einen Tausch vor. |
| 156 | 121 | Lebensmarke | Suppenküche | Du kochst für alle, die es brauchen. |

### Ruhestand naht

| # | Space | Wirkung | Titel | Geschichte |
|---|---|---|---|---|
| 157 | 122 | Nochmal drehen, außer allein vorn | Nochmal drehen | Frischer Schnee, frischer Schwung. |
| 158 | 122² | Nochmal drehen, außer allein vorn | Nochmal drehen | Die Schneeketten greifen. |
| 159 | 123 | 100.000 an Medizin | Kur im Sanatorium | Drei Wochen Bergluft, Moorbad und Ruhe. |
| 160 | 124 | 50.000 an Lehramt | Studium der Kinder | Die Kinder studieren, du zahlst. |
| 161 | 125 | Zahltag | Zahltag | Der erste Schnee, das Gehalt ist pünktlich. |
| 162 | 125² | Zahltag | Zahltag | Mit Weihnachtsgeld obendrauf. |
| 163 | 126 | Steuern an Buchhaltung | Steuern | Selbst im Schnee kommt Post vom Finanzamt. |
| 164 | 127 | Nochmal drehen, außer allein vorn | Nochmal drehen | Rückenwind vom Gletscher. |
| 165 | 128 | Lebensmarke | Am Gipfelkreuz | Ein stiller Tag für die, die fehlen. |
| 166 | 128² | Lebensmarke | Schneemann | Der größte im Dorf, gebaut mit den Enkeln. |
| 167 | 129 | 125.000 an Kunst | Kunstausstellung | Bilder vom Berg, gesponsert von dir. |
| 168 | 130 | Lebensmarke | Klammwanderung | Durch die Schlucht unter dem Wasserfall. |
| 169 | 130² | Lebensmarke | Polarlichter | Einmal im Leben, über dem verschneiten Gipfel. |
| 170 | 131 | Darf Gehalt mit jemandem tauschen | Gehalt tauschen? | Ein letzter Tausch vor der Rente? |
| 171 | 132 | Zahltag | Zahltag | Das Heizöl ist bezahlt, der Rest bleibt. |
| 172 | 133 | Lebensmarke | Eisangeln | Ein Loch im See, Tee aus der Thermoskanne. |
| 173 | 134 | Nochmal drehen, außer allein vorn | Nochmal drehen | Die Straße ist frisch geräumt. |
| 174 | 134² | Nochmal drehen, außer allein vorn | Nochmal drehen | Der Schneepflug fährt voraus. |
| 175 | 135 | 65.000 an Profisport | Hundeschlitten | Ein Gespann für das Rennen im Schnee. |
| 176 | 136 | Lebensmarke | Schneeschuhe | Leise Schritte durch den Neuschnee. |
| 177 | 137 | Zahltag | Zahltag | Der letzte Winter im Beruf. |
| 178 | 138 | Lebensmarke | Baum gepflanzt | Eine Zirbe, die noch die Enkel sehen. |
| 179 | 138² | Lebensmarke | Unterschlupf | Wanderer im Schneesturm finden bei dir Platz. |
| 180 | 139 | Nochmal drehen, außer allein vorn | Nochmal drehen | Die Sonne taut die Kurve frei. |
| 181 | 140 | Lebensmarke | Naturschutz | Eine Spende für die Murmeltiere am Gipfel. |
| 182 | 141 | 45.000 an IT-Beratung | Eigene Webseite | Für die Pension, die du eröffnen willst. |
| 183 | 141² | 45.000 an IT-Beratung | Smart Home | Die Heizung hört jetzt aufs Handy. |
| 184 | 142 | Lebensmarke | Ein Enkelkind | Die Nachricht kommt mit dem ersten Schnee. |
| 185 | 143 | Zahltag | Zahltag | Das letzte Gehalt. Fast. |
| 186 | 144 | 35.000 an Bühne | Silvesterball | Im Kurhaus, mit Orchester und Feuerwerk. |
| 187 | 144² | 35.000 an Bühne | Theaterabo | Logenplätze für die ganze Saison. |
| 188 | 145 | 45.000 an IT-Beratung | Start-up | Zwei junge Leute mit einer App für Bergführer. |
| 189 | 146 | +20.000 | Betriebsrente | Ein Brief, der dich lächeln lässt. |
