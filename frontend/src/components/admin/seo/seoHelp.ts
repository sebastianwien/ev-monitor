/** Anleitung je Ansicht und Erklärung je Befund. Zielgruppe: Marketing ohne SEO-Vorwissen. */
export type SeoTopic = 'overview' | 'pages' | 'queries' | 'campaigns' | 'models' | 'tech' | 'competition'

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
    question: 'Geht es bei Google und auf der Website aufwärts oder abwärts?',
    read: [
      'Die Kacheln vergleichen den gewählten Zeitraum mit dem gleich langen Zeitraum davor. Grün ist besser, rot schlechter.',
      '"Bei Google" kommt aus der Google Search Console und hängt 2 bis 3 Tage hinterher. "Auf der Website" kommt aus Plausible. "Ergebnis" sind neue Konten aus unserer Datenbank.',
      'Im Verlauf sollten Klicks und Google-Einstiege fast deckungsgleich laufen. Gehen sie auseinander, prüfe den Unter-Tab Technik.',
    ],
    act: [
      'Starte jede Woche hier und arbeite die Liste "Was du jetzt tun kannst" von oben ab.',
      'Notiere vor jeder Änderung, welche Zahl sich bewegen soll, und prüfe sie 28 Tage später mit demselben Zeitraum.',
    ],
    terms: ['market', 'impressions', 'clicks', 'ctr', 'position', 'visitors', 'googleEntries', 'registrations'],
  },
  pages: {
    question: 'Welche Seite verdient als Nächstes Arbeit?',
    read: [
      'Eine Zeile je Seite: links, wie Google sie zeigt, rechts, was Besucher danach tun.',
      'Die Filter oben sind fertige Befunde. Jeder sagt, was mit der Seite los ist und was du tun kannst.',
      'Klick auf eine Zeile zeigt die Suchbegriffe, für die die Seite bei Google erschien. Die brauchst du, um Titel und Beschreibung zu verbessern.',
    ],
    act: [
      'Nimm dir pro Woche eine Seite aus "Unterschätzt" oder "Leck" vor.',
      'Ändere Titel und Beschreibung so, dass die wichtigsten Suchbegriffe der Seite vorkommen und ein klarer Nutzen drinsteht.',
      'Mit dem Export (CSV) kannst du die Liste in Excel weiterbearbeiten.',
    ],
    terms: ['impressions', 'ctr', 'position', 'entries', 'bounce', 'cta', 'registrations'],
  },
  queries: {
    question: 'Wofür findet man uns, und wofür noch nicht?',
    read: [
      'Eine Zeile je Suchbegriff, zusammengefasst über alle unsere Seiten. "Beste Seite" ist die mit den meisten Klicks.',
      'Brand-Suchen enthalten unseren Namen. Ihr Verlauf oben zeigt, ob EV Monitor bekannter wird.',
      'Klick auf eine Zeile zeigt, welche unserer Seiten für den Begriff erschienen sind.',
    ],
    act: [
      '"Fast vorne": Inhalt der besten Seite gezielt zu diesem Begriff ausbauen, dann rutscht sie oft auf Seite 1.',
      '"AdWords-Kandidaten": Begriffe für eine kleine Testkampagne. Die Zielseite überzeugt nachweislich, nur organisch stehen wir zu weit hinten.',
      'Begriffe, die zu keiner Seite passen, sind Ideen für neue Inhalte.',
    ],
    terms: ['brand', 'impressions', 'clicks', 'position', 'volume', 'cpc', 'share'],
  },
  campaigns: {
    question: 'Was bringen Forum-Beiträge, Mails und Anzeigen wirklich?',
    read: [
      'Eine Zeile je Quelle, Medium und Kampagne. Besucher und CTA-Rate kommen aus Plausible.',
      'Registrierungen und Trials kommen aus unserer Datenbank, aber nur, wenn der Link UTM-Parameter trug oder der Besuch von Google kam. Sonst steht dort "-".',
      '"Google" ohne Medium ist die kostenlose Suche, die Vergleichszeile für alles Bezahlte.',
    ],
    act: [
      'Verlinke ev-monitor.net nie ohne UTM-Parameter. Der Link-Baukasten oben erzeugt sie in der richtigen Schreibweise.',
      'Vergleiche Kampagnen über die CTA-Rate und Registrierungen je Besucher, nicht über die Besucherzahl allein.',
    ],
    terms: ['utm', 'visitors', 'cta', 'registrations'],
  },
  models: {
    question: 'Welche Modelle sucht man, und wo sind unsere Modellseiten zu dünn oder unsichtbar?',
    read: [
      'Eine Zeile je Modellseite (/modelle/Marke/Modell) der deutschen Website, daneben, wie viele Autos und Ladevorgänge wir zu dem Modell haben.',
      '"Marken" fasst alle Modellseiten einer Marke plus die Markenseite zusammen.',
    ],
    act: [
      '"Nachfrage ohne Daten": Fahrer dieses Modells gezielt ansprechen (Forum, Kooperation), damit die Seite echte Werte bekommt.',
      '"Daten ohne Sichtbarkeit": Seite technisch prüfen (Unter-Tab Technik) und von stärkeren Seiten verlinken.',
    ],
    terms: ['impressions', 'position', 'registrations'],
  },
  tech: {
    question: 'Bekommt Google unsere Seiten technisch einwandfrei?',
    read: [
      'Jede Nacht um 5 Uhr holt ein Prüfprogramm alle Seiten aus der Sitemap so, wie Google sie bekommt, und prüft sie.',
      'Fehler kosten Sichtbarkeit und gehören zuerst behoben, Hinweise sind Feinschliff. Klick auf eine Zeile zeigt alle Befunde der Seite.',
      'Die Ladezeit-Werte (Core Web Vitals) misst Google einmal pro Woche für die meistgesehenen Seiten.',
    ],
    act: [
      'Fehler an die Entwicklung geben, mit dem Befund-Text und der Seite.',
      'Titel zu lang oder Beschreibung zu kurz kannst du selbst formulieren und als Textvorschlag weitergeben.',
    ],
    terms: ['noindex', 'canonical', 'jsonld', 'prerender', 'cwv'],
  },
  competition: {
    question: 'Wer steht bei Google vor uns, und welche Begriffe lohnen sich?',
    read: [
      'Einmal pro Woche holen wir für unsere wichtigsten Suchbegriffe die ersten zehn Google-Treffer.',
      '"Wettbewerber" zählt, wie oft eine fremde Website bei diesen Begriffen vor uns steht.',
      '"Begriffsideen" sind verwandte Suchen mit Suchvolumen, die wir noch nicht abdecken.',
    ],
    act: [
      'Schau dir die Seiten der häufigsten Wettbewerber an: Was beantworten sie, was wir nicht beantworten?',
      'Begriffsideen mit hohem Volumen und passendem Thema sind Kandidaten für neue Inhalte oder Abschnitte auf Modellseiten.',
    ],
    terms: ['volume', 'cpc', 'share', 'position'],
  },
}

/** Erklärung je Befund: was er bedeutet und was zu tun ist. */
export const FINDINGS: Record<string, { label: string; tone: 'good' | 'warn' | 'bad'; meaning: string; action: string }> = {
  UNDERRATED: {
    label: 'Unterschätzt', tone: 'warn',
    meaning: 'Die Seite steht bei Google auf Position 4 bis 15 und wird oft angezeigt, aber seltener geklickt als unsere anderen Seiten.',
    action: 'Titel und Beschreibung umschreiben: Suchbegriffe der Seite aufnehmen (Zeile aufklappen), konkreten Nutzen nennen, unter 60 bzw. 155 Zeichen bleiben.',
  },
  LEAK: {
    label: 'Leck', tone: 'bad',
    meaning: 'Viele kommen über Google, aber die meisten gehen sofort wieder, und kaum jemand registriert sich.',
    action: 'Prüfen, ob die Seite hält, was das Suchergebnis verspricht. Antwort weiter nach oben, klare nächste Handlung anbieten.',
  },
  MONEY: {
    label: 'Geldseite', tone: 'good',
    meaning: 'Gehört zu den Seiten mit den meisten Registrierungen je Klick.',
    action: 'Nicht ohne Grund umbauen. Von anderen Seiten hierher verlinken und als Vorbild für schwächere Seiten nehmen.',
  },
  DEAD: {
    label: 'Verschwunden', tone: 'bad',
    meaning: 'Hatte in den 90 Tagen davor Impressionen, im gewählten Zeitraum keine einzige mehr.',
    action: 'Im Unter-Tab Technik prüfen (noindex, Canonical, Status). Bei Sprachvarianten (/en, /se ...) ist das seit Oktober 2026 gewollt.',
  },
  STRIKING_DISTANCE: {
    label: 'Fast vorne', tone: 'warn',
    meaning: 'Wir stehen für den Begriff auf Position 5 bis 20, also knapp vor oder hinter Ergebnisseite 1, und er wird oft gesucht (Fachwort: Striking Distance).',
    action: 'Die beste Seite gezielt ausbauen: Begriff in Überschrift und Text, die Frage dahinter direkt beantworten.',
  },
  CANNIBALIZED: {
    label: 'Konkurrenz im eigenen Haus', tone: 'bad',
    meaning: 'Mehrere unserer eigenen Seiten erscheinen für denselben Begriff und nehmen sich gegenseitig Platz weg (Fachwort: Kannibalisierung).',
    action: 'Zeile aufklappen, eine Seite als Hauptseite festlegen und die anderen auf sie verlinken lassen.',
  },
  ADWORDS: {
    label: 'AdWords-Kandidat', tone: 'good',
    meaning: 'Kein Markenbegriff, wir stehen organisch schlechter als Position 10, aber die Zielseite bringt überdurchschnittlich viele Besucher zum Registrieren.',
    action: 'Für eine kleine Testkampagne in Google Ads vormerken. Kosten je Klick in der Spalte CPC, sobald der Unter-Tab Wettbewerb läuft.',
  },
  CONTENT_GAP: {
    label: 'Nachfrage ohne Daten', tone: 'warn',
    meaning: 'Das Modell wird überdurchschnittlich oft gesucht, wir haben aber keinen einzigen Ladevorgang dazu.',
    action: 'Fahrer dieses Modells gewinnen: Forum, Facebook-Gruppe, Kooperation. Jede echte Fahrt macht die Seite wertvoller.',
  },
  INVISIBLE: {
    label: 'Daten ohne Sichtbarkeit', tone: 'bad',
    meaning: 'Wir haben mindestens 10 Ladevorgänge zu dem Modell, aber die Seite wird kaum angezeigt oder steht hinter Position 20.',
    action: 'Unter-Tab Technik prüfen und die Seite von Marken- und verwandten Modellseiten verlinken.',
  },
}

export const TONE_CLASS: Record<'good' | 'warn' | 'bad', string> = {
  good: 'bg-emerald-900/60 text-emerald-200',
  warn: 'bg-amber-900/60 text-amber-200',
  bad: 'bg-red-900/60 text-red-200',
}
