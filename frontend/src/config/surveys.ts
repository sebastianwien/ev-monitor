/**
 * A label that is either a single (German) string or a DE/EN pair.
 * Locales without an authored variant (nb, sv) fall back to English,
 * mirroring the app's vue-i18n fallbackLocale chain.
 */
export type Localized = string | { de: string; en: string }

export function resolveLocalized(value: Localized, locale: string): string {
    if (typeof value === 'string') return value
    return locale === 'de' ? value.de : value.en
}

export interface SurveyOption {
    value: string
    label: Localized
    freeText?: boolean // wenn true: zeigt optionales Textfeld wenn diese Option gewählt ist
    freeTextPlaceholder?: Localized
}

export interface SurveyQuestion {
    key: string
    label: Localized
    multiple?: boolean
    options: SurveyOption[]
}

export interface SurveyConfig {
    slug: string
    title: Localized
    description: Localized
    info?: Localized[]  // optionale Infobox-Absätze über den Fragen
    questions: SurveyQuestion[]
}

export const surveys: Record<string, SurveyConfig> = {
    'premium-april-2026': {
        slug: 'premium-april-2026',
        title: 'Kurze Frage zu EV Monitor',
        description: 'Hilf mir dabei, das richtige Angebot zu bauen. Dauert 60 Sekunden.',
        info: [
            'Ich plane ein Feature, das Ladevorgänge vollautomatisch erfasst - ohne manuelle Eingabe. Dazu verbindest du dein Auto einmalig über Smartcar.',
            'Sobald du lädst, kommen die Daten automatisch rein. Du musst danach nichts mehr tun.',
        ],
        questions: [
            {
                key: 'current_tracking',
                label: 'Wie erfasst du deine Ladevorgänge aktuell?',
                options: [
                    { value: 'manual', label: 'Manuell' },
                    { value: 'other_tool', label: 'Anderes Tool (Tronity, TeslaFi, ...)', freeText: true, freeTextPlaceholder: 'Welches Tool?' },
                    { value: 'not_at_all', label: 'Gar nicht' },
                ],
            },
            {
                key: 'auto_sync_interest',
                label: 'Wäre automatisches Erfassen interessant für dich - dein Auto meldet sich selbst?',
                options: [
                    { value: 'yes', label: 'Ja' },
                    { value: 'maybe', label: 'Vielleicht' },
                    { value: 'no', label: 'Nein' },
                ],
            },
            {
                key: 'fair_price',
                label: 'Was wäre ein fairer Monatspreis dafür?',
                options: [
                    { value: '2.99', label: '€ 2,99 / Monat' },
                    { value: '3.99', label: '€ 3,99 / Monat' },
                    { value: '4.99', label: '€ 4,99 / Monat' },
                    { value: 'no_pay', label: 'Würde ich nicht zahlen' },
                ],
            },
            {
                key: 'concern',
                label: 'Was spricht aus deiner Sicht dagegen? (Mehrfachauswahl möglich)',
                multiple: true,
                options: [
                    { value: 'privacy', label: 'Ich weiß nicht wer Zugriff auf meine Fahrzeugdaten hat' },
                    { value: 'too_expensive', label: 'Zu teuer' },
                    { value: 'not_needed', label: 'Brauche ich nicht' },
                    { value: 'car_not_supported', label: 'Mein Auto wird nicht unterstützt' },
                    { value: 'none', label: 'Keiner' },
                ],
            },
        ],
    },

    'autosync-satisfaction': {
        slug: 'autosync-satisfaction',
        title: { de: 'Wie läuft AutoSync für dich?', en: 'How is AutoSync working for you?' },
        description: {
            de: 'Du nutzt AutoSync jetzt seit ein paar Wochen. Eine kurze Frage - dauert 60 Sekunden.',
            en: 'You have been using AutoSync for a few weeks now. A quick question - takes 60 seconds.',
        },
        questions: [
            {
                key: 'satisfaction',
                label: { de: 'Wie zufrieden bist du mit AutoSync insgesamt?', en: 'How satisfied are you with AutoSync overall?' },
                options: [
                    { value: '1', label: { de: 'Sehr unzufrieden', en: 'Very dissatisfied' } },
                    { value: '2', label: { de: 'Eher unzufrieden', en: 'Somewhat dissatisfied' } },
                    { value: '3', label: { de: 'Neutral', en: 'Neutral' } },
                    { value: '4', label: { de: 'Eher zufrieden', en: 'Somewhat satisfied' } },
                    { value: '5', label: { de: 'Sehr zufrieden', en: 'Very satisfied' } },
                ],
            },
            {
                key: 'issues',
                label: { de: 'Gab es etwas, das nicht rund lief? (Mehrfachauswahl)', en: 'Was there anything that did not work well? (Multiple choice)' },
                multiple: true,
                options: [
                    { value: 'missing_charges', label: { de: 'Ladevorgänge wurden nicht (alle) erfasst', en: 'Charging sessions were not (all) captured' } },
                    { value: 'wrong_data', label: { de: 'Daten waren falsch (kWh, Stand, Reichweite)', en: 'Data was wrong (kWh, state of charge, range)' } },
                    { value: 'nothing', label: { de: 'Lief alles rund', en: 'Everything worked fine' } },
                    {
                        value: 'other',
                        label: { de: 'Sonstiges', en: 'Other' },
                        freeText: true,
                        freeTextPlaceholder: { de: 'Was genau?', en: 'What exactly?' },
                    },
                ],
            },
        ],
    },
}
