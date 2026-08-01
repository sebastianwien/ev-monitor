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

/** Muss mit SurveyService.MAX_ANSWER_LENGTH im Backend uebereinstimmen. */
export const MAX_TEXT_ANSWER_LENGTH = 2000

export interface SurveyOption {
    value: string
    label: Localized
    freeText?: boolean // wenn true: zeigt optionales Textfeld wenn diese Option gewählt ist
    freeTextPlaceholder?: Localized
}

interface BaseQuestion {
    key: string
    label: Localized
    /** Optionale Fragen blockieren den Weiter-Button nicht. */
    optional?: boolean
}

export interface ChoiceQuestion extends BaseQuestion {
    type?: 'choice'
    multiple?: boolean
    options: SurveyOption[]
}

export interface TextQuestion extends BaseQuestion {
    type: 'text'
    placeholder?: Localized
    /** Mehrzeiliges Feld statt einzeiligem Input. */
    multiline?: boolean
    maxLength?: number
}

export type SurveyQuestion = ChoiceQuestion | TextQuestion

export interface SurveyPage {
    title?: Localized
    info?: Localized[] // optionale Infobox-Absätze über den Fragen
    questions: SurveyQuestion[]
}

export interface SurveyConfig {
    slug: string
    title: Localized
    description: Localized
    pages: SurveyPage[]
}

export type SurveyAnswers = Record<string, string | string[]>

export function isQuestionAnswered(question: SurveyQuestion, answers: SurveyAnswers): boolean {
    if (question.optional) return true
    const value = answers[question.key]
    if (Array.isArray(value)) return value.length > 0
    return typeof value === 'string' && value.trim().length > 0
}

export function isPageComplete(page: SurveyPage, answers: SurveyAnswers): boolean {
    return page.questions.every(q => isQuestionAnswered(q, answers))
}

/** Navigations- und Statustexte der Umfrage-Ansicht (die View selbst ist nicht i18n-isiert). */
export const surveyText = {
    unknown: { de: 'Diese Umfrage existiert nicht.', en: 'This survey does not exist.' },
    loading: { de: 'Lädt...', en: 'Loading...' },
    thanksTitle: { de: 'Danke!', en: 'Thank you!' },
    thanksBody: {
        de: 'Deine Antworten helfen mir dabei, EV Monitor besser zu machen.',
        en: 'Your answers help me make EV Monitor better.',
    },
    back: { de: 'Zurück', en: 'Back' },
    next: { de: 'Weiter', en: 'Next' },
    submit: { de: 'Absenden', en: 'Submit' },
    submitting: { de: 'Wird gespeichert...', en: 'Saving...' },
    error: { de: 'Fehler beim Speichern. Bitte versuche es erneut.', en: 'Could not save. Please try again.' },
    step: { de: 'Schritt', en: 'Step' },
    of: { de: 'von', en: 'of' },
    optionalHint: { de: 'optional', en: 'optional' },
} satisfies Record<string, Localized>

export const surveys: Record<string, SurveyConfig> = {
    'ev-pain-points-2026': {
        slug: 'ev-pain-points-2026',
        title: { de: 'E-Auto Pain Points', en: 'EV pain points' },
        description: {
            de: 'Was nervt dich beim Laden wirklich? Erzähl es mir in eigenen Worten - dauert 2 Minuten.',
            en: 'What really annoys you about charging? Tell me in your own words - takes 2 minutes.',
        },
        pages: [
            {
                info: [
                    {
                        de: 'Es gibt keine falschen Antworten und keine Pflicht zur Ausführlichkeit. Auch halbe Gedanken helfen mir weiter.',
                        en: 'There are no wrong answers and no need to write an essay. Half-formed thoughts help too.',
                    },
                ],
                questions: [
                    {
                        key: 'annoying_moment',
                        type: 'text',
                        multiline: true,
                        label: {
                            de: 'Was war bei deiner letzten längeren Fahrt mit dem E-Auto der nervigste Moment rund ums Laden?',
                            en: 'On your last longer EV trip: what was the most annoying moment around charging?',
                        },
                    },
                    {
                        key: 'paid_too_much',
                        type: 'text',
                        multiline: true,
                        label: {
                            de: 'An welcher Stelle hast du dabei unnötig Geld bezahlt oder hattest zumindest das Gefühl, zu viel zu zahlen?',
                            en: 'Where did you pay unnecessarily, or at least feel like you were paying too much?',
                        },
                    },
                    {
                        key: 'one_problem_solved',
                        type: 'text',
                        multiline: true,
                        label: {
                            de: 'Wenn eine App dir genau ein Problem rund um das Laden abnehmen könnte: Welches wäre das?',
                            en: 'If an app could take exactly one charging problem off your hands: which one?',
                        },
                    },
                    {
                        key: 'missing_features',
                        type: 'text',
                        multiline: true,
                        label: {
                            de: 'Welche Funktionen fehlen dir gerade noch bei EV Monitor, die dir das Leben leichter machen würden?',
                            en: 'Which features are you still missing in EV Monitor that would make your life easier?',
                        },
                    },
                    {
                        key: 'apps_used',
                        type: 'text',
                        label: {
                            de: 'Welche Apps benutzt du rund um dein E-Auto?',
                            en: 'Which apps do you use around your EV?',
                        },
                        placeholder: {
                            de: 'z.B. ABRP, Chargeprice, Hersteller-App ...',
                            en: 'e.g. ABRP, Chargeprice, manufacturer app ...',
                        },
                    },
                ],
            },
            {
                title: {
                    de: 'Fast geschafft - drei optionale Fragen',
                    en: 'Almost done - three optional questions',
                },
                info: [
                    {
                        de: 'Hilf uns mit diesen Antworten, einen guten Überblick über die Menschen zu bekommen, die E-Auto fahren. Alles freiwillig.',
                        en: 'Help us get a picture of the people who drive EVs. All voluntary.',
                    },
                ],
                questions: [
                    {
                        key: 'age_range',
                        optional: true,
                        label: { de: 'Wie alt bist du?', en: 'How old are you?' },
                        options: [
                            { value: 'under_25', label: { de: 'Unter 25', en: 'Under 25' } },
                            { value: '25_34', label: { de: '25 - 34', en: '25 - 34' } },
                            { value: '35_44', label: { de: '35 - 44', en: '35 - 44' } },
                            { value: '45_54', label: { de: '45 - 54', en: '45 - 54' } },
                            { value: '55_64', label: { de: '55 - 64', en: '55 - 64' } },
                            { value: '65_plus', label: { de: '65 oder älter', en: '65 or older' } },
                        ],
                    },
                    {
                        key: 'years_driving_ev',
                        optional: true,
                        label: {
                            de: 'Wie viele Jahre fährst du schon E-Auto?',
                            en: 'How many years have you been driving an EV?',
                        },
                        options: [
                            { value: 'under_1', label: { de: 'Weniger als 1 Jahr', en: 'Less than 1 year' } },
                            { value: '1_2', label: { de: '1 - 2 Jahre', en: '1 - 2 years' } },
                            { value: '3_5', label: { de: '3 - 5 Jahre', en: '3 - 5 years' } },
                            { value: 'over_5', label: { de: 'Mehr als 5 Jahre', en: 'More than 5 years' } },
                        ],
                    },
                    {
                        key: 'gender',
                        optional: true,
                        label: { de: 'Ich ordne mich ein als ...', en: 'I identify as ...' },
                        options: [
                            { value: 'male', label: { de: 'Männlich', en: 'Male' } },
                            { value: 'female', label: { de: 'Weiblich', en: 'Female' } },
                            { value: 'diverse', label: { de: 'Divers', en: 'Diverse' } },
                        ],
                    },
                ],
            },
        ],
    },

    'premium-april-2026': {
        slug: 'premium-april-2026',
        title: 'Kurze Frage zu EV Monitor',
        description: 'Hilf mir dabei, das richtige Angebot zu bauen. Dauert 60 Sekunden.',
        pages: [
            {
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
        ],
    },

    'autosync-satisfaction': {
        slug: 'autosync-satisfaction',
        title: { de: 'Wie läuft AutoSync für dich?', en: 'How is AutoSync working for you?' },
        description: {
            de: 'Du nutzt AutoSync jetzt seit ein paar Wochen. Eine kurze Frage - dauert 60 Sekunden.',
            en: 'You have been using AutoSync for a few weeks now. A quick question - takes 60 seconds.',
        },
        pages: [
            {
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
        ],
    },
}
