package com.evmonitor.application.tessie;

import com.evmonitor.domain.TessieRawImport;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Component
@RequiredArgsConstructor
class TessieRawImportJdbcWriter {

    private static final String SQL = """
            INSERT INTO tessie_raw_imports (user_id, vin, type, tessie_id, recorded_at, raw, processed)
            VALUES (?::uuid, ?, ?, ?, ?, ?::jsonb, false)
            ON CONFLICT (user_id, vin, type, tessie_id) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    int[] batchInsertIfNew(List<TessieRawImport> entries) {
        if (entries.isEmpty()) return new int[0];

        List<Object[]> args = entries.stream()
                .map(e -> new Object[]{
                        e.getUserId().toString(),
                        e.getVin(),
                        e.getType(),
                        e.getTessieId(),
                        Timestamp.from(e.getRecordedAt().toInstant()),
                        e.getRaw()
                })
                .toList();

        return jdbcTemplate.batchUpdate(SQL, args);
    }
}
