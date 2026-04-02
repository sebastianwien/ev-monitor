# Survey Feature

Einmalige Nutzerumfragen, konfigurierbar per Slug.

---

## Route

```
/umfrage/:slug
```

Beispiel: `/umfrage/premium-april-2026`

Auth required - nur eingeloggte User können teilnehmen.

---

## Wie eine Umfrage erstellt wird

Neue Umfrage in `frontend/src/config/surveys.ts` eintragen:

```ts
'mein-slug': {
    slug: 'mein-slug',
    title: 'Titel der Umfrage',
    description: 'Kurzbeschreibung.',
    info: ['Optionaler Infotext-Absatz 1', 'Absatz 2'],  // optional
    questions: [
        {
            key: 'frage_key',
            label: 'Fragetext',
            multiple: true,  // optional - Mehrfachauswahl (Checkbox), Default: Single (Radio)
            options: [
                { value: 'opt1', label: 'Option 1' },
                { value: 'opt2', label: 'Option 2', freeText: true },  // freeText: zeigt Textfeld wenn gewählt
            ],
        },
    ],
}
```

Kein Backend-Deployment nötig - der Slug wird im Frontend definiert, das Backend speichert die Antworten generisch.

---

## Verhalten

- **Status-Check beim Laden:** Frontend fragt `GET /api/surveys/{slug}/status` - bereits abgestimmte User sehen sofort die Danke-Seite.
- **Idempotent:** Doppelte Submissions werden still ignoriert (auch bei Race Conditions via DB Unique Constraint).
- **Validierung:** Submit-Button bleibt disabled bis alle Fragen beantwortet sind.
- **Free-Text:** Wenn eine Option `freeText: true` hat und gewählt wird, erscheint ein optionales Textfeld. Der Wert wird als `{question_key}_detail` im Payload mitgeschickt.

---

## API

| Methode | Endpoint | Beschreibung |
|---------|----------|--------------|
| `GET` | `/api/surveys/{slug}/status` | Gibt `{ "responded": true/false }` zurück |
| `POST` | `/api/surveys/{slug}/respond` | Speichert Antworten als JSON |

Beide Endpoints erfordern JWT-Auth.

---

## Datenspeicherung

Tabelle `survey_responses`:
- `survey_slug` - welche Umfrage
- `user_id` - wer hat geantwortet
- `answers` - JSONB mit allen Antworten (`{ "frage_key": "opt1", "frage2_key": ["a", "b"] }`)
- Unique Constraint auf `(survey_slug, user_id)` verhindert doppelte Einträge

---

## Relevante Dateien

- `frontend/src/config/surveys.ts` - Umfrage-Definitionen
- `frontend/src/views/SurveyView.vue` - UI
- `frontend/src/api/surveyService.ts` - API-Calls
- `backend/.../application/SurveyService.java` - Business Logic
- `backend/.../web/SurveyController.java` - REST Endpoints
