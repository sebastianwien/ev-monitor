export interface SurveyOption {
    value: string
    label: string
    freeText?: boolean // wenn true: zeigt optionales Textfeld wenn diese Option gewählt ist
}

export interface SurveyQuestion {
    key: string
    label: string
    options: SurveyOption[]
}

export interface SurveyConfig {
    slug: string
    title: string
    description: string
    questions: SurveyQuestion[]
}

export const surveys: Record<string, SurveyConfig> = {
    'premium-april-2026': {
        slug: 'premium-april-2026',
        title: 'Kurze Frage zu EV Monitor',
        description: 'Hilf mir dabei, das richtige Angebot zu bauen. Dauert 60 Sekunden.',
        questions: [
            {
                key: 'current_tracking',
                label: 'Wie erfasst du deine Ladevorgänge aktuell?',
                options: [
                    { value: 'manual', label: 'Manuell' },
                    { value: 'other_tool', label: 'Anderes Tool (Tronity, TeslaFi, ...)', freeText: true },
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
                label: 'Was spricht aus deiner Sicht dagegen?',
                options: [
                    { value: 'privacy', label: 'Ich weiß nicht wer Zugriff auf meine Fahrzeugdaten hat' },
                    { value: 'no_more_subscriptions', label: 'Ich möchte kein weiteres Abo abschließen' },
                    { value: 'too_expensive', label: 'Zu teuer' },
                    { value: 'not_needed', label: 'Brauche ich nicht' },
                    { value: 'car_not_supported', label: 'Mein Auto wird nicht unterstützt' },
                    { value: 'none', label: 'Keiner' },
                ],
            },
        ],
    },
}
