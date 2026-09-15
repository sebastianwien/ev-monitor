package com.evmonitor.application.user;

import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmpCatalogTest {

    private final JpaEmpRepository repository = mock(JpaEmpRepository.class);
    private final EmpCatalog catalog = new EmpCatalog(repository);

    @Test
    void findetDenKatalognamenUnabhaengigVonSchreibweiseUndLeerraum() {
        when(repository.findAllNamesSorted()).thenReturn(List.of("EnBW", "IONITY", "Maingau"));

        assertThat(catalog.resolve(" enbw ")).contains("EnBW");
        assertThat(catalog.resolve("ionity")).contains("IONITY");
    }

    @Test
    void unbekannteOderLeereNamenBleibenOhneVerweis() {
        when(repository.findAllNamesSorted()).thenReturn(List.of("EnBW"));

        assertThat(catalog.resolve("EnBW mobility+")).isEmpty();
        assertThat(catalog.resolve("")).isEmpty();
        assertThat(catalog.resolve(null)).isEmpty();
    }
}
