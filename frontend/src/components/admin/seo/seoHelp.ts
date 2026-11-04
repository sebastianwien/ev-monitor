/** Anleitung je Ansicht und Erklärung je Befund, mit SEO-Standardbegriffen. Zielgruppe: Marketing. */
export type SeoTopic = 'overview' | 'keywords' | 'landingpages' | 'acquisition' | 'audit' | 'serp'

export interface TopicHelp {
  /** Die eine Frage, die die Ansicht beantwortet. */
  question: string
  read: string[]
  act: string[]
  /** Schlüssel aus GLOSSARY, die hier wichtig sind. */
  terms: string[]
}

export const TOPIC_HELP: Record<SeoTopic, TopicHelp> = {
  overview: {
    question: 'Was ist diese Woche zu tun, und wohin entwickelt sich der Traffic?',
    read: [
      '"Top-Aufgaben" sind die Befunde mit dem größten Klick-Potenzial. Die Zahl ist eine Schätzung zusätzlicher Klicks im Zeitraum.',
      'Die Kacheln vergleichen den Zeitraum mit dem gleich langen Zeitraum davor. Grün ist besser, rot schlechter. Non-Brand-Klicks sind die eigentliche SEO-Leistung.',
      'Gewinner und Verlierer zeigen, welche Landingpages sich am stärksten bewegt haben. Channels zeigen, welcher Kanal Sign-ups bringt.',
    ],
    act: [
      'Starte jede Woche hier und arbeite die Top-Aufgaben von oben ab.',
      'Verlierer prüfen: Site Audit (technischer Fehler?) und Keywords der Seite (Ranking verloren?).',
      'Notiere vor jeder Änderung, welche Zahl sich bewegen soll, und prüfe sie 28 Tage später mit demselben Zeitraum.',
    ],
    terms: ['potential', 'impressions', 'clicks', 'ctr', 'position', 'brand', 'googleEntries', 'searchEntries', 'registrations', 'organicCr', 'channel'],
  },
  landingpages: {
    question: 'Welche Landingpage verdient als Nächstes Arbeit?',
    read: [
      'Eine Zeile je Landingpage: links die Search-Console-Werte vor dem Klick, rechts Plausible und Sign-ups nach dem Klick. Seiten hinter dem Login fehlen.',
      'Der Seitentyp grenzt ein (Modellseiten, Content ...). Die Befund-Filter sind fertige Diagnosen mit Handlungsempfehlung.',
      'Klick auf eine Zeile zeigt die Keywords, für die die Seite rankt. Die brauchst du für Title und Meta Description.',
      '"Modellseiten: Nachfrage und Datenbasis" stellt Suchnachfrage je Modell neben die Fahrzeugdaten, die wir dazu haben.',
    ],
    act: [
      'Sortiere nach Klick-Potenzial und nimm dir pro Woche eine Seite mit "Low CTR" vor.',
      'Title und Meta Description so umschreiben, dass die wichtigsten Keywords der Seite vorkommen und ein klarer Nutzen drinsteht.',
      'Export als CSV für Excel.',
    ],
    terms: ['pageType', 'impressions', 'clicksDelta', 'ctr', 'position', 'potential', 'entries', 'bounce', 'cta', 'registrations'],
  },
  keywords: {
    question: 'Für welche Keywords ranken wir, und wo liegt Potenzial?',
    read: [
      'Eine Zeile je Keyword, zusammengefasst über alle unsere Seiten. "Beste Landingpage" ist die mit den meisten Klicks.',
      'Die Keyword-Cluster oben fassen Keywords nach Suchintention zusammen. Klick auf ein Cluster filtert die Tabelle.',
      'Klick auf eine Zeile zeigt, welche unserer Seiten für das Keyword ranken.',
    ],
    act: [
      '"Striking Distance": die beste Landingpage gezielt auf das Keyword ausbauen (H1, Zwischenüberschrift, Antwort im Text). Ziel ist Seite 1.',
      '"Keyword-Kannibalisierung": eine Seite als Hauptseite festlegen, die anderen intern auf sie verlinken.',
      '"SEA-Kandidat": Keyword für eine kleine Google-Ads-Testkampagne. Die Landingpage konvertiert nachweislich, organisch ranken wir zu weit hinten.',
    ],
    terms: ['brand', 'topic', 'impressions', 'clicks', 'clicksDelta', 'position', 'potential', 'volume', 'cpc', 'share'],
  },
  acquisition: {
    question: 'Welcher Channel und welche Kampagne bringen Sign-ups?',
    read: [
      'Oben die Channels, darunter jede Quelle, jedes Medium und jede Kampagne einzeln. Besucher und Conversion Rate kommen aus Plausible.',
      'Sign-ups kommen aus unserer Datenbank, aber nur, wenn der Link UTM-Parameter trug oder der Besuch von Google kam. Sonst steht dort "-".',
      'Organic Search ist die Vergleichszeile für alles Bezahlte und alle Foren-Beiträge.',
    ],
    act: [
      'Verlinke ev-monitor.net nie ohne UTM-Parameter. Der UTM-Builder erzeugt sie in der richtigen Schreibweise.',
      'Vergleiche Channels über Sign-ups je 1 000 Besucher, nicht über die Besucherzahl allein.',
    ],
    terms: ['channel', 'utm', 'visitors', 'cta', 'registrations'],
  },
  audit: {
    question: 'Kann Google unsere Seiten technisch einwandfrei crawlen und indexieren?',
    read: [
      'Jede Nacht crawlt ein Prüfprogramm alle Seiten aus der Sitemap so, wie Google sie bekommt.',
      'Fehler kosten Rankings und gehören zuerst behoben, Hinweise sind Feinschliff. Klick auf eine Zeile zeigt alle Befunde der Seite.',
      'Duplicate URLs unten kommen aus der Search Console: Google führt dieselbe Seite unter mehreren Schreibweisen.',
    ],
    act: [
      'Fehler an die Entwicklung geben, mit Befund-Text und Seite.',
      'Title-Tag zu lang oder Meta Description zu kurz kannst du selbst formulieren und als Textvorschlag weitergeben.',
    ],
    terms: ['noindex', 'canonical', 'duplicate', 'jsonld', 'prerender', 'cwv'],
  },
  serp: {
    question: 'Wer rankt bei unseren Keywords vor uns, und welche Keywords lohnen sich?',
    read: [
      'Einmal pro Woche holen wir für unsere wichtigsten Keywords die organischen Top 10 der Google-SERP (Deutschland, Handy).',
      '"SERP je Keyword" zeigt unser Ranking und wer auf Platz 1 steht. Klick auf eine Zeile zeigt die ganze Top 10.',
      '"Keyword-Ideen" sind verwandte Suchen mit Suchvolumen, für die wir noch nicht ranken.',
    ],
    act: [
      'Bei Keywords mit hohem Suchvolumen und niedrigem Impression Share die Seite auf Platz 1 ansehen: Was beantwortet sie, was wir nicht beantworten?',
      'Keyword-Ideen mit hohem Volumen und passendem Thema sind Kandidaten für neue Inhalte oder Abschnitte auf Modellseiten.',
    ],
    terms: ['serp', 'volume', 'cpc', 'share', 'position'],
  },
}

/** Erklärung je Befund: was er bedeutet und was zu tun ist. */
export const FINDINGS: Record<string, { label: string; tone: 'good' | 'warn' | 'bad'; meaning: string; action: string }> = {
  UNDERRATED: {
    label: 'Low CTR', tone: 'warn',
    meaning: 'Die Seite rankt auf Position 4 bis 15, die CTR liegt aber unter der Hälfte dessen, was auf dieser Position üblich ist.',
    action: 'Title und Meta Description umschreiben: Keywords der Seite aufnehmen (Zeile aufklappen), konkreten Nutzen nennen, unter 60 bzw. 155 Zeichen bleiben.',
  },
  LEAK: {
    label: 'High Bounce', tone: 'bad',
    meaning: 'Viele kommen über Google, aber die meisten springen sofort ab, und kaum jemand konvertiert.',
    action: 'Prüfen, ob die Seite die Suchintention erfüllt. Antwort weiter nach oben, klaren Call-to-Action anbieten.',
  },
  MONEY: {
    label: 'Top Converter', tone: 'good',
    meaning: 'Gehört zu den Landingpages mit den meisten Sign-ups je Klick.',
    action: 'Nicht ohne Grund umbauen. Intern hierher verlinken und als Vorbild für schwächere Seiten nehmen.',
  },
  DEAD: {
    label: 'Lost Page', tone: 'bad',
    meaning: 'Hatte in den 90 Tagen davor Impressionen, im gewählten Zeitraum keine einzige mehr.',
    action: 'Im Site Audit prüfen (noindex, Canonical, HTTP-Status). Bei Sprachvarianten (/en, /se ...) ist das seit Oktober 2026 gewollt.',
  },
  STRIKING_DISTANCE: {
    label: 'Striking Distance', tone: 'warn',
    meaning: 'Wir ranken für das Keyword auf Position 5 bis 20, also knapp vor oder hinter Seite 1, und es wird oft gesucht.',
    action: 'Die beste Landingpage gezielt ausbauen: Keyword in H1 und Text, die Frage dahinter direkt beantworten, intern verlinken.',
  },
  CANNIBALIZED: {
    label: 'Keyword-Kannibalisierung', tone: 'bad',
    meaning: 'Mehrere unserer eigenen Seiten ranken für dasselbe Keyword und nehmen sich gegenseitig Rankings weg.',
    action: 'Zeile aufklappen, eine Seite als Hauptseite festlegen und die anderen intern auf sie verlinken.',
  },
  ADWORDS: {
    label: 'SEA-Kandidat', tone: 'good',
    meaning: 'Non-Brand-Keyword, organisch schlechter als Position 10, aber die Landingpage konvertiert überdurchschnittlich.',
    action: 'Für eine kleine Testkampagne in Google Ads vormerken. CPC steht in der Spalte, sobald SERP-Daten da sind.',
  },
  CONTENT_GAP: {
    label: 'Nachfrage ohne Daten', tone: 'warn',
    meaning: 'Das Modell wird überdurchschnittlich oft gesucht, wir haben aber keinen einzigen Ladevorgang dazu. Die Seite hat dadurch wenig eigenen Inhalt.',
    action: 'Fahrer dieses Modells gewinnen: Forum, Facebook-Gruppe, Kooperation. Jede echte Fahrt macht die Seite wertvoller.',
  },
  INVISIBLE: {
    label: 'Daten ohne Sichtbarkeit', tone: 'bad',
    meaning: 'Wir haben mindestens 10 Ladevorgänge zu dem Modell, aber die Seite hat kaum Impressionen oder rankt hinter Position 20. Oft heißt das auch: Kaum jemand sucht nach dem Modell.',
    action: 'Im Site Audit prüfen und die Seite von Marken- und verwandten Modellseiten intern verlinken.',
  },
}

export const TONE_CLASS: Record<'good' | 'warn' | 'bad', string> = {
  good: 'bg-emerald-900/60 text-emerald-200',
  warn: 'bg-amber-900/60 text-amber-200',
  bad: 'bg-red-900/60 text-red-200',
}
