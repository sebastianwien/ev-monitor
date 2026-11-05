/**
 * Katalog aller Plausible-Events: einzige Quelle für Name, Bedeutung und Properties.
 * `analytics.track()` nimmt nur Namen von hier, ein neues Event ohne Eintrag ist ein Typfehler.
 * Der Admin-Reiter "SEO & Traffic > Events" zeigt diese Texte neben den Zahlen aus Plausible.
 * Namen nie umbenennen: Plausible führt einen neuen Namen als neues Event, die Historie reißt ab.
 */

export type EventArea =
  | 'akquise' | 'modelle' | 'anmeldung' | 'onboarding' | 'nutzung' | 'daten' | 'geld' | 'partner' | 'community' | 'plausible'

export const EVENT_AREAS: Record<EventArea, string> = {
  akquise: 'Startseite und Demo',
  modelle: 'Modellliste und Modellseiten',
  anmeldung: 'Registrierung und Login',
  onboarding: 'Einstieg und Aktivierung',
  nutzung: 'App-Nutzung',
  daten: 'Import und Verbindungen',
  geld: 'Abo und Unterstützung',
  partner: 'Partner und Werbung',
  community: 'Bewertungen und Teilen',
  plausible: 'Goals ohne Code (in Plausible angelegt)',
}

export interface EventInfo {
  area: EventArea
  /** Lesbarer Name, wie im Admin-Tab angezeigt. */
  title: string
  /** Welche Frage das Event beantwortet. */
  question: string
  /** Wo es ausgelöst wird. */
  where: string
  /** Property -> Bedeutung und Werte. */
  props?: Record<string, string>
}

export const ANALYTICS_EVENTS = {
  // Startseite und Demo
  landing_page_viewed: { area: 'akquise', title: 'Startseite (alt) angesehen', question: 'Wie viele sehen die alte Startseite?', where: 'Startseite' },
  landing_v2_viewed: { area: 'akquise', title: 'Startseite v2 angesehen', question: 'Wie viele sehen die neue Startseite?', where: 'Startseite v2' },
  lp_v2_path_selected: { area: 'akquise', title: 'Startseite: Weg gewählt', question: 'Kommen eher Fahrer oder Interessierte?', where: 'Startseite v2, Weiche', props: { path: 'owner (habe E-Auto) oder browser (suche eins)' } },
  lp_v2_model_clicked: { area: 'akquise', title: 'Startseite: Modell angeklickt', question: 'Welche Modelle ziehen von der Startseite?', where: 'Startseite v2', props: { brand: 'Marke', model: 'Modell', path: 'gewählter Weg' } },
  lp_v2_all_models_clicked: { area: 'akquise', title: 'Startseite: alle Modelle', question: 'Wer geht von der Startseite zur Modellliste?', where: 'Startseite v2', props: { path: 'gewählter Weg' } },
  explainer_video_play: { area: 'akquise', title: 'Erklärvideo gestartet', question: 'Schauen Besucher das Video zum Verbrauch?', where: 'Startseite', props: { source: 'Stelle des Videos' } },
  cta_register_clicked: { area: 'akquise', title: 'Registrieren geklickt', question: 'Welcher Knopf bringt die meisten Anmeldungen? (Conversion-Goal im SEO-Tab)', where: 'Startseite, Preise, Banner', props: { source: 'Stelle des Knopfs' } },
  cta_models_clicked: { area: 'akquise', title: 'Zu den Modellen geklickt', question: 'Wer geht von der Startseite zu den Modellen?', where: 'Startseite', props: { source: 'Stelle des Knopfs' } },
  demo_login_clicked: { area: 'akquise', title: 'Demo geklickt', question: 'Welcher Demo-Knopf wird genutzt?', where: 'Startseite, Galerien', props: { source: 'Stelle des Knopfs' } },
  demo_session_started: { area: 'akquise', title: 'Demo gestartet', question: 'Wie viele landen wirklich in der Demo? (Conversion-Goal im SEO-Tab)', where: 'Demo-Login', props: { source: 'Herkunft' } },
  demo_banner_register_clicked: { area: 'akquise', title: 'Demo: Registrieren geklickt', question: 'Wandelt die Demo in Anmeldungen?', where: 'Banner in der Demo' },
  demo_banner_exit_clicked: { area: 'akquise', title: 'Demo verlassen', question: 'Wie viele brechen die Demo ab?', where: 'Banner in der Demo' },
  demo_converted_to_register: { area: 'akquise', title: 'Demo zu Registrierung', question: 'Aus welcher Demo kommt eine Registrierung?', where: 'Registrierung nach Demo', props: { demo_source: 'Herkunft der Demo' } },

  // Modellliste und Modellseiten
  ranking_entry_card: { area: 'modelle', title: 'Liste: Einstiegskarte gewählt', question: 'Mit welcher Frage steigen Kaufinteressenten ein?', where: '/modelle', props: { card: 'gewählte Karte' } },
  ranking_sort: { area: 'modelle', title: 'Liste: sortiert', question: 'Wonach sortieren Besucher?', where: '/modelle', props: { sort: 'Sortierung' } },
  ranking_priority: { area: 'modelle', title: 'Liste: Priorität gewählt', question: 'Was ist Kaufinteressenten am wichtigsten?', where: '/modelle, Antwortkarte', props: { value: 'Priorität' } },
  ranking_fit_filter: { area: 'modelle', title: 'Liste: Passt-Filter', question: 'Wer filtert nach Alltagstauglichkeit?', where: '/modelle', props: { stops: 'Ladestopps' } },
  ranking_top5_click: { area: 'modelle', title: 'Liste: Top-5 angeklickt', question: 'Klicken Besucher auf die Empfehlungen?', where: '/modelle, Antwortkarte', props: { rank: 'Platz' } },
  ranking_gate_shown: { area: 'modelle', title: 'Liste: Gate gezeigt', question: 'Wie oft trifft ein Besucher auf das Gate?', where: '/modelle, Antwortkarte' },
  ranking_unlocked: { area: 'modelle', title: 'Liste: freigeschaltet', question: 'Wie viele schalten die Top 5 frei?', where: '/modelle, Gate', props: { models: 'Anzahl Modelle' } },
  ranking_model_page: { area: 'modelle', title: 'Liste: zur Modellseite', question: 'Wer geht aus dem Vergleich zur Modellseite?', where: '/modelle, Detail' },
  compare_start: { area: 'modelle', title: 'Vergleich gestartet', question: 'Welche Modelle werden verglichen?', where: '/modelle', props: { models: 'Anzahl', set: 'verglichene Modelle' } },
  needs_check_edit: { area: 'modelle', title: 'Liste: Bedarf geändert', question: 'Passen Besucher ihren Bedarf an?', where: '/modelle, Bedarf (1 s entprellt)' },
  assumptions_open: { area: 'modelle', title: 'Liste: Annahmen geöffnet', question: 'Wollen Besucher wissen, wie gerechnet wird?', where: '/modelle' },
  model_profile_jump: { area: 'modelle', title: 'Modellseite: Frage angeklickt', question: 'Springen Besucher zum Fahrprofil-Rechner?', where: 'Modellseite, Hero', props: { model: 'Modell' } },
  personal_range_started: { area: 'modelle', title: 'Fahrprofil begonnen', question: 'Wie viele starten den Rechner?', where: 'Modellseite, Fahrprofil', props: { model: 'Modell' } },
  story_chip_answered: { area: 'modelle', title: 'Fahrprofil: Frage beantwortet', question: 'Welche Fragen werden beantwortet?', where: 'Modellseite, Fahrprofil', props: { model: 'Modell', question: 'Frage' } },
  personal_range_wallbox: { area: 'modelle', title: 'Fahrprofil: Laden zu Hause', question: 'Wie viele können zu Hause laden?', where: 'Modellseite, Fahrprofil', props: { model: 'Modell', home: 'ja/nein' } },
  personal_range_result: { area: 'modelle', title: 'Fahrprofil: Ergebnis', question: 'Wie viele kommen bis zum Ergebnis?', where: 'Modellseite, Fahrprofil', props: { model: 'Modell', data_level: 'Datenbasis' } },
  personal_range_gate_shown: { area: 'modelle', title: 'Fahrprofil: Gate gezeigt', question: 'Wie oft trifft der Rechner auf das Gate?', where: 'Modellseite, Fahrprofil', props: { model: 'Modell' } },
  personal_range_unlock_click: { area: 'modelle', title: 'Fahrprofil: Freischalten geklickt', question: 'Wer will das volle Ergebnis?', where: 'Modellseite, Fahrprofil', props: { model: 'Modell', open: 'offene Antworten' } },

  // Registrierung und Login
  register_page_viewed: { area: 'anmeldung', title: 'Registrierung aufgerufen', question: 'Wie viele öffnen die Registrierung?', where: '/register' },
  registration_completed: { area: 'anmeldung', title: 'Registriert', question: 'Echte Registrierungen je Tag (Conversion-Goal im SEO-Tab)', where: 'Registrierung' },
  registration_failed: { area: 'anmeldung', title: 'Registrierung gescheitert', question: 'Woran scheitert die Registrierung?', where: 'Registrierung', props: { reason: 'Grund' } },
  email_verified: { area: 'anmeldung', title: 'E-Mail bestätigt', question: 'Wie viele bestätigen ihre Adresse?', where: 'Bestätigungslink' },
  login: { area: 'anmeldung', title: 'Angemeldet', question: 'Logins gesamt', where: 'Login' },
  login_failed: { area: 'anmeldung', title: 'Login gescheitert', question: 'Woran scheitert der Login?', where: 'Login', props: { reason: 'Grund' } },
  password_reset_requested: { area: 'anmeldung', title: 'Passwort vergessen', question: 'Wie oft wird ein neues Passwort angefordert?', where: 'Login' },
  gate_code_requested: { area: 'anmeldung', title: 'Gate: Code angefordert', question: 'Wie viele fordern im Gate einen Code an?', where: 'Gate auf Modellseiten' },
  gate_code_verified: { area: 'anmeldung', title: 'Gate: Code bestätigt', question: 'Wie viele melden sich übers Gate an? (Conversion-Goal im SEO-Tab)', where: 'Gate auf Modellseiten', props: { placement: 'Stelle des Gates' } },

  // Einstieg und Aktivierung
  onboarding_started: { area: 'onboarding', title: 'Einstieg begonnen', question: 'Wie viele starten den Einstieg?', where: 'Onboarding' },
  onboarding_step_viewed: { area: 'onboarding', title: 'Einstieg: Schritt gesehen', question: 'Bis wohin kommen neue Nutzer?', where: 'Onboarding', props: { step: 'Schritt' } },
  onboarding_skipped: { area: 'onboarding', title: 'Einstieg übersprungen', question: 'Bei welchem Schritt wird abgebrochen?', where: 'Onboarding', props: { step: 'Schritt' } },
  onboarding_completed: { area: 'onboarding', title: 'Einstieg abgeschlossen', question: 'Wie viele schließen den Einstieg ab?', where: 'Onboarding', props: { completed_steps: 'erledigte Schritte' } },
  car_added: { area: 'onboarding', title: 'Auto angelegt', question: 'Wie viele legen ein (erstes) Auto an?', where: '/cars', props: { first_car: 'erstes Auto ja/nein' } },
  log_created: { area: 'onboarding', title: 'Ladung erfasst', question: 'Wie wird erfasst: von Hand, Foto oder Sprache?', where: 'Erfassen', props: { source: 'manual, ocr oder voice', first_log: 'erste Ladung ja/nein' } },
  activation_reached: { area: 'onboarding', title: 'Aktiviert', question: 'Wie viele Registrierte haben je einen Datenpunkt? (einmal je Gerät)', where: 'App' },
  empty_dashboard_viewed: { area: 'onboarding', title: 'Leeres Dashboard', question: 'Wo bleiben neue Nutzer hängen?', where: 'Dashboard', props: { reason: 'no_car, no_logs_ever oder no_logs_in_period' } },
  car_teaser_clicked: { area: 'onboarding', title: 'Auto-Hinweis geklickt', question: 'Welche Einrichtungs-Hinweise wirken?', where: '/cars', props: { kind: 'Art des Hinweises' } },
  car_teaser_dismissed: { area: 'onboarding', title: 'Auto-Hinweis weggeklickt', question: 'Welche Einrichtungs-Hinweise nerven?', where: '/cars', props: { kind: 'Art des Hinweises' } },

  // App-Nutzung
  dashboard_viewed: { area: 'nutzung', title: 'Dashboard angesehen', question: 'Dashboard-Besuche gesamt', where: 'Dashboard' },
  return_visit: { area: 'nutzung', title: 'Wiederkehr', question: 'Wer kommt nach wie vielen Tagen wieder?', where: 'App', props: { days_since_last: 'Tage seit letztem Besuch' } },
  ocr_scan_started: { area: 'nutzung', title: 'Foto-Erkennung gestartet', question: 'Wie oft wird per Foto erfasst?', where: 'Erfassen' },
  ocr_scan_completed: { area: 'nutzung', title: 'Foto-Erkennung fertig', question: 'Wie gut erkennt die Foto-Erkennung?', where: 'Erfassen', props: { confidence: 'Sicherheit', fields_extracted: 'erkannte Felder' } },
  Voice: { area: 'nutzung', title: 'Spracheingabe', question: 'Wo im Ablauf der Spracheingabe steigen Nutzer aus?', where: 'Erfassen, Mikrofon', props: { step: 'shown, open, consent, stop, cancel, error, draft, retry, saved, corrected, quota, upsell' } },
  ticker_tap: { area: 'nutzung', title: 'Ticker angetippt', question: 'Welche Ticker-Meldungen interessieren?', where: 'Ticker', props: { type: 'Meldungsart', target: 'Ziel' } },
  ticker_pause: { area: 'nutzung', title: 'Ticker angehalten', question: 'Wird der Ticker angehalten?', where: 'Ticker' },
  ticker_toggle: { area: 'nutzung', title: 'Ticker ein-/ausgeklappt', question: 'Stört der Ticker?', where: 'Ticker', props: { state: 'collapsed oder expanded' } },

  // Import und Verbindungen
  import_tab_clicked: { area: 'daten', title: 'Import-Reiter geklickt', question: 'Welche Integrationen werden angeklickt?', where: '/imports', props: { tab: 'Reiter' } },
  import_completed: { area: 'daten', title: 'Import abgeschlossen', question: 'Klappt der Import, mit wie vielen Einträgen? (Conversion-Goal im SEO-Tab)', where: '/imports', props: { source: 'Quelle', count: 'Einträge' } },
  connector_activated: { area: 'daten', title: 'Verbindung aktiviert', question: 'Welche Verbindungen werden wirklich verbunden?', where: '/imports', props: { type: 'Verbindung', brand: 'Marke' } },
  polestar_connect: { area: 'daten', title: 'Polestar verbunden', question: 'Klappt die Polestar-Verbindung?', where: '/imports', props: { result: 'Ergebnis' } },
  autosync_prompt_clicked: { area: 'daten', title: 'AutoSync-Hinweis geklickt', question: 'Wirkt der AutoSync-Hinweis am Auto?', where: '/cars', props: { brand: 'Marke' } },
  autosync_prompt_dismissed: { area: 'daten', title: 'AutoSync-Hinweis weggeklickt', question: 'Wer will kein AutoSync?', where: '/cars', props: { brand: 'Marke' } },
  api_key_created: { area: 'daten', title: 'API-Schlüssel erstellt', question: 'Wer nutzt die API?', where: 'Einstellungen' },
  api_key_deleted: { area: 'daten', title: 'API-Schlüssel gelöscht', question: 'Wer hört mit der API auf?', where: 'Einstellungen' },

  // Abo und Unterstützung
  upgrade_page_viewed: { area: 'geld', title: 'Upgrade-Seite angesehen', question: 'Wie viele sehen die Upgrade-Seite?', where: '/upgrade' },
  upsell_viewed: { area: 'geld', title: 'Bezahlseite angesehen', question: 'Wer kommt woher auf die Bezahlseiten?', where: '/supporter, /upgrade', props: { page: 'supporter oder upgrade', source: 'Herkunft (?from, vorige Route oder direct)' } },
  checkout_started: { area: 'geld', title: 'Kauf begonnen', question: 'Wer klickt auf Kaufen?', where: 'Bezahlseiten', props: { plan: 'monthly oder yearly', tier: 'Tarif', source: 'Herkunft' } },
  checkout_completed: { area: 'geld', title: 'Kauf abgeschlossen', question: 'Abschlüsse je Tarif und Herkunft', where: 'Erfolgsseite', props: { tier: 'Tarif', source: 'Herkunft' } },
  kofi_clicked: { area: 'geld', title: 'Ko-fi geklickt', question: 'Wer will per Ko-fi unterstützen?', where: 'Unterstützen-Popover' },
  paypal_clicked: { area: 'geld', title: 'PayPal geklickt', question: 'Wer will per PayPal unterstützen?', where: 'Unterstützen-Popover' },

  // Partner und Werbung
  thg_banner_clicked: { area: 'partner', title: 'THG-Banner geklickt', question: 'Wie oft wird die THG-Prämie über uns beantragt?', where: 'Dashboard, /modelle' },
  enbw_banner_clicked: { area: 'partner', title: 'EnBW-Banner geklickt', question: 'Wie oft wird die EnBW-Ladekarte angeklickt?', where: 'Affiliate-Banner (derzeit aus)' },
  partner_banner_click: { area: 'partner', title: 'Partner-Hinweis geklickt', question: 'Wirken Kooperations-Hinweise auf Modellseiten?', where: 'Modellseite', props: { partner: 'Partner' } },

  // Bewertungen und Teilen
  rating_block_viewed: { area: 'community', title: 'Bewertungen gesehen', question: 'Wie viele sehen Fahrerbewertungen?', where: 'Modellseite', props: { count: 'Anzahl Bewertungen' } },
  rating_started: { area: 'community', title: 'Bewertung begonnen', question: 'Wie viele beginnen eine Bewertung?', where: 'Auto bewerten', props: { source: 'Herkunft', edit: 'Änderung ja/nein' } },
  rating_completed_core: { area: 'community', title: 'Bewertung: Kern fertig', question: 'Wie viele schließen die Pflichtfragen ab?', where: 'Auto bewerten', props: { source: 'Herkunft', edit: 'Änderung ja/nein' } },
  rating_completed_deep: { area: 'community', title: 'Bewertung: ausführlich fertig', question: 'Wie viele schreiben mehr?', where: 'Auto bewerten', props: { chips: 'gewählte Stärken/Schwächen', text: 'Art des Texts' } },
  review_submitted: { area: 'community', title: 'Bewertungstext abgeschickt', question: 'Wie viele schreiben einen Text?', where: 'Auto bewerten', props: { kind: 'Art des Texts' } },
  rating_teaser_dismissed: { area: 'community', title: 'Bewerten-Hinweis weggeklickt', question: 'Nervt der Bewerten-Hinweis?', where: 'App', props: { source: 'Stelle' } },
  survey_submitted: { area: 'community', title: 'Umfrage abgeschickt', question: 'Wie viele beantworten Umfragen?', where: '/umfrage', props: { slug: 'Umfrage' } },
  car_share_created: { area: 'community', title: 'Fahrzeug-Link erstellt', question: 'Wer teilt sein Auto?', where: '/cars', props: { source: 'Stelle' } },
  car_share_link_shared: { area: 'community', title: 'Fahrzeug-Link geteilt', question: 'Wird der Link wirklich verschickt?', where: '/cars', props: { method: 'share_sheet oder clipboard', source: 'Stelle' } },
  car_share_signature_copied: { area: 'community', title: 'Forum-Signatur kopiert', question: 'Nutzen Fahrer die Signatur im Forum?', where: '/cars', props: { kind: 'Format' } },
  car_share_revoked: { area: 'community', title: 'Fahrzeug-Link zurückgezogen', question: 'Wie oft wird ein Link zurückgezogen?', where: '/cars' },
} as const satisfies Record<string, EventInfo>

export type AnalyticsEvent = keyof typeof ANALYTICS_EVENTS

/** In Plausible angelegte Goals, die kein Code sendet (Seitenaufrufe, Plausible-eigene Events). */
export const PLAUSIBLE_ONLY_GOALS: Record<string, EventInfo> = {
  'Startseite aufgerufen': { area: 'plausible', title: 'Startseite aufgerufen', question: 'Seitenaufruf-Goal für die Startseite', where: 'Plausible-Goal auf /' },
  'Modell-Detailseite besucht': { area: 'plausible', title: 'Modellseite besucht', question: 'Seitenaufruf-Goal für Modellseiten', where: 'Plausible-Goal auf /modelle/*/*' },
  'Visit /start': { area: 'plausible', title: '/start besucht', question: 'Seitenaufruf-Goal für /start', where: 'Plausible-Goal' },
  'Outbound Link: Click': { area: 'plausible', title: 'Externer Link geklickt', question: 'Klicks auf Links zu anderen Seiten (Plausible-Skript)', where: 'überall' },
}

export function eventInfo(name: string): EventInfo | null {
  return (ANALYTICS_EVENTS as Record<string, EventInfo>)[name] ?? PLAUSIBLE_ONLY_GOALS[name] ?? null
}
