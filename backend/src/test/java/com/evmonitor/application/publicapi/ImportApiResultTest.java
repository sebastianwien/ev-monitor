package com.evmonitor.application.publicapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportApiResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void skippedDeleted_addsHintPointingToTrash() throws Exception {
        String json = mapper.writeValueAsString(new ImportApiResult(0, 2, 0, 0, List.of(), 2));

        assertThat(json).contains("\"skippedDeleted\":2").contains("\"hint\":");
    }

    @Test
    void withoutDeleted_noHint() throws Exception {
        String json = mapper.writeValueAsString(new ImportApiResult(1, 0, 0, 0, List.of()));

        assertThat(json).doesNotContain("hint");
    }
}
