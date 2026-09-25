package com.evmonitor.application.ingest.event;

import com.evmonitor.domain.xpeng.XpengParseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code import_event.error} ist ein Betriebsprotokoll: Fehlerart ja, Inhalte nein. Meldungen tragen
 * teils Dateinamen, Tokens aus der Datei oder Zahlen - die fliegen raus, damit gleiche Fehler auch
 * gleich gruppiert werden.
 */
class ImportEventErrorsTest {

    @Test
    void null_givesNull() {
        assertThat(ImportEventErrors.describe(null)).isNull();
    }

    @Test
    void messageWithoutContent_keepsClassAndMessage() {
        assertThat(ImportEventErrors.describe(new IllegalStateException("Datei ist kein lesbares JSON")))
                .isEqualTo("IllegalStateException: Datei ist kein lesbares JSON");
    }

    @Test
    void fileNameAfterColon_isCut() {
        assertThat(ImportEventErrors.describe(
                new XpengParseException("ZIP-Entry mit Pfadanteil abgelehnt: ../max-mustermann/export.csv")))
                .isEqualTo("XpengParseException: ZIP-Entry mit Pfadanteil abgelehnt");
    }

    @Test
    void quotedTokensParenthesesAndDigits_areRemoved() {
        assertThat(ImportEventErrors.describe(
                new IllegalArgumentException("Unrecognized token 'WVWZZZ1KZAW000123' in line 42 (column 7)")))
                .isEqualTo("IllegalArgumentException: Unrecognized token in line");
        assertThat(ImportEventErrors.describe(new IllegalArgumentException("Wert \"Berlin\" ungültig")))
                .isEqualTo("IllegalArgumentException: Wert ungültig");
    }

    @Test
    void numbersAreRemoved_soSameErrorsGroup() {
        String a = ImportEventErrors.describe(new IllegalStateException("Too many telematics rows 500001"));
        String b = ImportEventErrors.describe(new IllegalStateException("Too many telematics rows 700002"));
        assertThat(a).isEqualTo(b).isEqualTo("IllegalStateException: Too many telematics rows");
    }

    @Test
    void pathsAreRemoved() {
        assertThat(ImportEventErrors.describe(new java.nio.file.NoSuchFileException("/tmp/xpeng/max-export.zip")))
                .isEqualTo("NoSuchFileException");
        assertThat(ImportEventErrors.describe(new IllegalStateException("Datei C:\\Users\\max\\a.zip fehlt")))
                .isEqualTo("IllegalStateException: Datei fehlt");
    }

    @Test
    void noMessage_givesClassOnly() {
        assertThat(ImportEventErrors.describe(new NullPointerException())).isEqualTo("NullPointerException");
    }

    @Test
    void longMessage_isCappedAt200() {
        String described = ImportEventErrors.describe(new IllegalStateException("x".repeat(500)));
        assertThat(described).hasSize(ImportEventErrors.MAX_LENGTH);
    }
}
