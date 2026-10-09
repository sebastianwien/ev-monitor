package com.evmonitor.domain.rating;

import com.evmonitor.domain.exception.ValidationException;

import java.util.List;

/** Ein eingereichter Erfahrungstext, getrimmt und geprüft. Pro und Contra sind optionale Kurzlisten. */
public record ReviewDraft(String body, List<String> pros, List<String> cons) {

    public static final int MAX_BODY_CHARS = 5_000;
    public static final int MAX_LIST_ITEMS = 5;
    public static final int MAX_ITEM_CHARS = 120;

    /**
     * @throws ValidationException bei leerem oder zu langem Text und bei zu vielen oder zu langen Listeneinträgen
     */
    public static ReviewDraft of(String body, List<String> pros, List<String> cons) {
        String trimmed = body == null ? "" : body.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_BODY_CHARS) {
            throw new ValidationException("REVIEW_BODY_LENGTH",
                    "Der Text braucht 1 bis " + MAX_BODY_CHARS + " Zeichen.");
        }
        return new ReviewDraft(trimmed, items(pros), items(cons));
    }

    private static List<String> items(List<String> raw) {
        List<String> items = raw == null ? List.of()
                : raw.stream().filter(java.util.Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (items.size() > MAX_LIST_ITEMS || items.stream().anyMatch(s -> s.length() > MAX_ITEM_CHARS)) {
            throw new ValidationException("REVIEW_LIST_LIMIT",
                    "Höchstens " + MAX_LIST_ITEMS + " Punkte mit je " + MAX_ITEM_CHARS + " Zeichen.");
        }
        return items;
    }
}
